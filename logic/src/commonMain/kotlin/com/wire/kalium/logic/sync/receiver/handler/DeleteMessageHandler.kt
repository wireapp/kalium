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

import com.wire.kalium.persistence.dao.message.MessageDAO
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.notification.NotificationEventsManager
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.common.logger.kaliumLogger

internal interface DeleteMessageHandler {
    suspend operator fun invoke(
        content: MessageContent.DeleteMessage,
        conversationId: ConversationId,
        senderUserId: UserId
    )
}

internal class DeleteMessageHandlerImpl internal constructor(
    private val messageRepository: MessageRepository,
    private val messageDAO: MessageDAO,
    private val assetRepository: AssetRepository,
    private val notificationEventsManager: NotificationEventsManager,
    private val selfUserId: UserId,
) : DeleteMessageHandler {
    override suspend fun invoke(
        content: MessageContent.DeleteMessage,
        conversationId: ConversationId,
        senderUserId: UserId
    ) {
        messageRepository.getMessageById(conversationId, content.messageId).onSuccess { messageToRemove ->
            val deletion = messageToRemove.receivedDeletion(senderUserId, selfUserId)
            messageDAO.applyReceivedDeletion(messageToRemove.id, conversationId.toDao(), deletion).onSuccess { notify ->
                if (notify) notificationEventsManager.scheduleDeleteMessageNotification(messageToRemove)
                if (deletion != ReceivedMessageDeletion.NONE) {
                    (messageToRemove.content as? MessageContent.Asset)?.value?.remoteData?.let { asset ->
                        assetRepository.deleteAssetLocally(asset.assetId).onFailure {
                            kaliumLogger.w("Deleted message asset cleanup failed: $it")
                        }
                    }
                }
            }
        }
    }

}
