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

package com.wire.kalium.logic.feature.message

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.util.MessageContentEncoder
import com.wire.kalium.persistence.dao.ConversationDAO

interface MessageSendingInterceptor {
    suspend fun prepareMessage(message: Message.Sendable): Either<CoreFailure, Message.Sendable>
}

class MessageSendingInterceptorImpl(
    private val messageContentEncoder: MessageContentEncoder,
    private val messageRepository: MessageRepository,
    private val conversationRepository: ConversationRepository
) : MessageSendingInterceptor {

    override suspend fun prepareMessage(message: Message.Sendable): Either<CoreFailure, Message.Sendable> {
        val replyMessageContent = message.content

        if (replyMessageContent !is MessageContent.Text
            || message !is Message.Regular
            || replyMessageContent.quotedMessageReference == null
        ) {
            return Either.Right(message)
        }

        val conversationEnforcedTimer = conversationRepository.getConversationById(message.conversationId)?.messageTimer

        return messageRepository.getMessageById(message.conversationId, replyMessageContent.quotedMessageReference.quotedMessageId)
            .map { originalMessage ->
                val encodedMessageContent = messageContentEncoder.encodeMessageContent(
                    messageDate = originalMessage.date,
                    messageContent = originalMessage.content
                )

                message.copy(
                    content = replyMessageContent.copy(
                        quotedMessageReference = replyMessageContent.quotedMessageReference.copy(
                            quotedMessageSha256 = encodedMessageContent?.sha256Digest
                        )
                    )
                )
            }
    }
}
