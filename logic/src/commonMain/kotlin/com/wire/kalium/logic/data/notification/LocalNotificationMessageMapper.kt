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

package com.wire.kalium.logic.data.notification

import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationDetails
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.user.OtherUser
import com.wire.kalium.logic.data.user.User
import kotlinx.datetime.toInstant

interface LocalNotificationMessageMapper {
    fun fromPublicUserToLocalNotificationMessageAuthor(author: OtherUser?): LocalNotificationMessageAuthor
    fun fromConnectionToLocalNotificationConversation(connection: ConversationDetails.Connection): LocalNotificationConversation
    fun fromConversationEventToLocalNotification(
        conversationEvent: Event.Conversation,
        conversation: Conversation,
        author: User?
    ): LocalNotificationConversation
}

class LocalNotificationMessageMapperImpl : LocalNotificationMessageMapper {

    override fun fromPublicUserToLocalNotificationMessageAuthor(author: OtherUser?) =
        LocalNotificationMessageAuthor(author?.name ?: "", null)

    override fun fromConnectionToLocalNotificationConversation(connection: ConversationDetails.Connection): LocalNotificationConversation {
        val author = fromPublicUserToLocalNotificationMessageAuthor(connection.otherUser)
        val message = LocalNotificationMessage.ConnectionRequest(
            author,
            // TODO: change time to Instant
            connection.lastModifiedDate.toInstant(),
            connection.connection.qualifiedToId
        )
        return LocalNotificationConversation(
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
    ): LocalNotificationConversation {
        return when (conversationEvent) {
            is Event.Conversation.DeletedConversation -> {
                val notificationMessage = LocalNotificationMessage.ConversationDeleted(
                    author = LocalNotificationMessageAuthor(author?.name ?: "", null),
                    // TODO: change time to Instant
                    time = conversationEvent.timestampIso.toInstant()
                )
                LocalNotificationConversation(
                    id = conversation.id,
                    conversationName = conversation.name ?: "",
                    messages = listOf(notificationMessage),
                    isOneToOneConversation = false
                )
            }

            else -> throw IllegalArgumentException("This event is not supported yet as a onetime notification")
        }

    }

}
