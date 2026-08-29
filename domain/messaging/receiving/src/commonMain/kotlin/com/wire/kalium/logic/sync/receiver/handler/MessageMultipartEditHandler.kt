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
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageEditPersistence
import com.wire.kalium.logic.data.message.MessageEditState
import com.wire.kalium.logic.data.notification.NotificationEventsManager

public interface MessageMultipartEditHandler {
    public suspend fun handle(
        message: Message.Signaling,
        messageContent: MessageContent.MultipartEdited,
    ): Either<CoreFailure, Unit>
}

public class MessageMultipartEditHandlerImpl public constructor(
    private val messageEditPersistence: MessageEditPersistence,
    private val notificationEventsManager: NotificationEventsManager,
) : MessageMultipartEditHandler {

    override suspend fun handle(
        message: Message.Signaling,
        messageContent: MessageContent.MultipartEdited,
    ) = messageEditPersistence.loadMessageEditState(message.conversationId, messageContent.editMessageId).flatMap { currentMessage ->

        if (currentMessage.senderUserId != message.senderUserId) {
            val obfuscatedId = message.senderUserId.toLogString()
            kaliumLogger.w(
                message = "User '$obfuscatedId' attempted to edit a message from another user. Ignoring the edit completely"
            )
            // Same as message not found. _i.e._ not found for the original sender at least
            return@flatMap Either.Left(StorageFailure.DataNotFound)
        }

        val content = currentMessage.content
        if (content is MessageEditState.Content.Multipart && content.lastEditInstant != null) {
            // if the locally stored message is also already edited, we check which one is newer
            if (content.lastEditInstant > message.date) {
                // our local pending or failed edit is newer than one we got from the backend so we update locally only message id and date
                messageEditPersistence.applyMultipartEdit(
                    conversationId = message.conversationId,
                    messageContent = messageContent.copy(
                        newTextContent = content.value,
                        newMentions = content.mentions,
                        newAttachments = content.attachments,
                    ),
                    newMessageId = message.id,
                    editInstant = content.lastEditInstant,
                )
            } else {
                notificationEventsManager.scheduleEditMessageNotification(message, messageContent)
                // incoming edit from the backend is newer than the one we have locally so we update the whole message and change the status
                messageEditPersistence.applyMultipartEdit(
                    conversationId = message.conversationId,
                    messageContent = messageContent,
                    newMessageId = message.id,
                    editInstant = message.date,
                ).flatMap {
                    messageEditPersistence.markMessageAsSent(
                        conversationId = message.conversationId,
                        messageId = message.id,
                    )
                }
            }
        } else {
            notificationEventsManager.scheduleEditMessageNotification(message, messageContent)

            messageEditPersistence.applyMultipartEdit(
                conversationId = message.conversationId,
                messageContent = messageContent,
                newMessageId = message.id,
                editInstant = message.date,
            )
        }
    }
}
