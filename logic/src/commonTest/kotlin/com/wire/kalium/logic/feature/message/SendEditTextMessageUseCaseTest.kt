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

package com.wire.kalium.logic.feature.message

import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.data.sync.SlowSyncStatus
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestMessage
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import com.wire.kalium.logic.test_util.testKaliumDispatcher
import com.wire.kalium.messaging.sending.MessageSender
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.util.KaliumDispatcher
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs

class SendEditTextMessageUseCaseTest {

    @Test
    fun givenAValidMessage_whenSendingEditTextIsSuccessful_thenMarkMessageAsSentAndReturnSuccess() = runTest {
        // Given
        val (arrangement, sendEditTextMessage) = Arrangement(testKaliumDispatcher)
            .withSlowSyncStatusComplete()
            .withGetMessageByIdSuccess(Message.Status.Sent)
            .withCurrentClientProviderSuccess()
            .withUpdateTextMessageSuccess()
            .withUpdateMessageStatusSuccess()
            .withSendMessageSuccess()
            .arrange()
        val originalMessageId = "message id"
        val editedMessageId = "edited message id"
        val editedMessageText = "text"

        // When
        val result = sendEditTextMessage(TestConversation.ID, originalMessageId, editedMessageText, listOf(), editedMessageId)

        // Then
        assertIs<MessageOperationResult.Success>(result)
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.messageRepository.updateTextMessage(any(), any(), originalMessageId, any())
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.messageRepository.updateMessageStatus(MessageEntity.Status.PENDING, any(), any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.messageSendFailureHandler.handleFailureAndUpdateMessageStatus(any(), any(), any(), any(), any())
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.messageSender.sendMessage(any(), any())
        }
    }

    @Test
    fun givenAValidMessage_whenSendingEditTextIsFailed_thenMarkMessageAsFailedAndReturnFailure() = runTest {
        // Given
        val (arrangement, sendEditTextMessage) = Arrangement(testKaliumDispatcher)
            .withSlowSyncStatusComplete()
            .withGetMessageByIdSuccess(Message.Status.Sent)
            .withCurrentClientProviderSuccess()
            .withUpdateTextMessageSuccess()
            .withUpdateMessageStatusSuccess()
            .withSendMessageFailure()
            .arrange()
        val originalMessageId = "message id"
        val editedMessageId = "edited message id"
        val editedMessageText = "text"

        // When
        val result = sendEditTextMessage(TestConversation.ID, originalMessageId, editedMessageText, listOf(), editedMessageId)

        // Then
        assertIs<MessageOperationResult.Failure>(result)
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.messageRepository.updateTextMessage(any(), any(), originalMessageId, any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.messageRepository.updateTextMessage(any(), any(), editedMessageId, any())
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.messageRepository.updateMessageStatus(MessageEntity.Status.PENDING, any(), any())
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.messageSendFailureHandler.handleFailureAndUpdateMessageStatus(any(), any(), any(), any(), eq(true))
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.messageSender.sendMessage(any(), any())
        }
    }

