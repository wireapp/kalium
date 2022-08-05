package com.wire.kalium.logic.data.notification

import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationDetails
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.user.OtherUser
import com.wire.kalium.logic.data.user.User

interface LocalNotificationMessageMapper {
    fun fromPublicUserToLocalNotificationMessageAuthor(author: OtherUser?): LocalNotificationMessageAuthor
    fun fromConnectionToLocalNotificationConversation(connection: ConversationDetails.Connection): LocalNotificationConversation
    fun fromDeletedConversationToLocalNotification(
        deletedConversationEvent: Event.Conversation.DeletedConversation,
        conversation: Conversation,
        author: User?
    ): LocalNotificationConversation
}

class LocalNotificationMessageMapperImpl : LocalNotificationMessageMapper {

    override fun fromPublicUserToLocalNotificationMessageAuthor(author: OtherUser?) =
        LocalNotificationMessageAuthor(author?.name ?: "", null)

    override fun fromConnectionToLocalNotificationConversation(connection: ConversationDetails.Connection): LocalNotificationConversation {
        val author = fromPublicUserToLocalNotificationMessageAuthor(connection.otherUser)
        val message = LocalNotificationMessage.ConnectionRequest(author, connection.lastModifiedDate, connection.connection.qualifiedToId)
        return LocalNotificationConversation(
            connection.conversationId,
            connection.conversation.name ?: "",
            listOf(message),
            true
        )
    }

    override fun fromDeletedConversationToLocalNotification(
        deletedConversationEvent: Event.Conversation.DeletedConversation,
        conversation: Conversation,
        author: User?
    ): LocalNotificationConversation {
        val notificationMessage = LocalNotificationMessage.ConversationDeleted(
            author = LocalNotificationMessageAuthor(author?.name ?: "", null),
            time = deletedConversationEvent.timestampIso
        )

        return LocalNotificationConversation(
            id = conversation.id,
            conversationName = conversation.name ?: "",
            messages = listOf(notificationMessage),
            isOneToOneConversation = false
        )
    }

}
