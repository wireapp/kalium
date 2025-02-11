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

package com.wire.kalium.logic.feature.message

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.map
import com.wire.kalium.logic.util.MessageContentEncoder

interface MessageSendingInterceptor {
    suspend fun prepareMessage(originalMessage: Message.Sendable): Either<CoreFailure, Message.Sendable>
}

internal class MessageSendingInterceptorImpl internal constructor(
    private val messageContentEncoder: MessageContentEncoder,
    private val messageRepository: MessageRepository
) : MessageSendingInterceptor {

    override suspend fun prepareMessage(originalMessage: Message.Sendable): Either<CoreFailure, Message.Sendable> {

        val replyMessageContent = originalMessage.content

        val quotedReference = (replyMessageContent as? MessageContent.Text)?.quotedMessageReference
        if (replyMessageContent !is MessageContent.Text
            || originalMessage !is Message.Regular
            || quotedReference == null
        ) {
            return Either.Right(originalMessage)
        }

        return messageRepository.getMessageById(originalMessage.conversationId, quotedReference.quotedMessageId)
            .map { persistedMessage ->
                val encodedMessageContent = messageContentEncoder.encodeMessageContent(
                    messageInstant = persistedMessage.date,
                    messageContent = persistedMessage.content
                )

                originalMessage.copy(
                    content = replyMessageContent.copy(
                        quotedMessageReference = quotedReference.copy(
                            quotedMessageSha256 = encodedMessageContent?.sha256Digest
                        )
                    )
                )
            }
    }
}
