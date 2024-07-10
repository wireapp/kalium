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

package com.wire.kalium.logic.data.notification

import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationDetails
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.user.OtherUser
import com.wire.kalium.logic.data.user.User
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import com.wire.kalium.persistence.dao.message.NotificationMessageEntity
import kotlinx.datetime.toInstant

interface LocalNotificationMessageMapper {
    fun fromPublicUserToLocalNotificationMessageAuthor(author: OtherUser?): LocalNotificationMessageAuthor
    fun fromConnectionToLocalNotificationConversation(connection: ConversationDetails.Connection): LocalNotification
    fun fromConversationEventToLocalNotification(
        conversationEvent: Event.Conversation,
        conversation: Conversation,
        author: User?
    ): LocalNotification

    fun fromMessageToMessageDeletedLocalNotification(message: Message): LocalNotification
    fun fromMessageToMessageEditedLocalNotification(message: Message, messageContent: MessageContent.TextEdited): LocalNotification
    fun fromEntitiesToLocalNotifications(
        list: List<NotificationMessageEntity>,
        messageSizePerConversation: Int,
        mapMessage: (NotificationMessageEntity) -> LocalNotificationMessage?
    ): List<LocalNotification.Conversation>
}

class LocalNotificationMessageMapperImpl : LocalNotificationMessageMapper {

    override fun fromPublicUserToLocalNotificationMessageAuthor(author: OtherUser?) =
        LocalNotificationMessageAuthor(author?.name ?: "", null)

    override fun fromConnectionToLocalNotificationConversation(connection: ConversationDetails.Connection): LocalNotification {
        val author = fromPublicUserToLocalNotificationMessageAuthor(connection.otherUser)
        val message = LocalNotificationMessage.ConnectionRequest(
            "",
            author,
            // TODO: change time to Instant
            connection.lastModifiedDate.toInstant(),
            connection.connection.qualifiedToId
        )
        return LocalNotification.Conversation(
            connection.conversationId,
            connection.conversation.name ?: "",
            listOf(message),
            true
        )
    }

    override fun fromConversationEventToLocalNotification(
        conversationEvent: Event.Conversation,
        conversation: Conversation,
        author: User?
    ): LocalNotification {
        return when (conversationEvent) {
            is Event.Conversation.DeletedConversation -> {
                val notificationMessage = LocalNotificationMessage.ConversationDeleted(
                    messageId = "",
                    author = LocalNotificationMessageAuthor(author?.name ?: "", null),
                    // TODO: change time to Instant
                    time = conversationEvent.timestampIso.toInstant()
                )
                LocalNotification.Conversation(
                    id = conversation.id,
                    conversationName = conversation.name ?: "",
                    messages = listOf(notificationMessage),
                    isOneToOneConversation = false
                )
            }

            else -> throw IllegalArgumentException("This event is not supported yet as a onetime notification")
        }
    }

    override fun fromMessageToMessageDeletedLocalNotification(message: Message): LocalNotification =
        LocalNotification.UpdateMessage(
            message.conversationId,
            message.id,
            LocalNotificationUpdateMessageAction.Delete
        )

    override fun fromMessageToMessageEditedLocalNotification(
        message: Message,
        messageContent: MessageContent.TextEdited
    ): LocalNotification =
        LocalNotification.UpdateMessage(
            message.conversationId,
            messageContent.editMessageId,
            LocalNotificationUpdateMessageAction.Edit(messageContent.newContent, message.id)
        )

    override fun fromEntitiesToLocalNotifications(
        list: List<NotificationMessageEntity>,
        messageSizePerConversation: Int,
        mapMessage: (NotificationMessageEntity) -> LocalNotificationMessage?
    ): List<LocalNotification.Conversation> =
        list.groupBy { it.conversationId }
            .map { (conversationId, messages) ->
                LocalNotification.Conversation(
                    // todo: needs some clean up!
                    id = conversationId.toModel(),
                    conversationName = messages.first().conversationName ?: "",
                    messages = messages.take(messageSizePerConversation).mapNotNull { mapMessage(it) },
                    isOneToOneConversation = messages.first().conversationType == ConversationEntity.Type.ONE_ON_ONE,
                    isReplyAllowed = messages.first().degradedConversationNotified
                )
            }
}
