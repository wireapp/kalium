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

package com.wire.kalium.logic.sync.receiver.message

import com.wire.kalium.logger.obfuscateId
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.kaliumLogger

interface MessageTextEditHandler {
    suspend fun handle(
        message: Message.Signaling,
        messageContent: MessageContent.TextEdited
    ): Either<CoreFailure, Unit>
}

class MessageTextEditHandlerImpl(
    private val messageRepository: MessageRepository
) : MessageTextEditHandler {

    override suspend fun handle(
        message: Message.Signaling,
        messageContent: MessageContent.TextEdited
    ) = messageRepository.getMessageById(message.conversationId, messageContent.editMessageId).flatMap { currentMessage ->

        if (currentMessage.senderUserId != message.senderUserId) {
            val obfuscatedId = message.senderUserId.toString().obfuscateId()
            kaliumLogger.w(
                message = "User '$obfuscatedId' attempted to edit a message from another user. Ignoring the edit completely"
            )
            // Same as message not found. _i.e._ not found for the original sender at least
            return@flatMap Either.Left(StorageFailure.DataNotFound)
        }

        messageRepository.updateTextMessage(
            conversationId = message.conversationId,
            messageContent = messageContent,
            newMessageId = message.id,
            editTimeStamp = message.date
        )
    }

}
