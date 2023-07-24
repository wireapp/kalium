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
package com.wire.kalium.logic.sync.receiver.handler

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.CompositeMessageRepository
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.functional.Either

internal interface ButtonActionConfirmationHandler {
    suspend fun handle(
        conversationId: ConversationId,
        messageContent: MessageContent.ButtonActionConfirmation
    ): Either<StorageFailure, Unit>
}

internal class ButtonActionConfirmationHandlerImpl internal constructor(
    private val compositeMessageRepository: CompositeMessageRepository
) : ButtonActionConfirmationHandler {

    override suspend fun handle(
        conversationId: ConversationId,
        messageContent: MessageContent.ButtonActionConfirmation
    ): Either<StorageFailure, Unit> {
        val messageId = messageContent.referencedMessageId

        return if (messageContent.buttonId != null) {
            compositeMessageRepository.markSelected(
                messageId = messageId,
                conversationId = conversationId,
                buttonId = messageContent.buttonId
            )
        } else {
            compositeMessageRepository.resetSelection(
                messageId = messageId,
                conversationId = conversationId
            )
        }
    }
}
