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

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.CompositeMessageRepository
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageMetadataRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap

internal interface ButtonActionConfirmationHandler {
    suspend fun handle(
        conversationId: ConversationId,
        senderId: UserId,
        messageContent: MessageContent.ButtonActionConfirmation
    ): Either<CoreFailure, Unit>
}

internal class ButtonActionConfirmationHandlerImpl internal constructor(
    private val compositeMessageRepository: CompositeMessageRepository,
    private val messageMetadataRepository: MessageMetadataRepository
) : ButtonActionConfirmationHandler {

    override suspend fun handle(
        conversationId: ConversationId,
        senderId: UserId,
        messageContent: MessageContent.ButtonActionConfirmation
    ): Either<CoreFailure, Unit> {
        val messageId = messageContent.referencedMessageId
        return messageMetadataRepository.originalSenderId(conversationId, messageId)
            .flatMap { originalSender ->
                if (originalSender != senderId) {
                    Either.Left(CoreFailure.InvalidEventSenderID)
                } else {
                    Either.Right(Unit)
                }
            }.flatMap {
                val buttonId = messageContent.buttonId
                if (buttonId != null) {
                    compositeMessageRepository.markSelected(
                        messageId = messageId,
                        conversationId = conversationId,
                        buttonId = buttonId
                    )
                } else {
                    compositeMessageRepository.resetSelection(
                        messageId = messageId,
                        conversationId = conversationId
                    )
                }
            }
    }
}
