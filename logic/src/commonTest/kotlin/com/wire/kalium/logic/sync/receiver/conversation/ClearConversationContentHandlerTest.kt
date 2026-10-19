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
package com.wire.kalium.logic.sync.receiver.conversation

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.IsMessageSentInSelfConversationUseCase
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.conversation.ClearConversationAssetsLocallyUseCase
import com.wire.kalium.logic.feature.conversation.delete.DeleteConversationUseCase
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.sync.receiver.handler.ClearConversationContentHandler
import com.wire.kalium.logic.sync.receiver.handler.ClearConversationContentHandlerImpl
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementMokkeryImpl
import com.wire.kalium.messaging.hooks.ConversationClearEventData
import com.wire.kalium.messaging.hooks.NoOpPersistenceEventHookNotifier
import com.wire.kalium.messaging.hooks.ConversationLastReadEventData
import com.wire.kalium.messaging.hooks.PersistenceEventHookNotifier
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.time.Instant
import kotlin.test.Test
import kotlin.test.assertFailsWith
import com.wire.kalium.logic.sync.KaliumSyncException
import kotlin.test.assertEquals

class ClearConversationContentHandlerTest {

    @Test
    fun NSEReplayUsesItsSnapshotInsteadOfClearingNewerEffectsAgain() = runTest {
        val calls = mutableListOf<String>()
        val hook = RecordingPersistenceEventHookNotifier()
        val (arrangement, handler) = Arrangement(hookNotifier = hook).arrange(recoverNSEClear = { message, _ ->
            calls += message.id
            true
        }) {
            withMessageSentInSelfConversation(true)
            everySuspend { conversationRepository.getConversationById(CONVERSATION_ID) } returns Either.Left(StorageFailure.DataNotFound)
        }
        handler.handle(arrangement.transactionContext, OWN_MESSAGE,
            MessageContent.Cleared(CONVERSATION_ID, OWN_MESSAGE.date, false))
        assertEquals(listOf(OWN_MESSAGE.id), calls)
        assertEquals(0, hook.conversationClearCalls.size)
        assertNoCleanup(arrangement, hook)
    }

    @Test
    fun foreignSenderInRegularConversationIsRejected() = rejectedSignal(MESSAGE, false)

    @Test
    fun foreignSenderInSelfConversationIsRejected() = rejectedSignal(MESSAGE.copy(conversationId = SELF_CONVERSATION_ID), true)

    @Test
    fun selfSenderInRegularConversationIsRejected() = rejectedSignal(OWN_MESSAGE.copy(conversationId = CONVERSATION_ID), false)

    private fun rejectedSignal(message: Message.Signaling, selfConversation: Boolean) = runTest {
        for (removeLocally in listOf(false, true)) {
            for (target in listOf(CONVERSATION_ID, ConversationId("another-conversation", "other-domain"))) {
                val hook = RecordingPersistenceEventHookNotifier()
                val recoveryCalls = mutableListOf<String>()
                val (arrangement, handler) = Arrangement(hook).arrange(recoverNSEClear = { signal, _ ->
                    recoveryCalls += signal.id
                    false
                }) { withMessageSentInSelfConversation(selfConversation) }

                handler.handle(arrangement.transactionContext, message,
                    MessageContent.Cleared(target, Instant.DISTANT_PAST, removeLocally))

                assertNoCleanup(arrangement, hook)
                assertEquals(emptyList(), recoveryCalls)
                verifySuspend(VerifyMode.not) { arrangement.conversationRepository.getConversationById(any()) }
            }
        }
    }

    @Test
    fun missingTargetIsRejected() = runTest {
        val hook = RecordingPersistenceEventHookNotifier()
        val (arrangement, handler) = Arrangement(hook).arrange {
            withMessageSentInSelfConversation(true)
            everySuspend { conversationRepository.getConversationById(CONVERSATION_ID) } returns Either.Left(StorageFailure.DataNotFound)
        }
        for (removeLocally in listOf(false, true)) {
            handler.handle(arrangement.transactionContext, OWN_MESSAGE,
                MessageContent.Cleared(CONVERSATION_ID, Instant.DISTANT_PAST, removeLocally))
        }
        assertNoCleanup(arrangement, hook)
    }

    @Test
    fun selfConversationTargetIsRejected() = runTest {
        val hook = RecordingPersistenceEventHookNotifier()
        val (arrangement, handler) = Arrangement(hook).arrange(recoverNSEClear = { _, _ ->
            error("Self-conversation target must not trigger recovery")
        }) {
            withMessageSentInSelfConversation(true)
            everySuspend { conversationRepository.getConversationById(CONVERSATION_ID) } returns
                Either.Right(TestConversation.SELF().copy(id = CONVERSATION_ID))
        }
        for (target in listOf(SELF_CONVERSATION_ID, CONVERSATION_ID)) {
            for (removeLocally in listOf(false, true)) {
                handler.handle(arrangement.transactionContext, OWN_MESSAGE,
                    MessageContent.Cleared(target, Instant.DISTANT_PAST, removeLocally))
            }
        }
        assertNoCleanup(arrangement, hook)
    }