    @Test
    fun givenNoNetworkAndPendingMessagesDisabled_whenSendingEditText_thenDoesNotScheduleResend() = runTest {
        // Given
        val (arrangement, sendEditTextMessage) = Arrangement(testKaliumDispatcher)
            .withSlowSyncStatusComplete()
            .withGetMessageByIdSuccess(Message.Status.Sent)
            .withCurrentClientProviderSuccess()
            .withUpdateTextMessageSuccess()
            .withUpdateMessageStatusSuccess()
            .withSendMessageFailure()
            .withPendingMessagesDisabled()
            .arrange()
        val originalMessageId = "message id"
        val editedMessageId = "edited message id"
        val editedMessageText = "text"

        // When
        val result = sendEditTextMessage(TestConversation.ID, originalMessageId, editedMessageText, listOf(), editedMessageId)

        // Then
        assertIs<MessageOperationResult.Failure>(result)
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.messageSendFailureHandler.handleFailureAndUpdateMessageStatus(any(), any(), any(), any(), eq(false))
        }
    }

    @Test
    fun givenMessageIsPending_whenSendingEditText_thenUpdateContentOnlyAndReturnSuccess() = runTest {
        // Given
        val (arrangement, sendEditTextMessage) = Arrangement(testKaliumDispatcher)
            .withSlowSyncStatusComplete()
            .withGetMessageByIdSuccess(Message.Status.Pending)
            .withUpdateTextMessageSuccess()
            .arrange()
        val originalMessageId = "message id"
        val editedMessageId = "edited message id"
        val editedMessageText = "text"

        // When
        val result = sendEditTextMessage(TestConversation.ID, originalMessageId, editedMessageText, listOf(), editedMessageId)

        // Then
        assertIs<MessageOperationResult.Success>(result)
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.messageRepository.updateTextMessage(any(), eq(originalMessageId), any<MessageContent.Text>())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.messageRepository.updateMessageStatus(any(), any(), any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.messageSendFailureHandler.handleFailureAndUpdateMessageStatus(any(), any(), any(), any(), any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.messageSender.sendMessage(any(), any())
        }
    }

    private class Arrangement(var dispatcher: KaliumDispatcher = TestKaliumDispatcher) {
        val messageRepository = mock<MessageRepository>(mode = MockMode.autoUnit)
        val currentClientIdProvider = mock<CurrentClientIdProvider>(mode = MockMode.autoUnit)
        val slowSyncRepository = mock<SlowSyncRepository>(mode = MockMode.autoUnit)
        val messageSender = mock<MessageSender>(mode = MockMode.autoUnit)
        val messageSendFailureHandler = mock<MessageSendFailureHandler>(mode = MockMode.autoUnit)
        private var pendingMessages = true

        suspend fun withSendMessageSuccess() = apply {
            everySuspend {
                messageSender.sendMessage(any(), any())
            } returns Either.Right(Unit)
        }

        suspend fun withSendMessageFailure() = apply {
            everySuspend {
                messageSender.sendMessage(any(), any())
            } returns Either.Left(NetworkFailure.NoNetworkConnection(null))
        }

        suspend fun withGetMessageByIdSuccess(status: Message.Status) = apply {
            everySuspend {
                messageRepository.getMessageById(any(), any())
            } returns Either.Right(TestMessage.TEXT_MESSAGE.copy(status = status))
        }

        fun withSlowSyncStatusComplete() = apply {
            val stateFlow = MutableStateFlow<SlowSyncStatus>(SlowSyncStatus.Complete).asStateFlow()
            every {
                slowSyncRepository.slowSyncStatus
            } returns stateFlow
        }

        suspend fun withCurrentClientProviderSuccess(clientId: ClientId = TestClient.CLIENT_ID) = apply {
            everySuspend {
                currentClientIdProvider.invoke()
            } returns Either.Right(clientId)
        }

        suspend fun withUpdateTextMessageSuccess() = apply {
            everySuspend {
                messageRepository.updateTextMessage(any(), any<MessageContent.TextEdited>(), any(), any())
            } returns Either.Right(Unit)
            everySuspend {
                messageRepository.updateTextMessage(any(), any<String>(), any<MessageContent.Text>())
            } returns Either.Right(Unit)
        }

        suspend fun withUpdateMessageStatusSuccess() = apply {
            everySuspend {
                messageRepository.updateMessageStatus(any(), any(), any())
            } returns Either.Right(Unit)
        }

        fun withPendingMessagesDisabled() = apply {
            pendingMessages = false
        }

        fun arrange() = this to SendEditTextMessageUseCase(
            messageRepository = messageRepository,
            selfUserId = TestUser.SELF.id,
            provideClientId = currentClientIdProvider,
            slowSyncRepository = slowSyncRepository,
            messageSender = messageSender,
            messageSendFailureHandler = messageSendFailureHandler,
            pendingMessagesEnabled = pendingMessages,
            dispatchers = dispatcher,
        )
    }
}
