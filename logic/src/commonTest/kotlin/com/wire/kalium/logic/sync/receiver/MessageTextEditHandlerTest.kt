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
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.notification.NotificationEventsManager
import com.wire.kalium.logic.framework.TestMessage
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.sync.receiver.handler.MessageTextEditHandlerImpl
import com.wire.kalium.persistence.dao.message.MessageDAO
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.persistence.dao.message.MessageEntityContent
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.minutes

class MessageTextEditHandlerTest {
    @Test
    fun authorizedTextEditChangesIdentityContentAndDate() = runTest {
        val fixture = Fixture().givenOriginal(ORIGINAL)
        fixture.handler.handle(EDIT, CONTENT)
        verifySuspend {
            fixture.dao.updateTextMessageContent(
                EDIT.date, ORIGINAL.conversationId.toDao(), ORIGINAL.id,
                MessageEntityContent.Text(CONTENT.newContent, emptyList(), emptyList()), EDIT.id, null
            )
            fixture.notifications.scheduleEditMessageNotification(EDIT, CONTENT)
        }
    }

    @Test
    fun editFromAnotherSenderIsIgnoredWithoutMutationOrPresentation() = runTest {
        val fixture = Fixture().givenOriginal(ORIGINAL)
        assertIs<Either.Left<StorageFailure>>(fixture.handler.handle(EDIT.copy(senderUserId = TestUser.OTHER_USER_ID), CONTENT))
        verifySuspend(VerifyMode.not) {
            fixture.dao.updateTextMessageContent(any(), any(), any(), any(), any(), any())
            fixture.notifications.scheduleEditMessageNotification(any(), any<MessageContent.TextEdited>())
        }
    }

    @Test
    fun newerOrEqualServerEditAlsoCompletesThePendingLocalEdit() = runTest {
        for (date in listOf(EDIT.date, EDIT.date - 1.minutes)) {
            val fixture = Fixture().givenOriginal(ORIGINAL.copy(editStatus = Message.EditStatus.Edited(date), status = Message.Status.Pending))
            fixture.handler.handle(EDIT, CONTENT)
            verifySuspend {
                fixture.dao.updateTextMessageContent(
                    EDIT.date, ORIGINAL.conversationId.toDao(), ORIGINAL.id,
                    MessageEntityContent.Text(CONTENT.newContent, emptyList(), emptyList()), EDIT.id, MessageEntity.Status.SENT
                )
                fixture.notifications.scheduleEditMessageNotification(EDIT, CONTENT)
            }
        }
    }

    @Test
    fun olderServerEditRekeysWhilePreservingNewerLocalTextDateAndPendingStatus() = runTest {
        val localDate = EDIT.date + 1.minutes
        val original = ORIGINAL.copy(
            content = MessageContent.Text("pending local text"), editStatus = Message.EditStatus.Edited(localDate), status = Message.Status.Pending
        )
        val fixture = Fixture().givenOriginal(original)
        fixture.handler.handle(EDIT, CONTENT)
        verifySuspend {
            fixture.dao.updateTextMessageContent(
                localDate, ORIGINAL.conversationId.toDao(), ORIGINAL.id,
                MessageEntityContent.Text("pending local text", emptyList(), emptyList()), EDIT.id, null
            )
        }
        verifySuspend(VerifyMode.not) { fixture.notifications.scheduleEditMessageNotification(any(), any<MessageContent.TextEdited>()) }
    }

    @Test
    fun storageFailureDoesNotEmitAnEditNotification() = runTest {
        val fixture = Fixture().givenOriginal(ORIGINAL)
        everySuspend { fixture.dao.updateTextMessageContent(any(), any(), any(), any(), any(), any()) } throws IllegalStateException("write failed")
        assertIs<Either.Left<*>>(fixture.handler.handle(EDIT, CONTENT))
        verifySuspend(VerifyMode.not) { fixture.notifications.scheduleEditMessageNotification(any(), any<MessageContent.TextEdited>()) }
    }

    private class Fixture {
        val repository = mock<MessageRepository>()
        val dao = mock<MessageDAO>(mode = MockMode.autoUnit)
        val notifications = mock<NotificationEventsManager>(mode = MockMode.autoUnit)
        val handler = MessageTextEditHandlerImpl(repository, dao, notifications)
        suspend fun givenOriginal(message: Message) = apply {
            everySuspend { repository.getMessageById(any(), any()) } returns Either.Right(message)
        }
    }

    private companion object {
        val ORIGINAL = TestMessage.TEXT_MESSAGE
        val CONTENT = MessageContent.TextEdited(editMessageId = ORIGINAL.id, newContent = "server text", newMentions = emptyList())
        val EDIT = TestMessage.signalingMessage(content = CONTENT).copy(senderUserId = ORIGINAL.senderUserId)
    }
}