    @Test
    fun targetLookupFailurePreventsCleanupAndRecovery() = runTest {
        val hook = RecordingPersistenceEventHookNotifier()
        val (arrangement, handler) = Arrangement(hook).arrange(recoverNSEClear = { _, _ ->
            error("Failed target lookup must not trigger recovery")
        }) {
            withMessageSentInSelfConversation(true)
            everySuspend { conversationRepository.getConversationById(CONVERSATION_ID) } returns
                Either.Left(StorageFailure.Generic(IllegalStateException("Storage unavailable")))
        }
        assertFailsWith<KaliumSyncException> {
            handler.handle(arrangement.transactionContext, OWN_MESSAGE,
                MessageContent.Cleared(CONVERSATION_ID, Instant.DISTANT_PAST, true))
        }
        assertNoCleanup(arrangement, hook)
    }

    private suspend fun assertNoCleanup(arrangement: Arrangement, hook: RecordingPersistenceEventHookNotifier) {
        verifySuspend(VerifyMode.not) { arrangement.conversationRepository.clearContent(any()) }
        verifySuspend(VerifyMode.not) { arrangement.clearConversationAssetsLocally(any()) }
        verifySuspend(VerifyMode.not) { arrangement.deleteConversation(any(), any()) }
        assertEquals(emptyList(), hook.conversationClearCalls)
    }

    @Test
    fun givenSelfSenderAndNeedToRemoveLocally_whenMessageInSelfConversation_thenConversationDeleted() = runTest {
        // given
        val (arrangement, handler) = Arrangement()
            .arrange {
                withMessageSentInSelfConversation(true)
            }

        // when
        handler.handle(
            message = OWN_MESSAGE,
            messageContent = MessageContent.Cleared(
                conversationId = CONVERSATION_ID,
                time = Instant.DISTANT_PAST,
                needToRemoveLocally = true
            ),
            transactionContext = arrangement.transactionContext
        )

        // then
        verifySuspend(VerifyMode.exactly(1)) { arrangement.deleteConversation(any(), any()) }
        verifySuspend(VerifyMode.exactly(1)) { arrangement.conversationRepository.clearContent(any()) }
    }

    @Test
    fun givenMessageFromTheSelfUser_whenMessageInSelfConversationAndNoNeedToRemove_thenOnlyContentRemoved() = runTest {
        // given
        val (arrangement, handler) = Arrangement()
            .arrange {
                withMessageSentInSelfConversation(true)
            }

        // when
        handler.handle(
            message = OWN_MESSAGE,
            messageContent = MessageContent.Cleared(
                conversationId = CONVERSATION_ID,
                time = Instant.DISTANT_PAST,
                needToRemoveLocally = false
            ),
            transactionContext = arrangement.transactionContext
        )

        // then
        verifySuspend(VerifyMode.not) { arrangement.deleteConversation(any(), any()) }
        verifySuspend(VerifyMode.exactly(1)) { arrangement.conversationRepository.clearContent(any()) }
    }

    @Test
    fun givenConversationClearedSuccessfully_whenHandled_thenHookIsNotified() = runTest {
        // given
        val hookNotifier = RecordingPersistenceEventHookNotifier()
        val (arrangement, handler) = Arrangement(hookNotifier = hookNotifier)
            .arrange {
                withMessageSentInSelfConversation(true)
            }

        // when
        handler.handle(
            message = OWN_MESSAGE,
            messageContent = MessageContent.Cleared(
                conversationId = CONVERSATION_ID,
                time = Instant.DISTANT_PAST,
                needToRemoveLocally = false
            ),
            transactionContext = arrangement.transactionContext
        )

        // then
        assertEquals(1, hookNotifier.conversationClearCalls.size)
        val (data, selfUserId) = hookNotifier.conversationClearCalls.single()
        assertEquals(CONVERSATION_ID, data.conversationId)
        assertEquals(TestUser.USER_ID, selfUserId)
    }


    @Test
    fun assetCleanupFailureDoesNotPreventClearAndRemove() = runTest {
        val hook = RecordingPersistenceEventHookNotifier()
        val (arrangement, handler) = Arrangement(hook).arrange {
            withMessageSentInSelfConversation(true)
            everySuspend { clearConversationAssetsLocally(any()) } returns Either.Left(CoreFailure.Unknown(null))
        }

        handler.handle(arrangement.transactionContext, OWN_MESSAGE,
            MessageContent.Cleared(CONVERSATION_ID, Instant.DISTANT_PAST, true))

        verifySuspend(VerifyMode.exactly(1)) { arrangement.conversationRepository.clearContent(CONVERSATION_ID) }
        verifySuspend(VerifyMode.exactly(1)) { arrangement.deleteConversation(any(), eq(CONVERSATION_ID)) }
        assertEquals(1, hook.conversationClearCalls.size)
    }

