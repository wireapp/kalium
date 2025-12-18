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
package com.wire.kalium.logic.sync.receiver.handler

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.fakes.FakeMessageRepository
import com.wire.kalium.logic.fakes.FakeNotificationEventsManager
import com.wire.kalium.logic.framework.TestMessage
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.util.time.UNIX_FIRST_DATE
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes

class MessageMultipartEditHandlerTest {

    @Test
    fun givenEditMatchesOriginalSender_whenHandling_thenShouldUpdateContentWithCorrectParameters() = runTest {
        var called = 0
        val (_, handler) = Arrangement()
            .withUpdateMultipartMessage { called++ }
            .arrange()

        handler.handle(EDIT_MESSAGE.copy(senderUserId = ORIGINAL_SENDER_USER_ID), EDIT_CONTENT)

        assertEquals(1, called)
    }

    @Test
    fun givenEditDoesNOTMatchesOriginalSender_whenHandling_thenShouldNOTUpdateContent() = runTest {
        var called = 0
        val (_, handler) = Arrangement()
            .withUpdateMultipartMessage { called++ }
            .arrange()

        handler.handle(EDIT_MESSAGE.copy(senderUserId = TestUser.OTHER_USER_ID), EDIT_CONTENT)

        assertEquals(0, called)
    }

    @Test
    fun givenEditIsNewerThanLocalPendingStoredEdit_whenHandling_thenShouldUpdateTheWholeMessageDataAndStatus() = runTest {
        var updateCalled = 0
        var updateStatusCalled = 0
        var scheduleNotificationCalled = 0

        val originalContent = TestMessage.multipartMessage().content
        val originalEditStatus = Message.EditStatus.Edited(Instant.UNIX_FIRST_DATE)
        val originalMessage = ORIGINAL_MESSAGE.copy(
            editStatus = originalEditStatus,
            content = originalContent,
            status = Message.Status.Pending
        )
        val editContent = EDIT_CONTENT
        val editMessage = EDIT_MESSAGE.copy(
            date = Instant.UNIX_FIRST_DATE,
            content = editContent
        )
        val (_, handler) = Arrangement()
            .withGetMessageById(originalMessage)
            .withUpdateMultipartMessage { updateCalled++ }
            .withUpdateMessageStatus { updateStatusCalled++ }
            .withScheduleNotification { scheduleNotificationCalled++ }
            .arrange()

        handler.handle(editMessage, editContent)

        assertEquals(1, updateCalled)
        assertEquals(1, updateStatusCalled)
        assertEquals(1, scheduleNotificationCalled)
    }

    @Test
    fun givenEditIsOlderThanLocalPendingStoredEdit_whenHandling_thenShouldUpdateOnlyMessageIdAndDate() = runTest {
        var updateCalled = 0
        var updateStatusCalled = 0
        val originalContent = TestMessage.multipartMessage().content as MessageContent.Multipart
        val originalEditStatus = Message.EditStatus.Edited(Instant.DISTANT_FUTURE) // original message date is newer than edit date
        val originalMessage = ORIGINAL_MESSAGE.copy(
            editStatus = originalEditStatus,
            content = originalContent,
            status = Message.Status.Pending

        )
        val editContent = EDIT_CONTENT
        val editMessage = EDIT_MESSAGE.copy(
            date = originalEditStatus.lastEditInstant - 10.minutes, // edit date is older than original message date
            content = editContent
        )
        val expectedContent = MessageContent.MultipartEdited(
            editMessageId = editContent.editMessageId,
            newTextContent = originalContent.value,
            newMentions = originalContent.mentions,
            newAttachments = originalContent.attachments,
        )
        val (_, handler) = Arrangement()
            .withGetMessageById(originalMessage)
            .withUpdateMultipartMessage { content, editInstant ->
                if (content == expectedContent && editInstant == originalEditStatus.lastEditInstant) {
                    updateCalled++
                }
            }
            .withUpdateMessageStatus { updateStatusCalled++ }
            .arrange()

        handler.handle(editMessage, editContent)

        assertEquals(1, updateCalled)
        assertEquals(0, updateStatusCalled)
    }

