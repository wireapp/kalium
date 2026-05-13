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

import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.message.composite.Button
import com.wire.kalium.logic.framework.TestMessage
import com.wire.kalium.logic.framework.TestUser
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class MessageCompositeEditHandlerTest {

    @Test
    fun givenEditMatchesOriginalSender_whenHandling_thenShouldUpdateContentWithCorrectParameters() =
        runTest {
            val (arrangement, messageCompositeEditHandler) = arrange {
                withGetMessageById(Either.Right(ORIGINAL_MESSAGE))
                withEditCompositeMessage(Unit.right())
            }

            messageCompositeEditHandler.handle(
                EDIT_MESSAGE.copy(senderUserId = ORIGINAL_SENDER_USER_ID),
                EDIT_COMPOSITE_CONTENT
            )

            with(arrangement) {
                verifySuspend(VerifyMode.exactly(1)) {
                    messageRepository.updateCompositeMessage(
                        eq(EDIT_MESSAGE.conversationId),
                        eq(EDIT_COMPOSITE_CONTENT),
                        eq(EDIT_MESSAGE.id),
                        eq(EDIT_MESSAGE.date)
                    )
                }
            }
        }

    @Test
    fun givenEditDoesNOTMatchesOriginalSender_whenHandling_thenShouldNOTUpdateContent() = runTest {
        val (arrangement, messageCompositeEditHandler) = arrange {
            withGetMessageById(Either.Right(ORIGINAL_MESSAGE))
            withEditCompositeMessage(Unit.right())
        }

        messageCompositeEditHandler.handle(
            EDIT_MESSAGE.copy(senderUserId = TestUser.OTHER_USER_ID),
            EDIT_COMPOSITE_CONTENT
        )

        with(arrangement) {
            verifySuspend(VerifyMode.not) {
                messageRepository.updateCompositeMessage(any(), any(), any(), any())
            }
        }
    }

    private suspend fun arrange(block: suspend Arrangement.() -> Unit) =
        Arrangement(block).arrange()

    private class Arrangement(
        private val block: suspend Arrangement.() -> Unit
    ) {
        val messageRepository = mock<MessageRepository>()

        suspend fun arrange() = block().run {
            everySuspend {
                messageRepository.updateTextMessage(any(), any(), any(), any())
            } returns Either.Right(Unit)
            everySuspend {
                messageRepository.updateMessageStatus(any(), any(), any())
            } returns Either.Right(Unit)
            this@Arrangement to MessageCompositeEditHandlerImpl(
                messageRepository = messageRepository,
            )
        }

        suspend fun withGetMessageById(result: Either<com.wire.kalium.common.error.StorageFailure, com.wire.kalium.logic.data.message.Message>) {
            everySuspend {
                messageRepository.getMessageById(any(), any())
            } returns result
        }

        suspend fun withEditCompositeMessage(result: Either<com.wire.kalium.common.error.StorageFailure, Unit>) {
            everySuspend {
                messageRepository.updateCompositeMessage(any(), any(), any(), any())
            } returns result
        }
    }

    private companion object {
        val ORIGINAL_MESSAGE = TestMessage.TEXT_MESSAGE
        val ORIGINAL_MESSAGE_ID = ORIGINAL_MESSAGE.id
        val ORIGINAL_SENDER_USER_ID = ORIGINAL_MESSAGE.senderUserId

        val EDIT_COMPOSITE_CONTENT = MessageContent.CompositeEdited(
            editMessageId = ORIGINAL_MESSAGE_ID,
            newTextContent = (ORIGINAL_MESSAGE.content as MessageContent.Text).copy(value = "edited content"),
            newButtonList = listOf(Button("new added button", "new id", false))
        )

        val EDIT_MESSAGE = TestMessage.signalingMessage(
            content = EDIT_COMPOSITE_CONTENT
        )
    }
}
