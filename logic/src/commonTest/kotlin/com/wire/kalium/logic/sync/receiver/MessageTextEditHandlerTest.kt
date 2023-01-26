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

package com.wire.kalium.logic.sync.receiver

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.framework.TestMessage
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.sync.receiver.message.MessageTextEditHandler
import com.wire.kalium.logic.sync.receiver.message.MessageTextEditHandlerImpl
import io.mockative.Mock
import io.mockative.any
import io.mockative.anything
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class MessageTextEditHandlerTest {

    @Test
    fun givenEditMatchesOriginalSender_whenHandling_thenShouldUpdateContentWithCorrectParameters() = runTest {
        val (arrangement, messageTextEditHandler) = Arrangement()
            .withCurrentMessageByIdReturning(Either.Right(ORIGINAL_MESSAGE))
            .arrange()

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
        val (arrangement, messageTextEditHandler) = Arrangement()
            .withCurrentMessageByIdReturning(Either.Right(ORIGINAL_MESSAGE))
            .arrange()

        messageTextEditHandler.handle(EDIT_MESSAGE.copy(senderUserId = TestUser.OTHER_USER_ID), EDIT_CONTENT)

        with(arrangement) {
            verify(messageRepository)
                .suspendFunction(messageRepository::updateTextMessage)
                .with(any(), any(), any(), any())
                .wasNotInvoked()
        }
    }

    private class Arrangement {

        @Mock
        val messageRepository: MessageRepository = mock(MessageRepository::class)

        init {
            given(messageRepository)
                .suspendFunction(messageRepository::updateTextMessage)
                .whenInvokedWith(anything(), anything(), anything(), anything())
                .thenReturn(Either.Right(Unit))
        }

        fun withCurrentMessageByIdReturning(result: Either<CoreFailure, Message>) = apply {
            given(messageRepository)
                .suspendFunction(messageRepository::getMessageById)
                .whenInvokedWith(anything(), anything())
                .thenReturn(result)
        }

        fun arrange(): Pair<Arrangement, MessageTextEditHandler> =
            this to MessageTextEditHandlerImpl(messageRepository)

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
