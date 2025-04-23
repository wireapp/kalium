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

import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.data.message.IsMessageSentInSelfConversationUseCase
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.kaliumLogger
import io.mockative.Mockable

@Mockable
internal interface DeleteForMeHandler {
    suspend fun handle(
        message: Message.Signaling,
        messageContent: MessageContent.DeleteForMe
    )
}

internal class DeleteForMeHandlerImpl internal constructor(
    private val messageRepository: MessageRepository,
    private val isMessageSentInSelfConversation: IsMessageSentInSelfConversationUseCase
) : DeleteForMeHandler {

    override suspend fun handle(
        message: Message.Signaling,
        messageContent: MessageContent.DeleteForMe
    ) {
        if (isMessageSentInSelfConversation(message)) {
            messageRepository.deleteMessage(
                messageUuid = messageContent.messageId,
                conversationId = messageContent.conversationId
            )
        } else {
            kaliumLogger.withFeatureId(KaliumLogger.Companion.ApplicationFlow.EVENT_RECEIVER)
                .i(message = "Delete message sender is not verified: $messageContent")
        }
    }

}
