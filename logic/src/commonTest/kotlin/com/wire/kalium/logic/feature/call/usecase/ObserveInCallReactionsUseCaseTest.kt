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

package com.wire.kalium.logic.feature.call.usecase

import app.cash.turbine.test
import com.wire.kalium.logic.data.call.InCallReactionMessage
import com.wire.kalium.logic.data.call.InCallReactionsDataSource
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.sync.receiver.handler.InCallEmojiMessageHandlerImpl
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class ObserveInCallReactionsUseCaseTest {

    @Test
    fun givenIncomingHandlerAndCallObserverShareRepository_thenHandledEmojiIsObserved() = runTest {
        val sharedRepository = InCallReactionsDataSource()
        val incomingHandler = InCallEmojiMessageHandlerImpl(sharedRepository)
        val observeInCallReactions = ObserveInCallReactionsUseCaseImpl(sharedRepository)

        observeInCallReactions(conversationId).test {
            incomingHandler.handle(signalingMessage, content)

            assertEquals(
                InCallReactionMessage(conversationId, senderUserId, content.emojis.keys),
                awaitItem(),
            )
        }
    }

    private companion object {
        val conversationId = ConversationId("conversation-id", "wire.example")
        val senderUserId = UserId("sender-id", "wire.example")
        val content = MessageContent.InCallEmoji(linkedMapOf("first" to 1, "second" to 2))
        val signalingMessage = Message.Signaling(
            id = "signaling-id",
            content = content,
            conversationId = conversationId,
            date = Instant.parse("2026-08-19T10:15:30Z"),
            senderUserId = senderUserId,
            senderClientId = ClientId("sender-client"),
            status = Message.Status.Sent,
            isSelfMessage = false,
            expirationData = null,
        )
    }
}
