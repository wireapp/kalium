package com.wire.kalium.logic.data.notification

import com.wire.kalium.logic.data.conversation.ConversationDetails
import com.wire.kalium.logic.data.user.OtherUser

interface LocalNotificationMessageMapper {
    fun fromPublicUserToLocalNotificationMessageAuthor(author: OtherUser?): LocalNotificationMessageAuthor
    fun fromConnectionToLocalNotificationConversation(connection: ConversationDetails.Connection): LocalNotificationConversation
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

}
