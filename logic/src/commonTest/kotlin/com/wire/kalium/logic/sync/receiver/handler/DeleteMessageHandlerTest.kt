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

import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.framework.TestMessage
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.util.arrangement.repository.AssetRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.AssetRepositoryArrangementImpl
import com.wire.kalium.logic.util.arrangement.repository.MessageRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.MessageRepositoryArrangementImpl
import com.wire.kalium.logic.util.arrangement.usecase.NotificationEventsManagerArrangement
import com.wire.kalium.logic.util.arrangement.usecase.EphemeralEventsNotificationManagerArrangementImpl
import io.mockative.any
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.once
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.time.Duration

class DeleteMessageHandlerTest {

    @Test
    fun givenDeleteNotFromTheOriginalSender_whenOriginalMessageIsEpheral_thenDelete() = runTest {
        val originalMessageID = "originalMessageID"
        val content = MessageContent.DeleteMessage(originalMessageID)
        val conversationId = ConversationId("conversationId", "conversationDomain")

        val originalMessage =
            TestMessage.TEXT_MESSAGE.copy(
                senderUserId = SELF_USER_ID,
                id = originalMessageID,
                conversationId = conversationId,
                expirationData = Message.ExpirationData(expireAfter = Duration.parse("PT1H"))
            )

        val (arrangement, handler) = arrange {
            withGetMessageById(Either.Right(originalMessage))
            withDeleteMessage(Either.Right(Unit))
        }

        handler(
            content = content,
            senderUserId = UserId("requesterID", "requesterDomain"),
            conversationId = conversationId
        )

        coVerify {
            arrangement.messageRepository.deleteMessage(eq(originalMessageID), eq(conversationId))
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenRequesterIsNotOriginalMessageSender_whenReceivingDeleteSignal_thenMarkAsDelete() = runTest {
        val originalMessageID = "originalMessageID"
        val deleteMessageSenderID = UserId("deleteMessageSenderID", "deleteMessageSenderDomain")
        val content = MessageContent.DeleteMessage(originalMessageID)
        val conversationId = ConversationId("conversationId", "conversationDomain")

        val originalMessage =
            TestMessage.TEXT_MESSAGE.copy(
                senderUserId = deleteMessageSenderID,
                id = originalMessageID,
                conversationId = conversationId,
                expirationData = null
            )
        val (arrangement, handler) = arrange {
            withGetMessageById(Either.Right(originalMessage))
        }

        handler(
            content = content,
            senderUserId = UserId("requesterID", "requesterDomain"),
            conversationId = conversationId
        )

        coVerify {
            arrangement.messageRepository.markMessageAsDeleted(eq(originalMessageID), eq(conversationId))
        }.wasNotInvoked()

        coVerify {
            arrangement.messageRepository.deleteMessage(eq(originalMessageID), eq(conversationId))
        }.wasNotInvoked()

        coVerify {
            arrangement.messageRepository.getMessageById(eq(conversationId), eq(originalMessageID))
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.notificationEventsManager.scheduleDeleteMessageNotification(any())
        }.wasNotInvoked()
    }

    @Test
    fun givenOriginalMessageIsNotSelfDelete_whenReceivingDeleteSignal_thenMarkAsDelete() = runTest {
        val originalMessageID = "originalMessageID"
        val deleteMessageSenderID = UserId("deleteMessageSenderID", "deleteMessageSenderDomain")
        val content = MessageContent.DeleteMessage(originalMessageID)
        val conversationId = ConversationId("conversationId", "conversationDomain")

        val originalMessage =
            TestMessage.TEXT_MESSAGE.copy(
                senderUserId = deleteMessageSenderID,
                id = originalMessageID,
                conversationId = conversationId,
                expirationData = null
            )
        val (arrangement, handler) = arrange {
            withGetMessageById(Either.Right(originalMessage))
            withMarkAsDeleted(Either.Right(Unit))
        }

        handler(
            content = content,
            senderUserId = deleteMessageSenderID,
            conversationId = conversationId
        )

        coVerify {
            arrangement.messageRepository.markMessageAsDeleted(eq(originalMessageID), eq(conversationId))
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.messageRepository.deleteMessage(eq(originalMessageID), eq(conversationId))
        }.wasNotInvoked()

        coVerify {
            arrangement.messageRepository.getMessageById(eq(conversationId), eq(originalMessageID))
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.notificationEventsManager.scheduleDeleteMessageNotification(eq(originalMessage))
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenOriginalMessageIsSelfDelete_whenReceivingDeleteSignal_thenDelete() = runTest {
        val originalMessageID = "originalMessageID"
        val deleteMessageSenderID = UserId("deleteMessageSenderID", "deleteMessageSenderDomain")
        val content = MessageContent.DeleteMessage(originalMessageID)
        val conversationId = ConversationId("conversationId", "conversationDomain")

        val originalMessage =
            TestMessage.TEXT_MESSAGE.copy(
                senderUserId = deleteMessageSenderID,
                id = originalMessageID,
                conversationId = conversationId,
                expirationData = Message.ExpirationData(Duration.parse("10s"))
            )
        val (arrangement, handler) = arrange {
            withGetMessageById(Either.Right(originalMessage))
            withDeleteMessage(Either.Right(Unit))
        }

        handler(content = content, senderUserId = deleteMessageSenderID, conversationId = conversationId)

        coVerify {
            arrangement.messageRepository.markMessageAsDeleted(eq(originalMessageID), eq(conversationId))
        }.wasNotInvoked()

        coVerify {
            arrangement.messageRepository.deleteMessage(eq(originalMessageID), eq(conversationId))
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.messageRepository.getMessageById(eq(conversationId), eq(originalMessageID))
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.notificationEventsManager.scheduleDeleteMessageNotification(any())
        }.wasNotInvoked()
    }

    @Test
    fun givenSelfIsTheOneSentTheDeleteEvent_whenReceivingDeleteSignal_thenDelete() = runTest {
        val originalMessageID = "originalMessageID"
        val originalMessageSenderID = UserId("originalMessageSenderID", "originalMessageSenderDomain")

        val deleteMessageSenderID = SELF_USER_ID
        val content = MessageContent.DeleteMessage(originalMessageID)
        val conversationId = ConversationId("conversationId", "conversationDomain")

        val originalMessage =
            TestMessage.TEXT_MESSAGE.copy(
                senderUserId = originalMessageSenderID,
                id = originalMessageID,
                conversationId = conversationId,
                expirationData = Message.ExpirationData(Duration.parse("10s"))
            )
        val (arrangement, handler) = arrange {
            withGetMessageById(Either.Right(originalMessage))
            withDeleteMessage(Either.Right(Unit))
        }

        handler(content = content, senderUserId = deleteMessageSenderID, conversationId = conversationId)

        coVerify {
            arrangement.messageRepository.markMessageAsDeleted(eq(originalMessageID), eq(conversationId))
        }.wasNotInvoked()

        coVerify {
            arrangement.messageRepository.deleteMessage(eq(originalMessageID), eq(conversationId))
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.messageRepository.getMessageById(eq(conversationId), eq(originalMessageID))
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.notificationEventsManager.scheduleDeleteMessageNotification(any())
        }.wasNotInvoked()
    }

    private companion object {
        val SELF_USER_ID = UserId("selfID", "selfDomain")
    }

    private fun arrange(block: suspend Arrangement.() -> Unit) = Arrangement(block).arrange()

    private class Arrangement(
        private val block: suspend Arrangement.() -> Unit
    ) : MessageRepositoryArrangement by MessageRepositoryArrangementImpl(),
        AssetRepositoryArrangement by AssetRepositoryArrangementImpl(),
        NotificationEventsManagerArrangement by EphemeralEventsNotificationManagerArrangementImpl() {

        fun arrange() = run {
            runBlocking { block() }
            this@Arrangement to DeleteMessageHandlerImpl(
                messageRepository = messageRepository,
                assetRepository = assetRepository,
                notificationEventsManager = notificationEventsManager,
                selfUserId = SELF_USER_ID
            )
        }
    }
}
