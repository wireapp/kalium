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
package com.wire.kalium.logic.feature.message

import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.framework.TestMessage
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.util.MessageContentEncoder
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.eq
import dev.mokkery.mock
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals

class MessageSendingInterceptorTest {

    @Test
    fun givenQuoteReferenceFromAnotherConversation_whenPreparingMessage_thenFetchesQuotedMessageFromSourceConversation() = runTest {
        val targetConversationId = ConversationId("one-on-one", "wire.com")
        val sourceConversationId = ConversationId("group", "wire.com")
        val quotedMessageId = "quoted-message-id"
        val quotedMessage = TestMessage.TEXT_MESSAGE.copy(
            id = quotedMessageId,
            conversationId = sourceConversationId,
            content = MessageContent.Text("original group message")
        )
        val message = TestMessage.TEXT_MESSAGE.copy(
            id = "private-reply-id",
            conversationId = targetConversationId,
            senderUserId = TestUser.SELF.id,
            isSelfMessage = true,
            content = MessageContent.Text(
                value = "private reply",
                quotedMessageReference = MessageContent.QuoteReference(
                    quotedMessageId = quotedMessageId,
                    quotedMessageConversationId = sourceConversationId,
                    quotedMessageSha256 = null,
                    isVerified = true
                )
            )
        )
        val messageRepository = mock<MessageRepository>(mode = MockMode.autoUnit)
        everySuspend {
            messageRepository.getMessageById(eq(sourceConversationId), eq(quotedMessageId))
        } returns Either.Right(quotedMessage)

        val result = MessageSendingInterceptorImpl(
            messageContentEncoder = MessageContentEncoder(),
            messageRepository = messageRepository
        ).prepareMessage(message)

        verifySuspend {
            messageRepository.getMessageById(eq(sourceConversationId), eq(quotedMessageId))
        }
        val preparedMessage = (result as Either.Right).value as Message.Regular
        val preparedQuote = (preparedMessage.content as MessageContent.Text).quotedMessageReference
        val expectedQuoteHash = MessageContentEncoder()
            .encodeMessageContent(quotedMessage.date, quotedMessage.content)
            ?.sha256Digest
        assertContentEquals(expectedQuoteHash, preparedQuote?.quotedMessageSha256)
    }
}
