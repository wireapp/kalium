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

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.util.arrangement.repository.CompositeMessageRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.CompositeMessageRepositoryArrangementImpl
import com.wire.kalium.logic.util.arrangement.repository.MessageMetadataRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.MessageMetadataRepositoryArrangementImpl
import com.wire.kalium.logic.util.shouldFail
import io.mockative.any
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.once
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class ButtonActionConfirmationHandlerTest {

    @Test
    fun givenContentWithButtonId_whenHandlingEvent_thenThatButtonIdAsSelected() = runTest {
        val convId = CONVERSATION_ID
        val senderId = SENDER_USER_ID
        val content = MessageContent.ButtonActionConfirmation(
            referencedMessageId = "messageId",
            buttonId = "buttonId"
        )
        val (arrangement, handler) = Arrangement().arrange {
            withMarkSelected(result = Either.Right(Unit))
            withMessageOriginalSender(result = Either.Right(senderId))
        }

        handler.handle(convId, senderId, content)

        coVerify {
            arrangement.compositeMessageRepository.markSelected(eq("messageId"), eq(convId), eq("buttonId"))
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.compositeMessageRepository.resetSelection(any(), any())
        }.wasNotInvoked()
    }

    @Test
    fun givenContentWithNoButtonId_whenHandlingEvent_thenThanSelectionIsReset() = runTest {
        val convId = CONVERSATION_ID
        val senderId = SENDER_USER_ID
        val content = MessageContent.ButtonActionConfirmation(
            referencedMessageId = "messageId",
            buttonId = null
        )

        val (arrangement, handler) = Arrangement().arrange {
            withClearSelection(result = Either.Right(Unit))
            withMessageOriginalSender(result = Either.Right(senderId))
        }

        handler.handle(convId, senderId, content)

        coVerify {
            arrangement.compositeMessageRepository.markSelected(any(), any(), any())
        }.wasNotInvoked()

        coVerify {
            arrangement.compositeMessageRepository.resetSelection(eq("messageId"), eq(convId))
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenSenderIdIsNotTheSameAsOriginalSender_whenHandlingEvent_thenIgnore() = runTest {
        val convId = CONVERSATION_ID
        val senderId = SENDER_USER_ID

        val originalMessageSender = UserId("originalMessageSender", "domain")
        val content = MessageContent.ButtonActionConfirmation(
            referencedMessageId = "messageId",
            buttonId = null
        )

        val (arrangement, handler) = Arrangement().arrange {
            withClearSelection(result = Either.Right(Unit))
            withMessageOriginalSender(result = Either.Right(originalMessageSender))
        }

        handler.handle(convId, senderId, content).shouldFail {
            it is CoreFailure.InvalidEventSenderID
        }

        coVerify {
            arrangement.compositeMessageRepository.markSelected(any(), any(), any())
        }.wasNotInvoked()

        coVerify {
            arrangement.compositeMessageRepository.resetSelection(any(), any())
        }.wasNotInvoked()
    }

    private companion object {
        val CONVERSATION_ID = ConversationId("conversationId", "domain")
        val SENDER_USER_ID = UserId("senderUserId", "domain")
    }

    private class Arrangement :
        CompositeMessageRepositoryArrangement by CompositeMessageRepositoryArrangementImpl(),
        MessageMetadataRepositoryArrangement by MessageMetadataRepositoryArrangementImpl() {

        private val handler: ButtonActionConfirmationHandler = ButtonActionConfirmationHandlerImpl(
            compositeMessageRepository = compositeMessageRepository,
            messageMetadataRepository = messageMetadataRepository
        )

        fun arrange(block: suspend Arrangement.() -> Unit): Pair<Arrangement, ButtonActionConfirmationHandler> {
            runBlocking { block() }
            return this to handler
        }
    }
}
