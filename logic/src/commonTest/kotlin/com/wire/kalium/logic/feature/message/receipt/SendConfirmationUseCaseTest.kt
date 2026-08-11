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

package com.wire.kalium.logic.feature.message.receipt

import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.properties.UserPropertyRepository
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestMessage
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.sync.SyncManager
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.messaging.sending.MessageSender
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

class SendConfirmationUseCaseTest {

    @Test
    fun givenAConversationId_whenReadConfirmationsEnabled_thenShouldSendConfirmation() = runTest {
        val (arrangement, sendConfirmation) = Arrangement()
            .withCurrentClientIdProvider()
            .withGetConversationByIdSuccessful()
            .withToggleReadReceiptsStatus(true)
            .withPendingMessagesResponse()
            .withSendMessageSuccess()
            .arrange()

        val after = Instant.DISTANT_PAST
        val until = after + 10.seconds

        val result = sendConfirmation(TestConversation.ID, after, until)

        result.toEither().shouldSucceed()
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.messageSender.sendMessage(any(), any())
        }
    }

    @Test
    fun givenMLSOneOnOneConversation_whenSendingReadConfirmation_thenShouldSendConfirmation() = runTest {
        val (arrangement, sendConfirmation) = Arrangement()
            .withCurrentClientIdProvider()
            .withGetConversationByIdSuccessful(TestConversation.MLS_CONVERSATION)
            .withToggleReadReceiptsStatus(true)
            .withPendingMessagesResponse()
            .withSendMessageSuccess()
            .arrange()

        val after = Instant.DISTANT_PAST
        val until = after + 10.seconds

        val result = sendConfirmation(TestConversation.ID, after, until)

        result.toEither().shouldSucceed()
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.messageSender.sendMessage(any(), any())
        }
    }

    @Test
    fun givenMLSGroupConversation_whenSendingReadConfirmation_thenShouldNotSendConfirmation() = runTest {
        val mlsGroupConversation = TestConversation.MLS_CONVERSATION.copy(type = Conversation.Type.Group.Regular)
        val (arrangement, sendConfirmation) = Arrangement()
            .withGetConversationByIdSuccessful(mlsGroupConversation)
            .arrange()

        val after = Instant.DISTANT_PAST
        val until = after + 10.seconds

        val result = sendConfirmation(TestConversation.ID, after, until)

        result.toEither().shouldSucceed()
        verifySuspend(VerifyMode.not) {
            arrangement.messageRepository.getPendingConfirmationMessagesByConversationAfterDate(
                any(),
                eq(after),
                eq(until),
                any()
            )
        }
        verifySuspend(VerifyMode.not) {
            arrangement.messageSender.sendMessage(any(), any())
        }
    }

    @Test
    fun givenMixedConversation_whenSendingReadConfirmation_thenShouldSendConfirmationUsingProteus() = runTest {
        val (arrangement, sendConfirmation) = Arrangement()
            .withCurrentClientIdProvider()
            .withGetConversationByIdSuccessful(TestConversation.MIXED_CONVERSATION)
            .withToggleReadReceiptsStatus(true)
            .withPendingMessagesResponse()
            .withSendMessageSuccess()
            .arrange()

        val after = Instant.DISTANT_PAST
        val until = after + 10.seconds

        val result = sendConfirmation(TestConversation.ID, after, until)

        result.toEither().shouldSucceed()
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.messageSender.sendMessage(any(), any())
        }
    }

    @Test
    fun givenAConversationId_whenReadConfirmationsDisabled_thenShouldNOTSendConfirmation() = runTest {
        val (arrangement, sendConfirmation) = Arrangement()
            .withCurrentClientIdProvider()
            .withGetConversationByIdSuccessful()
            .withToggleReadReceiptsStatus(false)
            .withPendingMessagesResponse()
            .withSendMessageSuccess()
            .arrange()

        val after = Instant.DISTANT_PAST
        val until = after + 10.seconds

        val result = sendConfirmation(TestConversation.ID, after, until)

        result.toEither().shouldSucceed()
        verifySuspend(VerifyMode.not) {
            arrangement.messageSender.sendMessage(any(), any())
        }

        verifySuspend(VerifyMode.not) {
            arrangement.messageRepository.getPendingConfirmationMessagesByConversationAfterDate(
                any(),
                eq(after),
                eq(until),
                any()
            )
        }
    }

    private class Arrangement {
        private val currentClientIdProvider = mock<CurrentClientIdProvider>(mode = MockMode.autoUnit)
        private val syncManager = mock<SyncManager>(mode = MockMode.autoUnit)
        val messageSender = mock<MessageSender>(mode = MockMode.autoUnit)
        private val conversationRepository = mock<ConversationRepository>(mode = MockMode.autoUnit)
        val messageRepository = mock<MessageRepository>(mode = MockMode.autoUnit)
        private val userPropertyRepository = mock<UserPropertyRepository>(mode = MockMode.autoUnit)

        suspend fun withCurrentClientIdProvider() = apply {
            everySuspend {
                currentClientIdProvider.invoke()
            } returns Either.Right(TestClient.CLIENT_ID)
        }

        suspend fun withSendMessageSuccess() = apply {
            everySuspend {
                messageSender.sendMessage(any(), any())
            } returns Either.Right(Unit)
        }

        suspend fun withGetConversationByIdSuccessful(
            conversation: Conversation = TestConversation.CONVERSATION
        ) = apply {
            everySuspend {
                conversationRepository.getConversationById(any())
            } returns Either.Right(conversation)
        }

        suspend fun withToggleReadReceiptsStatus(enabled: Boolean = false) = apply {
            everySuspend {
                userPropertyRepository.getReadReceiptsStatus()
            } returns enabled
        }

        suspend fun withPendingMessagesResponse() = apply {
            everySuspend {
                messageRepository.getPendingConfirmationMessagesByConversationAfterDate(any(), any(), any(), any())
            } returns Either.Right(listOf(TestMessage.TEXT_MESSAGE.id))
        }

        fun arrange() = this to SendConfirmationUseCase(
            currentClientIdProvider,
            syncManager,
            messageSender,
            TestUser.SELF.id,
            conversationRepository,
            messageRepository,
            userPropertyRepository
        )
    }

}
