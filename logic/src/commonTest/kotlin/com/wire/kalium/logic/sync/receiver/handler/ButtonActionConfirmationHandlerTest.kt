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
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.CompositeMessageRepository
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageMetadataRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.util.shouldFail
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
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

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.compositeMessageRepository.markSelected(eq("messageId"), eq(convId), eq("buttonId"))
        }

        verifySuspend(VerifyMode.not) {
            arrangement.compositeMessageRepository.resetSelection(any(), any())
        }
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

        verifySuspend(VerifyMode.not) {
            arrangement.compositeMessageRepository.markSelected(any(), any(), any())
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.compositeMessageRepository.resetSelection(eq("messageId"), eq(convId))
        }
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

        verifySuspend(VerifyMode.not) {
            arrangement.compositeMessageRepository.markSelected(any(), any(), any())
        }

        verifySuspend(VerifyMode.not) {
            arrangement.compositeMessageRepository.resetSelection(any(), any())
        }
    }

    private companion object {
        val CONVERSATION_ID = ConversationId("conversationId", "domain")
        val SENDER_USER_ID = UserId("senderUserId", "domain")
    }

    private class Arrangement {
        val compositeMessageRepository = mock<CompositeMessageRepository>()
        val messageMetadataRepository = mock<MessageMetadataRepository>()

        private val handler: ButtonActionConfirmationHandler = ButtonActionConfirmationHandlerImpl(
            compositeMessageRepository = compositeMessageRepository,
            messageMetadataRepository = messageMetadataRepository
        )

        suspend fun withMarkSelected(result: Either<StorageFailure, Unit>) {
            everySuspend { compositeMessageRepository.markSelected(any(), any(), any()) } returns result
        }

        suspend fun withClearSelection(result: Either<StorageFailure, Unit>) {
            everySuspend { compositeMessageRepository.resetSelection(any(), any()) } returns result
        }

        suspend fun withMessageOriginalSender(result: Either<StorageFailure, UserId>) {
            everySuspend { messageMetadataRepository.originalSenderId(any(), any()) } returns result
        }

        suspend fun arrange(block: suspend Arrangement.() -> Unit): Pair<Arrangement, ButtonActionConfirmationHandler> {
            block()
            return this to handler
        }
    }
}
