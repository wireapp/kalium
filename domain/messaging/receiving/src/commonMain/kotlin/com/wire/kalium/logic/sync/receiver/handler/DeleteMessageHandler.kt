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

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.IncomingMessageDeletionPersistence
import com.wire.kalium.logic.data.message.MessageDeletionSnapshot
import com.wire.kalium.logic.data.notification.DeleteMessageNotificationScheduler
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.messaging.hooks.MessageDeleteEventData
import com.wire.kalium.messaging.hooks.PersistenceEventHookNotifier
import com.wire.kalium.util.InternalKaliumApi

@InternalKaliumApi
public interface DeleteMessageHandler {
    public suspend operator fun invoke(
        content: MessageContent.DeleteMessage,
        conversationId: ConversationId,
        senderUserId: UserId,
    )
}

@InternalKaliumApi
public fun interface DeleteMessageAssetCleanup {
    public suspend fun deleteAssetLocally(assetId: String): Either<CoreFailure, Unit>
}

@InternalKaliumApi
public class DeleteMessageHandlerImpl public constructor(
    private val messageDeletionPersistence: IncomingMessageDeletionPersistence,
    private val assetCleanup: DeleteMessageAssetCleanup,
    private val deleteMessageNotificationScheduler: DeleteMessageNotificationScheduler,
    private val selfUserId: UserId,
    private val persistenceEventHookNotifier: PersistenceEventHookNotifier,
) : DeleteMessageHandler {
    override suspend fun invoke(
        content: MessageContent.DeleteMessage,
        conversationId: ConversationId,
        senderUserId: UserId,
    ) {
        messageDeletionPersistence.loadMessageDeletionSnapshot(conversationId, content.messageId).onSuccess { messageToRemove ->
            val isSelfSender = messageToRemove.senderUserId == selfUserId
            if (isSelfSender && messageToRemove.isRegularEphemeral) {
                messageDeletionPersistence.deleteMessage(
                    messageUuid = messageToRemove.messageId,
                    conversationId = messageToRemove.conversationId,
                )
            } else if (isSenderVerified(messageToRemove, senderUserId)) {
                if (messageToRemove.isRegularEphemeral) {
                    messageDeletionPersistence.deleteMessage(
                        messageUuid = messageToRemove.messageId,
                        conversationId = messageToRemove.conversationId,
                    )
                } else {
                    deleteMessageNotificationScheduler.scheduleDeleteMessageNotification(
                        conversationId = messageToRemove.conversationId,
                        messageId = messageToRemove.messageId,
                    )
                    messageDeletionPersistence.markMessageAsDeleted(
                        messageUuid = messageToRemove.messageId,
                        conversationId = messageToRemove.conversationId,
                    )
                }
            }
            messageToRemove.remoteAssetId?.let { assetId ->
                assetCleanup.deleteAssetLocally(assetId)
            }
        }
        persistenceEventHookNotifier.onMessageDeleted(
            MessageDeleteEventData(conversationId, content.messageId),
            selfUserId,
        )
    }

    private fun isSenderVerified(
        message: MessageDeletionSnapshot,
        deleteMessageSenderId: UserId,
    ): Boolean = deleteMessageSenderId == message.senderUserId || deleteMessageSenderId == selfUserId
}
