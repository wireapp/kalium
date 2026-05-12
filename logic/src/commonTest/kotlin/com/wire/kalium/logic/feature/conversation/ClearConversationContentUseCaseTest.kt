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

package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.logic.cache.SelfConversationIdProvider
import com.wire.kalium.messaging.hooks.PersistenceEventHookNotifier
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.common.functional.Either
import com.wire.kalium.messaging.sending.MessageSender
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs

class ClearConversationContentUseCaseTest {

    @Test
    fun givenGettingClientIdFails_whenInvoking_thenCorrectlyPropagateFailure() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withClearConversationContent(true)
            .withCurrentClientId(false)
            .withMessageSending(true)
            .withClearConversationAssetsLocally(true)
            .withSelfConversationIds(listOf(selfConversationId))
            .arrange()

        val result = useCase(ConversationId("someValue", "someDomain"))

        assertIs<ClearConversationContentUseCase.Result.Failure>(result)

        with(arrangement) {
            verifySuspend(VerifyMode.exactly(1)) { currentClientIdProvider.invoke() }
            verifySuspend(VerifyMode.not) { messageSender.sendMessage(any(), any()) }
            verifySuspend(VerifyMode.not) { clearConversationAssetsLocally(any()) }
            verifySuspend(VerifyMode.not) { conversationRepository.clearContent(any()) }
        }
    }

    @Test
    fun givenSendMessageFails_whenInvoking_thenCorrectlyPropagateFailure() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withClearConversationContent(true)
            .withCurrentClientId(true)
            .withMessageSending(false)
            .withClearConversationAssetsLocally(true)
            .withSelfConversationIds(listOf(selfConversationId))
            .arrange()

        val result = useCase(ConversationId("someValue", "someDomain"))

        assertIs<ClearConversationContentUseCase.Result.Failure>(result)

        with(arrangement) {
            verifySuspend(VerifyMode.exactly(1)) { currentClientIdProvider.invoke() }
            verifySuspend(VerifyMode.exactly(1)) { messageSender.sendMessage(any(), any()) }
            verifySuspend(VerifyMode.not) { clearConversationAssetsLocally(any()) }
            verifySuspend(VerifyMode.not) { conversationRepository.clearContent(any()) }
        }
    }

    @Test
    fun givenClearAssetsFails_whenInvoking_thenCorrectlyPropagateFailure() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withClearConversationContent(true)
            .withCurrentClientId(true)
            .withMessageSending(true)
            .withClearConversationAssetsLocally(false)
            .withSelfConversationIds(listOf(selfConversationId))
            .arrange()

        val result = useCase(ConversationId("someValue", "someDomain"))

        assertIs<ClearConversationContentUseCase.Result.Failure>(result)

        with(arrangement) {
            verifySuspend(VerifyMode.exactly(1)) { currentClientIdProvider.invoke() }
            verifySuspend(VerifyMode.exactly(1)) { messageSender.sendMessage(any(), any()) }
            verifySuspend(VerifyMode.exactly(1)) { clearConversationAssetsLocally(any()) }
            verifySuspend(VerifyMode.not) { conversationRepository.clearContent(any()) }
        }
    }

    @Test
    fun givenClearConversationFails_whenInvoking_thenCorrectlyPropagateFailure() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withClearConversationContent(false)
            .withClearConversationAssetsLocally(true)
            .withMessageSending(true)
            .withCurrentClientId((true))
            .withSelfConversationIds(listOf(selfConversationId))
            .arrange()

        val result = useCase(ConversationId("someValue", "someDomain"))

        assertIs<ClearConversationContentUseCase.Result.Failure>(result)

        with(arrangement) {
            verifySuspend(VerifyMode.exactly(1)) { currentClientIdProvider.invoke() }
            verifySuspend(VerifyMode.exactly(1)) { messageSender.sendMessage(any(), any()) }
            verifySuspend(VerifyMode.exactly(1)) { clearConversationAssetsLocally(any()) }
            verifySuspend(VerifyMode.exactly(1)) { conversationRepository.clearContent(any()) }
        }
    }

    @Test
    fun givenClearingConversationSucceeds_whenInvoking_thenCorrectlyPropagateSuccess() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withClearConversationContent(true)
            .withCurrentClientId(true)
            .withMessageSending(true)
            .withClearConversationAssetsLocally(true)
            .withSelfConversationIds(listOf(selfConversationId))
            .arrange()

        val result = useCase(ConversationId("someValue", "someDomain"))

        assertIs<ClearConversationContentUseCase.Result.Success>(result)

        with(arrangement) {
            verifySuspend(VerifyMode.exactly(1)) { currentClientIdProvider.invoke() }
            verifySuspend(VerifyMode.exactly(1)) { messageSender.sendMessage(any(), any()) }
            verifySuspend(VerifyMode.exactly(1)) { clearConversationAssetsLocally(any()) }
            verifySuspend(VerifyMode.exactly(1)) { conversationRepository.clearContent(any()) }
        }
    }

    private companion object {
        val selfConversationId = ConversationId("self_conversation_id", "self_domain")
    }

    private class Arrangement {

        val conversationRepository = mock<ConversationRepository>(mode = MockMode.autoUnit)
        val currentClientIdProvider = mock<CurrentClientIdProvider>(mode = MockMode.autoUnit)
        val selfConversationIdProvider: SelfConversationIdProvider = mock()
        val messageSender = mock<MessageSender>(mode = MockMode.autoUnit)
        val clearConversationAssetsLocally = mock<ClearConversationAssetsLocallyUseCase>(mode = MockMode.autoUnit)

        suspend fun withClearConversationContent(isSuccessFull: Boolean) = apply {
            everySuspend {
                conversationRepository.clearContent(any())
            } returns (if (isSuccessFull) Either.Right(Unit) else Either.Left(CoreFailure.Unknown(Throwable("an error"))))
        }

        suspend fun withClearConversationAssetsLocally(isSuccessFull: Boolean) = apply {
            everySuspend {
                clearConversationAssetsLocally(any())
            } returns (if (isSuccessFull) Either.Right(Unit) else Either.Left(CoreFailure.Unknown(Throwable("an error"))))
        }

        suspend fun withCurrentClientId(isSuccessFull: Boolean) = apply {
            everySuspend { currentClientIdProvider() }
                .returns(
                    if (isSuccessFull) Either.Right(TestClient.CLIENT_ID)
                    else Either.Left(CoreFailure.Unknown(Throwable("an error")))
                )
        }

        suspend fun withMessageSending(isSuccessFull: Boolean) = apply {
            everySuspend {
                messageSender.sendMessage(any(), any())
            } returns (if (isSuccessFull) Either.Right(Unit) else Either.Left(CoreFailure.Unknown(Throwable("an error"))))
        }

        suspend fun withSelfConversationIds(conversationIds: List<ConversationId>) = apply {
            everySuspend {
                selfConversationIdProvider.invoke()
            } returns Either.Right(conversationIds)
        }

        val persistenceEventHookNotifier: PersistenceEventHookNotifier = object : PersistenceEventHookNotifier {}

        fun arrange() = this to ClearConversationContentUseCaseImpl(
            conversationRepository,
            messageSender,
            TestUser.SELF.id,
            currentClientIdProvider,
            selfConversationIdProvider,
            clearConversationAssetsLocally,
            persistenceEventHookNotifier,
        )
    }

}
