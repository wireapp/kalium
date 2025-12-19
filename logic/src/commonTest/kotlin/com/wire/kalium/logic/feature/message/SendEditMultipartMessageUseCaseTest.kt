/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.left
import com.wire.kalium.common.functional.right
import com.wire.kalium.cryptography.CryptoTransactionContext
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageAttachment
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.failure.ProteusSendMessageFailure
import com.wire.kalium.logic.fakes.FakeMessageRepository
import com.wire.kalium.logic.fakes.sync.FakeSlowSyncRepository
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import com.wire.kalium.logic.test_util.testKaliumDispatcher
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.messaging.sending.BroadcastMessage
import com.wire.kalium.messaging.sending.BroadcastMessageTarget
import com.wire.kalium.messaging.sending.MessageSender
import com.wire.kalium.messaging.sending.MessageTarget
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.util.KaliumDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class SendEditMultipartMessageUseCaseTest {

    @Test
    fun givenAValidMessage_whenSendingEditMultipartIsSuccessful_thenMarkMessageAsSentAndReturnSuccess() = runTest {

        val originalMessageId = "message id"
        val editedMessageId = "edited message id"
        val editedMessageText = "text"

        var updateCalled = 0
        var updateStatusCalled = 0
        var handleFailureCalled = 0
        var sendCalled = 0

        // Given
        val (_, sendEditTextMessage) = Arrangement(testKaliumDispatcher)
            .withUpdateMessage { messageId ->
                if (messageId == originalMessageId) updateCalled++
            }
            .withUpdateMessageStatus { status->
                if (status == MessageEntity.Status.PENDING) updateStatusCalled++
            }
            .withHandleMessageFailure { handleFailureCalled++ }
            .withSendMessage {
                sendCalled++
                Unit.right()
            }
            .arrange()

        // When
        val result = sendEditTextMessage(TestConversation.ID, originalMessageId, editedMessageText, listOf(), editedMessageId)

        // Then
        result.shouldSucceed()

        assertEquals(1, updateCalled)
        assertEquals(1, updateStatusCalled)
        assertEquals(0, handleFailureCalled)
        assertEquals(1, sendCalled)
    }

    @Test
    fun givenAValidMessage_whenSendingEditMultipartIsFailed_thenMarkMessageAsFailedAndReturnFailure() = runTest {


        val originalMessageId = "message id"
        val editedMessageId = "edited message id"
        val editedMessageText = "text"

        var updateCalled = 0
        var updateStatusCalled = 0
        var handleFailureCalled = 0
        var sendCalled = 0

        // Given
        val (_, sendEditTextMessage) = Arrangement(testKaliumDispatcher)
            .withUpdateMessage { messageId ->
                if (messageId == originalMessageId) {
                    updateCalled++
                } else {
                    error("Must not happen in this test")
                }
            }
            .withUpdateMessageStatus { status->
                if (status == MessageEntity.Status.PENDING) updateStatusCalled++
            }
            .withHandleMessageFailure { handleFailureCalled++ }
            .withSendMessage {
                sendCalled++
                CoreFailure.Unknown(IllegalStateException("Test exception")).left()
            }
            .arrange()

        // When
        val result = sendEditTextMessage(TestConversation.ID, originalMessageId, editedMessageText, listOf(), editedMessageId)

        // Then
        result.shouldFail()

        assertEquals(1, updateCalled)
        assertEquals(1, updateStatusCalled)
        assertEquals(1, handleFailureCalled)
        assertEquals(1, sendCalled)
    }

    private class Arrangement(var dispatcher: KaliumDispatcher = TestKaliumDispatcher) {

        private var sendMessage: () -> Either<CoreFailure, Unit> = { Unit.right() }
        private var handleMessageFailure: () -> Unit = {}
        private var updateMessage: (String) -> Unit = {}
        private var updateMessageStatus: (MessageEntity.Status) -> Unit = {}

        private val messageRepository: MessageRepository = object : FakeMessageRepository() {
            override suspend fun updateMultipartMessage(
                conversationId: ConversationId,
                messageContent: MessageContent.MultipartEdited,
                newMessageId: String,
                editInstant: Instant
            ): Either<CoreFailure, Unit> {
                return updateMessage(newMessageId).right()
            }

            override suspend fun updateMessageStatus(
                messageStatus: MessageEntity.Status,
                conversationId: ConversationId,
                messageUuid: String
            ): Either<CoreFailure, Unit> {
                return updateMessageStatus(messageStatus).right()
            }
        }
        private val clientIdProvider = CurrentClientIdProvider { TestClient.CLIENT_ID.right() }

        private val messageSender = object : MessageSender {
            override suspend fun sendPendingMessage(conversationId: ConversationId, messageUuid: String) = Unit.right()
            override suspend fun sendMessage(
                message: Message.Sendable,
                messageTarget: MessageTarget
            ): Either<CoreFailure, Unit> {
                return sendMessage()
            }

            override suspend fun broadcastMessage(message: BroadcastMessage, target: BroadcastMessageTarget) = Unit.right()
        }

        private val sendFailureHandler = object : MessageSendFailureHandler {
            override suspend fun handleClientsHaveChangedFailure(
                transactionContext: CryptoTransactionContext,
                sendFailure: ProteusSendMessageFailure,
                conversationId: ConversationId?
            ) = Unit.right()

            override suspend fun handleFailureAndUpdateMessageStatus(
                failure: CoreFailure,
                conversationId: ConversationId,
                messageId: String,
                messageType: String,
                scheduleResendIfNoNetwork: Boolean
            ) {
                handleMessageFailure()
            }
        }

        private val messageSyncTracker = object : com.wire.kalium.logic.feature.message.sync.MessageSyncTrackerUseCase {
            override suspend fun trackMessageInsert(message: Message) = Unit
            override suspend fun trackMessageDelete(conversationId: ConversationId, messageId: String) = Unit
            override suspend fun trackMessageUpdate(conversationId: ConversationId, messageId: String) = Unit
        }

        fun withSendMessage(block: () -> Either<CoreFailure, Unit>) = apply {
            sendMessage = block
        }

        fun withUpdateMessage(block: (String) -> Unit) = apply {
            updateMessage = block
        }

        fun withUpdateMessageStatus(block: (MessageEntity.Status) -> Unit) = apply {
            updateMessageStatus = block
        }

        fun withHandleMessageFailure(block: () -> Unit) = apply {
            handleMessageFailure = block
        }

        fun arrange() = this to SendEditMultipartMessageUseCase(
            messageRepository = messageRepository,
            selfUserId = TestUser.SELF.id,
            provideClientId = clientIdProvider,
            slowSyncRepository = FakeSlowSyncRepository(),
            messageSender = messageSender,
            messageSendFailureHandler = sendFailureHandler,
            getMessageAttachments = { _, _ -> emptyList<MessageAttachment>().right() },
            messageSyncTracker = messageSyncTracker,
            dispatchers = dispatcher
        )
        
    }

}
