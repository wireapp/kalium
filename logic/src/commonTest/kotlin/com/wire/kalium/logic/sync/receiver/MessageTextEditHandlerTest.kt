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

package com.wire.kalium.logic.sync.receiver

import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.framework.TestMessage
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.sync.receiver.handler.MessageTextEditHandlerImpl
import com.wire.kalium.logic.util.arrangement.repository.MessageRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.MessageRepositoryArrangementImpl
import com.wire.kalium.logic.util.arrangement.usecase.EphemeralEventsNotificationManagerArrangement
import com.wire.kalium.logic.util.arrangement.usecase.EphemeralEventsNotificationManagerArrangementImpl
import com.wire.kalium.persistence.dao.message.MessageEntity
import io.mockative.any
import io.mockative.anything
import io.mockative.eq
import io.mockative.given
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class MessageTextEditHandlerTest {

    @Test
    fun givenEditMatchesOriginalSender_whenHandling_thenShouldUpdateContentWithCorrectParameters() = runTest {
        val (arrangement, messageTextEditHandler) = arrange {
            withGetMessageById(Either.Right(ORIGINAL_MESSAGE))
        }

        messageTextEditHandler.handle(EDIT_MESSAGE.copy(senderUserId = ORIGINAL_SENDER_USER_ID), EDIT_CONTENT)

        with(arrangement) {
            verify(messageRepository)
                .suspendFunction(messageRepository::updateTextMessage)
                .with(eq(EDIT_MESSAGE.conversationId), eq(EDIT_CONTENT), eq(EDIT_MESSAGE.id), eq(EDIT_MESSAGE.date))
                .wasInvoked(exactly = once)
        }
    }

    @Test
    fun givenEditDoesNOTMatchesOriginalSender_whenHandling_thenShouldNOTUpdateContent() = runTest {
        val (arrangement, messageTextEditHandler) = arrange {
            withGetMessageById(Either.Right(ORIGINAL_MESSAGE))
        }

        messageTextEditHandler.handle(EDIT_MESSAGE.copy(senderUserId = TestUser.OTHER_USER_ID), EDIT_CONTENT)

        with(arrangement) {
            verify(messageRepository)
                .suspendFunction(messageRepository::updateTextMessage)
                .with(any(), any(), any(), any())
                .wasNotInvoked()
        }
    }

    @Test
    fun givenEditIsNewerThanLocalPendingStoredEdit_whenHandling_thenShouldUpdateTheWholeMessageDataAndStatus() = runTest {
        val originalContent = TestMessage.TEXT_CONTENT
        val originalEditStatus = Message.EditStatus.Edited("2000-01-01T12:00:00.000Z")
        val originalMessage = ORIGINAL_MESSAGE.copy(
            editStatus = originalEditStatus,
            content = originalContent,
            status = Message.Status.Pending
        )
        val editContent = EDIT_CONTENT
        val editMessage = EDIT_MESSAGE.copy(
            date = "2000-01-01T12:00:00.001Z",
            content = editContent
        )
        val (arrangement, messageTextEditHandler) = arrange {
            withGetMessageById(Either.Right(originalMessage))
        }

        messageTextEditHandler.handle(editMessage, editContent)

        with(arrangement) {
            verify(messageRepository)
                .suspendFunction(messageRepository::updateTextMessage)
                .with(eq(editMessage.conversationId), eq(editContent), eq(editMessage.id), eq(editMessage.date))
                .wasInvoked(exactly = once)
            verify(messageRepository)
                .suspendFunction(messageRepository::updateMessageStatus)
                .with(eq(MessageEntity.Status.SENT), eq(editMessage.conversationId), eq(editMessage.id))
                .wasInvoked(exactly = once)
            verify(ephemeralNotifications)
                .suspendFunction(ephemeralNotifications::scheduleEditMessageNotification)
                .with(eq(editMessage), eq(editContent), )
                .wasInvoked(exactly = once)
        }
    }

    @Test
    fun givenEditIsOlderThanLocalPendingStoredEdit_whenHandling_thenShouldUpdateOnlyMessageIdAndDate() = runTest {
        val originalContent = TestMessage.TEXT_CONTENT
        val originalEditStatus = Message.EditStatus.Edited("2000-01-01T12:00:00.001Z")
        val originalMessage = ORIGINAL_MESSAGE.copy(
            editStatus = originalEditStatus,
            content = originalContent,
            status = Message.Status.Pending

        )
        val editContent = EDIT_CONTENT
        val editMessage = EDIT_MESSAGE.copy(
            date = "2000-01-01T12:00:00.000Z",
            content = editContent
        )
        val expectedContent = MessageContent.TextEdited(
            editMessageId = editContent.editMessageId,
            newContent = originalContent.value,
            newMentions = originalContent.mentions
        )
        val (arrangement, messageTextEditHandler) = arrange {
            withGetMessageById(Either.Right(originalMessage))
        }

        messageTextEditHandler.handle(editMessage, editContent)

        with(arrangement) {
            verify(messageRepository)
                .suspendFunction(messageRepository::updateTextMessage)
                .with(any(), eq(expectedContent), eq(editMessage.id), eq(originalEditStatus.lastTimeStamp))
                .wasInvoked(exactly = once)
            verify(messageRepository)
                .suspendFunction(messageRepository::updateMessageStatus)
                .with(any(), any(), any())
                .wasNotInvoked()
        }
    }

    private fun arrange(block: Arrangement.() -> Unit) = Arrangement(block).arrange()

    private class Arrangement(
        private val block: Arrangement.() -> Unit
    ) : MessageRepositoryArrangement by MessageRepositoryArrangementImpl(),
        EphemeralEventsNotificationManagerArrangement by EphemeralEventsNotificationManagerArrangementImpl() {

        init {
            given(messageRepository)
                .suspendFunction(messageRepository::updateTextMessage)
                .whenInvokedWith(anything(), anything(), anything(), anything())
                .thenReturn(Either.Right(Unit))
            given(messageRepository)
                .suspendFunction(messageRepository::updateMessageStatus)
                .whenInvokedWith(anything(), anything(), anything())
                .thenReturn(Either.Right(Unit))
        }

        fun arrange() = block().run {
            this@Arrangement to MessageTextEditHandlerImpl(
                messageRepository = messageRepository,
                editMessageNotificationsManager = ephemeralNotifications,
            )
        }

    }

    private companion object {
        val ORIGINAL_MESSAGE = TestMessage.TEXT_MESSAGE
        val ORIGINAL_MESSAGE_ID = ORIGINAL_MESSAGE.id
        val ORIGINAL_SENDER_USER_ID = ORIGINAL_MESSAGE.senderUserId
        val EDIT_CONTENT = MessageContent.TextEdited(
            editMessageId = ORIGINAL_MESSAGE_ID,
            newContent = "some new content",
            newMentions = listOf()
        )
        val EDIT_MESSAGE = TestMessage.signalingMessage(
            content = EDIT_CONTENT
        )
    }
}
