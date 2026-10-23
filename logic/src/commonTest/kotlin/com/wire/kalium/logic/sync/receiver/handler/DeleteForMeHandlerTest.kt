/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.persistence.dao.message.MessageDAO
import dev.mokkery.MockMode
import dev.mokkery.mock
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.util.arrangement.usecase.IsMessageSentInSelfConversationUseCaseArrangement
import com.wire.kalium.logic.util.arrangement.usecase.IsMessageSentInSelfConversationUseCaseArrangementImpl
import dev.mokkery.matcher.any
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.time.Instant
import kotlin.test.Test

class DeleteForMeHandlerTest {

    @Test
    fun givenMessageFromSelfConversation_whenHandling_thenMessageIsDeleted() = runTest {
        val (arrangement, handler) = arrange {
            withMessageSentInSelfConversationReturning(true)
        }

        handler.handle(MESSAGE, CONTENT)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.messageDAO.deleteMessage(MESSAGE_ID, CONVERSATION_ID.toDao())
        }
    }

    @Test
    fun givenMessageOutsideSelfConversation_whenHandling_thenMessageIsNotDeleted() = runTest {
        val (arrangement, handler) = arrange {
            withMessageSentInSelfConversationReturning(false)
        }

        handler.handle(MESSAGE, CONTENT)

        verifySuspend(VerifyMode.not) { arrangement.messageDAO.deleteMessage(any(), any()) }
    }

    private suspend fun arrange(block: suspend Arrangement.() -> Unit) = Arrangement(block).arrange()

    private class Arrangement(
        private val block: suspend Arrangement.() -> Unit,
    ) : IsMessageSentInSelfConversationUseCaseArrangement by IsMessageSentInSelfConversationUseCaseArrangementImpl() {

        val messageDAO = mock<MessageDAO>(MockMode.autoUnit)

        suspend fun arrange() = run {
            block()
            this@Arrangement to DeleteForMeHandlerImpl(
                messageDAO = messageDAO,
                isMessageSentInSelfConversation = isMessageSentInSelfConversationUseCase
            )
        }
    }

    private companion object {
        val SELF_USER_ID = UserId("self-user", "wire.com")
        val CONVERSATION_ID = ConversationId("conversation", "wire.com")
        const val MESSAGE_ID = "message-id"
        val CONTENT = MessageContent.DeleteForMe(
            messageId = MESSAGE_ID,
            conversationId = CONVERSATION_ID,
        )
        val MESSAGE = Message.Signaling(
            id = "signaling-message-id",
            content = CONTENT,
            conversationId = CONVERSATION_ID,
            date = Instant.parse("2026-08-29T12:00:00Z"),
            senderUserId = SELF_USER_ID,
            senderClientId = ClientId("self-client"),
            status = Message.Status.Sent,
            isSelfMessage = true,
            expirationData = null,
        )
    }
}