    @Test
    fun contentFailureStillPreventsRemovalAndSuccessHook() = runTest {
        val hook = RecordingPersistenceEventHookNotifier()
        val (arrangement, handler) = Arrangement(hook).arrange {
            withMessageSentInSelfConversation(true)
            everySuspend { conversationRepository.clearContent(any()) } returns Either.Left(CoreFailure.Unknown(null))
        }

        assertFailsWith<KaliumSyncException> {
            handler.handle(arrangement.transactionContext, OWN_MESSAGE,
                MessageContent.Cleared(CONVERSATION_ID, Instant.DISTANT_PAST, true))
        }

        verifySuspend(VerifyMode.not) { arrangement.deleteConversation(any(), any()) }
        assertEquals(0, hook.conversationClearCalls.size)
    }

    @Test
    fun conversationDeletionFailureStillPropagates() = runTest {
        val (arrangement, handler) = Arrangement().arrange {
            withMessageSentInSelfConversation(true)
            everySuspend { deleteConversation(any(), any()) } returns Either.Left(CoreFailure.Unknown(null))
        }

        assertFailsWith<KaliumSyncException> {
            handler.handle(arrangement.transactionContext, OWN_MESSAGE,
                MessageContent.Cleared(CONVERSATION_ID, Instant.DISTANT_PAST, true))
        }
    }

    private class Arrangement(
        private val hookNotifier: PersistenceEventHookNotifier = NoOpPersistenceEventHookNotifier,
    ) : CryptoTransactionProviderArrangement by CryptoTransactionProviderArrangementMokkeryImpl() {

        val conversationRepository = mock<ConversationRepository>()
        val deleteConversation = mock<DeleteConversationUseCase>()
        val isMessageSentInSelfConversationUseCase = mock<IsMessageSentInSelfConversationUseCase>()
        val clearConversationAssetsLocally = mock<ClearConversationAssetsLocallyUseCase>()

        suspend fun withMessageSentInSelfConversation(isSentInSelfConv: Boolean) = apply {
            everySuspend { isMessageSentInSelfConversationUseCase(any()) } returns isSentInSelfConv
        }

        suspend fun arrange(
            recoverNSEClear: suspend (Message.Signaling, MessageContent.Cleared) -> Boolean = { _, _ -> false },
            block: suspend Arrangement.() -> Unit,
        ): Pair<Arrangement, ClearConversationContentHandler> = run {
            val clearConversationContentHandler = ClearConversationContentHandlerImpl(
                conversationRepository = conversationRepository,
                selfUserId = TestUser.USER_ID,
                isMessageSentInSelfConversation = isMessageSentInSelfConversationUseCase,
                clearLocalConversationAssets = clearConversationAssetsLocally,
                deleteConversation = deleteConversation,
                persistenceEventHookNotifier = hookNotifier,
                recoverNSEClear = recoverNSEClear,
            )
            withDeletingConversationSucceeding()
            withClearContentSucceeding()
            everySuspend { conversationRepository.getConversationById(CONVERSATION_ID) } returns
                Either.Right(TestConversation.GROUP().copy(id = CONVERSATION_ID))
            everySuspend { clearConversationAssetsLocally(any()) } returns Either.Right(Unit)
            block()

            this to clearConversationContentHandler
        }

        suspend fun withDeletingConversationSucceeding() {
            everySuspend { deleteConversation(any(), any()) } returns Either.Right(Unit)
        }

        suspend fun withClearContentSucceeding() {
            everySuspend { conversationRepository.clearContent(any()) } returns Either.Right(Unit)
        }
    }

    private class RecordingPersistenceEventHookNotifier : PersistenceEventHookNotifier {
        val conversationClearCalls = mutableListOf<Pair<ConversationClearEventData, UserId>>()

        override suspend fun onConversationCleared(data: ConversationClearEventData, selfUserId: UserId) {
            conversationClearCalls += data to selfUserId
        }

        override suspend fun onConversationLastReadPersisted(
            data: ConversationLastReadEventData,
            selfUserId: UserId
        ) = Unit
    }

    companion object {
        private val CONVERSATION_ID = ConversationId("conversationId", "domain")
        private val SELF_CONVERSATION_ID = ConversationId("self-conversation", "domain")
        private val OTHER_USER_ID = UserId("otherUserId", "domain")

        private val MESSAGE_CONTENT = MessageContent.DataTransfer(
            trackingIdentifier = MessageContent.DataTransfer.TrackingIdentifier(
                identifier = "abcd-1234-efgh-5678"
            )
        )
        val MESSAGE = Message.Signaling(
            id = "messageId",
            content = MESSAGE_CONTENT,
            conversationId = CONVERSATION_ID,
            date = Instant.DISTANT_PAST,
            senderUserId = OTHER_USER_ID,
            senderClientId = ClientId("deviceId"),
            status = Message.Status.Sent,
            isSelfMessage = false,
            expirationData = null,
        )

        val OWN_MESSAGE = MESSAGE.copy(senderUserId = TestUser.USER_ID, conversationId = SELF_CONVERSATION_ID, isSelfMessage = true)
    }
}
