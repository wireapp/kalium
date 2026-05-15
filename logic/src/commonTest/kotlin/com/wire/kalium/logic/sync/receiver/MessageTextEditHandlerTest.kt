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

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.notification.NotificationEventsManager
import com.wire.kalium.logic.framework.TestMessage
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.sync.receiver.handler.MessageTextEditHandlerImpl
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.util.time.UNIX_FIRST_DATE
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
import kotlin.time.Duration.Companion.minutes

class MessageTextEditHandlerTest {

    @Test
    fun givenEditMatchesOriginalSender_whenHandling_thenShouldUpdateContentWithCorrectParameters() = runTest {
        val (arrangement, messageTextEditHandler) = arrange {
            withGetMessageById(Either.Right(ORIGINAL_MESSAGE))
        }

        messageTextEditHandler.handle(EDIT_MESSAGE.copy(senderUserId = ORIGINAL_SENDER_USER_ID), EDIT_CONTENT)

        with(arrangement) {
            verifySuspend(VerifyMode.exactly(1)) {
                messageRepository.updateTextMessage(
                    eq(EDIT_MESSAGE.conversationId),
                    eq(EDIT_CONTENT),
                    eq(EDIT_MESSAGE.id),
                    eq(EDIT_MESSAGE.date)
                )
            }
        }
    }

    @Test
    fun givenEditDoesNOTMatchesOriginalSender_whenHandling_thenShouldNOTUpdateContent() = runTest {
        val (arrangement, messageTextEditHandler) = arrange {
            withGetMessageById(Either.Right(ORIGINAL_MESSAGE))
        }

        messageTextEditHandler.handle(EDIT_MESSAGE.copy(senderUserId = TestUser.OTHER_USER_ID), EDIT_CONTENT)

        with(arrangement) {
            verifySuspend(VerifyMode.not) {
                messageRepository.updateTextMessage(any(), any(), any(), any())
            }
        }
    }

    @Test
    fun givenEditIsNewerThanLocalPendingStoredEdit_whenHandling_thenShouldUpdateTheWholeMessageDataAndStatus() = runTest {
        val originalContent = TestMessage.TEXT_CONTENT
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
        val (arrangement, messageTextEditHandler) = arrange {
            withGetMessageById(Either.Right(originalMessage))
        }

        messageTextEditHandler.handle(editMessage, editContent)

        with(arrangement) {
            verifySuspend(VerifyMode.exactly(1)) {
                messageRepository.updateTextMessage(
                    eq(editMessage.conversationId),
                    eq(editContent),
                    eq(editMessage.id),
                    eq(editMessage.date)
                )
            }
            verifySuspend(VerifyMode.exactly(1)) {
                messageRepository.updateMessageStatus(eq(MessageEntity.Status.SENT), eq(editMessage.conversationId), eq(editMessage.id))
            }
            verifySuspend(VerifyMode.exactly(1)) {
                notificationEventsManager.scheduleEditMessageNotification(eq(editMessage), eq(editContent))
            }
        }
    }

    @Test
    fun givenEditIsOlderThanLocalPendingStoredEdit_whenHandling_thenShouldUpdateOnlyMessageIdAndDate() = runTest {
        val originalContent = TestMessage.TEXT_CONTENT
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
            verifySuspend(VerifyMode.exactly(1)) {
                messageRepository.updateTextMessage(any(), eq(expectedContent), eq(editMessage.id), eq(originalEditStatus.lastEditInstant))
            }
            verifySuspend(VerifyMode.not) {
                messageRepository.updateMessageStatus(any(), any(), any())
            }
        }
    }

    @Test
    fun givenAnAlreadyEditedMessage_whenNewEditIsInTheFuture_thenMessageContentIsUpdatd() = runTest {
        val originalContent = TestMessage.TEXT_CONTENT
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
        val expectedContent = MessageContent.TextEdited(
            editMessageId = editContent.editMessageId,
            newContent = editContent.newContent,
            newMentions = editContent.newMentions
        )
        val (arrangement, messageTextEditHandler) = arrange {
            withGetMessageById(Either.Right(originalMessage))
        }

        messageTextEditHandler.handle(editMessage, editContent)

        with(arrangement) {
            verifySuspend(VerifyMode.exactly(1)) {
                messageRepository.updateTextMessage(any(), eq(expectedContent), eq(editMessage.id), eq(editMessage.date))
            }
            verifySuspend(VerifyMode.exactly(1)) {
                messageRepository.updateMessageStatus(any(), any(), any())
            }
        }
    }

    private suspend fun arrange(block: suspend Arrangement.() -> Unit) = Arrangement(block).arrange()

    private class Arrangement(
        private val block: suspend Arrangement.() -> Unit
    ) {
        val messageRepository = mock<MessageRepository>()
        val notificationEventsManager = mock<NotificationEventsManager>(mode = MockMode.autoUnit)

        suspend fun arrange() = block().run {
            everySuspend {
                messageRepository.updateTextMessage(any(), any(), any(), any())
            } returns Either.Right(Unit)
            everySuspend {
                messageRepository.updateMessageStatus(any(), any(), any())
            } returns Either.Right(Unit)
            this@Arrangement to MessageTextEditHandlerImpl(
                messageRepository = messageRepository,
                notificationEventsManager = notificationEventsManager,
            )
        }

        suspend fun withGetMessageById(result: Either<StorageFailure, Message>) {
            everySuspend {
                messageRepository.getMessageById(any(), any())
            } returns result
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