    @Test
    fun givenAnAlreadyEditedMessage_whenNewEditIsInTheFuture_thenMessageContentIsUpdated() = runTest {
        var updateCalled = 0
        var updateStatusCalled = 0
        val originalContent = TestMessage.multipartMessage().content
        val originalEditStatus = Message.EditStatus.Edited(Instant.UNIX_FIRST_DATE)
        val originalMessage = ORIGINAL_MESSAGE.copy(
            editStatus = originalEditStatus,
            content = originalContent,
            status = Message.Status.Sent
        )
        val editContent = EDIT_CONTENT
        val editMessage = EDIT_MESSAGE.copy(
            date = Instant.UNIX_FIRST_DATE + 10.minutes,
            content = editContent
        )
        val expectedContent = MessageContent.MultipartEdited(
            editMessageId = editContent.editMessageId,
            newTextContent = editContent.newTextContent,
            newMentions = editContent.newMentions,
            newAttachments = editContent.newAttachments,
        )
        val (_, messageTextEditHandler) = Arrangement()
            .withGetMessageById(originalMessage)
            .withUpdateMultipartMessage { content, editInstant ->
                if (content == expectedContent && editInstant == editMessage.date) {
                    updateCalled++
                }
            }
            .withUpdateMessageStatus { updateStatusCalled++ }
            .arrange()

        messageTextEditHandler.handle(editMessage, editContent)

        assertEquals(1, updateCalled)
        assertEquals(1, updateStatusCalled)
    }

    private class Arrangement {

        private var message: Message = ORIGINAL_MESSAGE
        private var updateMultipartMessage: (MessageContent.MultipartEdited, Instant) -> Unit = { _, _ -> }
        private var updateMessageStatus: () -> Unit = {}
        private var scheduleMessageNotification: () -> Unit = {}

        private val repository: MessageRepository = object : FakeMessageRepository() {

            override suspend fun getMessageById(conversationId: ConversationId, messageUuid: String): Either<StorageFailure, Message> {
                return message.right()
            }

            override suspend fun updateMultipartMessage(
                conversationId: ConversationId,
                messageContent: MessageContent.MultipartEdited,
                newMessageId: String,
                editInstant: Instant
            ): Either<CoreFailure, Unit> {
                return updateMultipartMessage(messageContent, editInstant).right()
            }

            override suspend fun updateMessageStatus(
                messageStatus: MessageEntity.Status,
                conversationId: ConversationId,
                messageUuid: String
            ): Either<CoreFailure, Unit> {
                return updateMessageStatus().right()
            }
        }

        private val notificationEventsManager = object : FakeNotificationEventsManager() {
            override suspend fun scheduleEditMessageNotification(message: Message, messageContent: MessageContent.MultipartEdited) {
                scheduleMessageNotification()
            }
        }

        fun withGetMessageById(message: Message) = apply {
            this.message = message
        }

        fun withUpdateMultipartMessage(block: (MessageContent.MultipartEdited, Instant) -> Unit) = apply {
            updateMultipartMessage = block
        }

        fun withUpdateMultipartMessage(block: () -> Unit) = apply {
            updateMultipartMessage = { _, _ -> block() }
        }

        fun withUpdateMessageStatus(block: () -> Unit) = apply {
            updateMessageStatus = block
        }

        fun withScheduleNotification(block: () -> Unit) = apply {
            scheduleMessageNotification = block
        }

        fun arrange() = this to MessageMultipartEditHandlerImpl(
            messageRepository = repository,
            notificationEventsManager = notificationEventsManager,
        )
    }
    private companion object {
        val ORIGINAL_MESSAGE = TestMessage.multipartMessage()
        val ORIGINAL_MESSAGE_ID = ORIGINAL_MESSAGE.id
        val ORIGINAL_SENDER_USER_ID = ORIGINAL_MESSAGE.senderUserId
        val EDIT_CONTENT = MessageContent.MultipartEdited(
            editMessageId = ORIGINAL_MESSAGE_ID,
            newTextContent = "some new content",
            newMentions = listOf()
        )
        val EDIT_MESSAGE = TestMessage.signalingMessage(
            content = EDIT_CONTENT
        )
    }
}
