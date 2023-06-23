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

import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.onSuccess

internal interface DeleteMessageHandler {
    suspend operator fun invoke(
        content: MessageContent.DeleteMessage,
        conversationId: ConversationId,
        senderUserId: UserId
    )
}

internal class DeleteMessageHandlerImpl internal constructor(
    private val messageRepository: MessageRepository,
    private val assetRepository: AssetRepository
) : DeleteMessageHandler {
    override suspend fun invoke(
        content: MessageContent.DeleteMessage,
        conversationId: ConversationId,
        senderUserId: UserId
    ) {
        messageRepository.getMessageById(conversationId, content.messageId).onSuccess { messageToRemove ->
            val canBeDeleted = isSenderVerified(messageToRemove, senderUserId)
            if (!canBeDeleted) return

            val isOriginalEphemeral = (messageToRemove as? Message.Regular)?.expirationData != null
            if (isOriginalEphemeral) {
                messageRepository.deleteMessage(
                    messageUuid = messageToRemove.id,
                    conversationId = messageToRemove.conversationId
                )
            } else {
                messageRepository.markMessageAsDeleted(
                    messageUuid = messageToRemove.id,
                    conversationId = messageToRemove.conversationId
                )
            }
            removeAssetIfExists(messageToRemove)
        }
    }

    /**
     * checks if the sender of the delete message is the same as the sender of the message to be deleted
     */
    private fun isSenderVerified(
        message: Message,
        deleteMessageSenderId: UserId
    ): Boolean = deleteMessageSenderId == message.senderUserId

    private suspend fun removeAssetIfExists(messageToRemove: Message) {
        (messageToRemove.content as? MessageContent.Asset)?.value?.remoteData?.let { assetToRemove ->
            assetRepository.deleteAssetLocally(assetId = assetToRemove.assetId)
        }
    }
}
