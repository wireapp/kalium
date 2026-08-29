/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

package com.wire.kalium.logic.sync.receiver.handler

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.cryptography.CryptoTransactionContext
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.IsMessageSentInSelfConversationUseCase
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.sync.receiver.conversation.ConversationLifecycleEventRepository
import com.wire.kalium.messaging.hooks.ConversationClearEventData
import com.wire.kalium.messaging.hooks.PersistenceEventHookNotifier
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.fail

class ClearConversationContentHandlerTest {

    @Test
    fun givenEverySenderAndSelfConversationCombination_whenHandling_thenOnlyMatchingAuthorizationStatesRunEffects() = runTest {
        listOf(
            AuthorizationCase(isSelfSender = false, isMessageInSelfConversation = false, effectsExpected = true),
            AuthorizationCase(isSelfSender = false, isMessageInSelfConversation = true, effectsExpected = false),
            AuthorizationCase(isSelfSender = true, isMessageInSelfConversation = false, effectsExpected = false),
            AuthorizationCase(isSelfSender = true, isMessageInSelfConversation = true, effectsExpected = true),
        ).forEach { case ->
            val arrangement = Arrangement().apply {
                everySuspend { isMessageSentInSelfConversation(any()) } returns case.isMessageInSelfConversation
            }
            val message = signalingMessage.copy(senderUserId = if (case.isSelfSender) selfUserId else otherUserId)

            arrangement.handler.handle(transactionContext, message, clearedContent)

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.isMessageSentInSelfConversation(eq(message))
            }
            verifySuspend(if (case.effectsExpected) VerifyMode.exactly(1) else VerifyMode.not) {
                arrangement.conversationLifecycleEventRepository.clearContent(eq(payloadConversationId))
            }
            verifySuspend(if (case.effectsExpected) VerifyMode.exactly(1) else VerifyMode.not) {
                arrangement.clearConversationAssetsLocally(eq(payloadConversationId))
            }
            verifySuspend(if (case.effectsExpected) VerifyMode.exactly(1) else VerifyMode.not) {
                arrangement.persistenceEventHookNotifier.onConversationCleared(
                    eq(ConversationClearEventData(payloadConversationId)),
                    eq(selfUserId),
                )
            }
            val deletionExpected = case.effectsExpected && case.isSelfSender
            verifySuspend(if (deletionExpected) VerifyMode.exactly(1) else VerifyMode.not) {
                arrangement.wholeConversationDeletion(eq(transactionContext), eq(payloadConversationId))
            }
        }
    }

    @Test
    fun givenAuthorizedSelfSenderWithoutRemovalFlag_whenHandling_thenWholeConversationDeletionIsSkipped() = runTest {
        val arrangement = Arrangement()

        arrangement.handler.handle(
            transactionContext,
            signalingMessage.copy(senderUserId = selfUserId),
            clearedContent.copy(needToRemoveLocally = false),
        )

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.conversationLifecycleEventRepository.clearContent(eq(payloadConversationId))
        }
        verifySuspend(VerifyMode.not) {
            arrangement.wholeConversationDeletion(any(), any())
        }
    }

    @Test
    fun givenAuthorizedSelfSenderWithRemovalFlag_whenHandling_thenPayloadEffectsRunInExactOrderWithExactArguments() = runTest {
        val arrangement = Arrangement()
        val message = signalingMessage.copy(senderUserId = selfUserId)

        arrangement.handler.handle(transactionContext, message, clearedContent)

        verifySuspend(VerifyMode.order) {
            arrangement.isMessageSentInSelfConversation(eq(message))
            arrangement.conversationLifecycleEventRepository.clearContent(eq(payloadConversationId))
            arrangement.clearConversationAssetsLocally(eq(payloadConversationId))
            arrangement.persistenceEventHookNotifier.onConversationCleared(
                eq(ConversationClearEventData(payloadConversationId)),
                eq(selfUserId),
            )
            arrangement.wholeConversationDeletion(eq(transactionContext), eq(payloadConversationId))
        }
    }

    @Test
    fun givenAllEffectDependenciesReturnLeft_whenHandling_thenLaterEffectsStillRunInOrder() = runTest {
        val arrangement = Arrangement().apply {
            everySuspend {
                conversationLifecycleEventRepository.clearContent(any())
            } returns Either.Left(StorageFailure.DataNotFound)
            everySuspend {
                clearConversationAssetsLocally(any())
            } returns Either.Left(CoreFailure.Unknown(IllegalStateException("asset cleanup failed")))
            everySuspend {
                wholeConversationDeletion(any(), any())
            } returns Either.Left(CoreFailure.Unknown(IllegalStateException("deletion failed")))
        }
        val message = signalingMessage.copy(senderUserId = selfUserId)

        arrangement.handler.handle(transactionContext, message, clearedContent)

        verifySuspend(VerifyMode.order) {
            arrangement.conversationLifecycleEventRepository.clearContent(eq(payloadConversationId))
            arrangement.clearConversationAssetsLocally(eq(payloadConversationId))
            arrangement.persistenceEventHookNotifier.onConversationCleared(
                eq(ConversationClearEventData(payloadConversationId)),
                eq(selfUserId),
            )
            arrangement.wholeConversationDeletion(eq(transactionContext), eq(payloadConversationId))
        }
    }

    @Test
    fun givenAnyDependencyThrows_whenHandling_thenSameExceptionEscapesAndLaterWorkIsSkipped() = runTest {
        DependencyStage.entries.forEach { stage ->
            assertEscapingFailure(stage, IllegalStateException("$stage failed"))
        }
    }

    @Test
    fun givenAnyDependencyCancels_whenHandling_thenSameCancellationEscapesAndLaterWorkIsSkipped() = runTest {
        DependencyStage.entries.forEach { stage ->
            assertEscapingFailure(stage, CancellationException("$stage cancelled"))
        }
    }

    private suspend fun assertEscapingFailure(stage: DependencyStage, expected: Throwable) {
        val arrangement = Arrangement().apply {
            when (stage) {
                DependencyStage.VERIFIER -> everySuspend {
                    isMessageSentInSelfConversation(any())
                } throws expected

                DependencyStage.CLEAR_CONTENT -> everySuspend {
                    conversationLifecycleEventRepository.clearContent(any())
                } throws expected

                DependencyStage.ASSET_CLEANUP -> everySuspend {
                    clearConversationAssetsLocally(any())
                } throws expected

                DependencyStage.HOOK -> everySuspend {
                    persistenceEventHookNotifier.onConversationCleared(any(), any())
                } throws expected

                DependencyStage.DELETION -> everySuspend {
                    wholeConversationDeletion(any(), any())
                } throws expected
            }
        }

        val actual = try {
            arrangement.handler.handle(
                transactionContext,
                signalingMessage.copy(senderUserId = selfUserId),
                clearedContent,
            )
            fail("Expected $expected to escape from $stage")
        } catch (actual: Throwable) {
            actual
        }

        assertSame(expected, actual)
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.isMessageSentInSelfConversation(any())
        }
        verifySuspend(stage.callMode(DependencyStage.CLEAR_CONTENT)) {
            arrangement.conversationLifecycleEventRepository.clearContent(any())
        }
        verifySuspend(stage.callMode(DependencyStage.ASSET_CLEANUP)) {
            arrangement.clearConversationAssetsLocally(any())
        }
        verifySuspend(stage.callMode(DependencyStage.HOOK)) {
            arrangement.persistenceEventHookNotifier.onConversationCleared(any(), any())
        }
        verifySuspend(stage.callMode(DependencyStage.DELETION)) {
            arrangement.wholeConversationDeletion(any(), any())
        }
    }

    private fun DependencyStage.callMode(dependency: DependencyStage): VerifyMode =
        if (ordinal >= dependency.ordinal) VerifyMode.exactly(1) else VerifyMode.not

    private class Arrangement {
        val conversationLifecycleEventRepository = mock<ConversationLifecycleEventRepository>()
        val isMessageSentInSelfConversation = mock<IsMessageSentInSelfConversationUseCase>()
        val clearConversationAssetsLocally = mock<ClearConversationAssetsLocally>()
        val wholeConversationDeletion = mock<WholeConversationDeletion>()
        val persistenceEventHookNotifier = mock<PersistenceEventHookNotifier>(MockMode.autoUnit)

        init {
            everySuspend { conversationLifecycleEventRepository.clearContent(any()) } returns Either.Right(Unit)
            everySuspend { isMessageSentInSelfConversation(any()) } returns true
            everySuspend { clearConversationAssetsLocally(any()) } returns Either.Right(Unit)
            everySuspend { wholeConversationDeletion(any(), any()) } returns Either.Right(Unit)
        }

        val handler: ClearConversationContentHandler = ClearConversationContentHandlerImpl(
            conversationLifecycleEventRepository = conversationLifecycleEventRepository,
            selfUserId = selfUserId,
            isMessageSentInSelfConversation = isMessageSentInSelfConversation,
            clearLocalConversationAssets = clearConversationAssetsLocally,
            deleteConversation = wholeConversationDeletion,
            persistenceEventHookNotifier = persistenceEventHookNotifier,
        )
    }

    private data class AuthorizationCase(
        val isSelfSender: Boolean,
        val isMessageInSelfConversation: Boolean,
        val effectsExpected: Boolean,
    )

    private enum class DependencyStage {
        VERIFIER,
        CLEAR_CONTENT,
        ASSET_CLEANUP,
        HOOK,
        DELETION,
    }

    private companion object {
        val envelopeConversationId = ConversationId("envelope-conversation", "wire.example")
        val payloadConversationId = ConversationId("payload-conversation", "wire.example")
        val selfUserId = UserId("self-user", "wire.example")
        val otherUserId = UserId("other-user", "wire.example")
        val transactionContext = mock<CryptoTransactionContext>(MockMode.autoUnit)
        val clearedContent = MessageContent.Cleared(
            conversationId = payloadConversationId,
            time = Instant.parse("2026-08-19T12:00:00Z"),
            needToRemoveLocally = true,
        )
        val signalingMessage = Message.Signaling(
            id = "signaling-message-id",
            content = clearedContent,
            conversationId = envelopeConversationId,
            date = Instant.parse("2026-08-19T12:01:00Z"),
            senderUserId = otherUserId,
            senderClientId = ClientId("sender-client"),
            status = Message.Status.Sent,
            isSelfMessage = false,
            expirationData = null,
        )
    }
}
