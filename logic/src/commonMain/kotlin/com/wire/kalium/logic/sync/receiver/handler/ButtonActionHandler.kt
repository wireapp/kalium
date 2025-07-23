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

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.MessageButtonId
import com.wire.kalium.logic.data.id.MessageId
import com.wire.kalium.logic.data.message.CompositeMessageRepository
import com.wire.kalium.logic.data.message.MessageMetadataRepository
import com.wire.kalium.logic.data.user.UserId
import io.mockative.Mockable

@Mockable
internal interface ButtonActionHandler {
    suspend fun handle(
        conversationId: ConversationId,
        senderId: UserId,
        messageId: MessageId,
        buttonId: MessageButtonId,
    ): Either<CoreFailure, Unit>
}

internal class ButtonActionHandlerImpl internal constructor(
    private val selfUserId: UserId,
    private val compositeMessageRepository: CompositeMessageRepository,
    private val messageMetadataRepository: MessageMetadataRepository
) : ButtonActionHandler {

    override suspend fun handle(
        conversationId: ConversationId,
        senderId: UserId,
        messageId: MessageId,
        buttonId: MessageButtonId
    ): Either<CoreFailure, Unit> {
        if (senderId != selfUserId) {
            return Either.Left(CoreFailure.InvalidEventSenderID)
        }

        return messageMetadataRepository.originalSenderId(conversationId, messageId)
            .flatMap {
                compositeMessageRepository.markSelected(
                    messageId = messageId,
                    conversationId = conversationId,
                    buttonId = buttonId
                )
            }
    }
}
