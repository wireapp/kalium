/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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
package com.wire.kalium.logic.sync.handler

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.framework.TestMessage
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.sync.receiver.handler.DeleteMessageHandler
import com.wire.kalium.logic.sync.receiver.handler.DeleteMessageHandlerImpl
import io.mockative.Mock
import io.mockative.any
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.time.Duration

@OptIn(ExperimentalCoroutinesApi::class)
class DeleteMessageHandlerTest {

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
        val (arrangement, handler) = Arrangement()
            .withOriginalMessage(Either.Right(originalMessage))
            .arrange()

        handler(
            content = content,
            senderUserId = UserId("requesterID", "requesterDomain"),
            conversationId = conversationId
        )

        verify(arrangement.messageRepository)
            .suspendFunction(arrangement.messageRepository::markMessageAsDeleted)
            .with(eq(originalMessageID), eq(conversationId))
            .wasNotInvoked()

        verify(arrangement.messageRepository)
            .suspendFunction(arrangement.messageRepository::deleteMessage)
            .with(eq(originalMessageID), eq(conversationId))
            .wasNotInvoked()

        verify(arrangement.messageRepository)
            .suspendFunction(arrangement.messageRepository::getMessageById)
            .with(eq(conversationId), eq(originalMessageID))
            .wasInvoked(exactly = once)
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
        val (arrangement, handler) = Arrangement()
            .withOriginalMessage(Either.Right(originalMessage))
            .withMarkAsDeleted(Either.Right(Unit))
            .arrange()

        handler(
            content = content,
            senderUserId = deleteMessageSenderID,
            conversationId = conversationId
        )

        verify(arrangement.messageRepository)
            .suspendFunction(arrangement.messageRepository::markMessageAsDeleted)
            .with(eq(originalMessageID), eq(conversationId))
            .wasInvoked(exactly = once)

        verify(arrangement.messageRepository)
            .suspendFunction(arrangement.messageRepository::deleteMessage)
            .with(eq(originalMessageID), eq(conversationId))
            .wasNotInvoked()

        verify(arrangement.messageRepository)
            .suspendFunction(arrangement.messageRepository::getMessageById)
            .with(eq(conversationId), eq(originalMessageID))
            .wasInvoked(exactly = once)
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
        val (arrangement, handler) = Arrangement()
            .withOriginalMessage(Either.Right(originalMessage))
            .withDeleteMessage(Either.Right(Unit))
            .arrange()

        handler(content = content, senderUserId = deleteMessageSenderID, conversationId = conversationId)

        verify(arrangement.messageRepository)
            .suspendFunction(arrangement.messageRepository::markMessageAsDeleted)
            .with(eq(originalMessageID), eq(conversationId))
            .wasNotInvoked()

        verify(arrangement.messageRepository)
            .suspendFunction(arrangement.messageRepository::deleteMessage)
            .with(eq(originalMessageID), eq(conversationId))
            .wasInvoked(exactly = once)

        verify(arrangement.messageRepository)
            .suspendFunction(arrangement.messageRepository::getMessageById)
            .with(eq(conversationId), eq(originalMessageID))
            .wasInvoked(exactly = once)
    }


    private class Arrangement {

        @Mock
        val messageRepository: MessageRepository = mock(MessageRepository::class)

        @Mock
        val assetRepository: AssetRepository = mock(AssetRepository::class)

        private val handler: DeleteMessageHandler = DeleteMessageHandlerImpl(messageRepository, assetRepository)

        fun withOriginalMessage(result: Either<StorageFailure, Message>) = apply {
            given(messageRepository)
                .suspendFunction(messageRepository::getMessageById)
                .whenInvokedWith(any(), any())
                .thenReturn(result)
        }

        fun withMarkAsDeleted(result: Either<StorageFailure, Unit>) = apply {
            given(messageRepository)
                .suspendFunction(messageRepository::markMessageAsDeleted)
                .whenInvokedWith(any(), any())
                .thenReturn(result)
        }

        fun withDeleteMessage(result: Either<StorageFailure, Unit>) = apply {
            given(messageRepository)
                .suspendFunction(messageRepository::deleteMessage)
                .whenInvokedWith(any(), any())
                .thenReturn(result)
        }

        fun arrange() = this to handler
    }
}
