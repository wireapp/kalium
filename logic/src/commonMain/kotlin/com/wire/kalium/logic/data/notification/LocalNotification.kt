package com.wire.kalium.logic.data.notification

import com.wire.kalium.logic.data.id.ConversationId

/**
 * Kalium local data classes that contains all the necessary data for displaying Message Notifications,
 * and suppose to be mapped (in platform side) into platform-specific objects to show the notification
 */
data class LocalNotificationConversation(
    val id: ConversationId,
    val conversationName: String,
    val messages: List<LocalNotificationMessage>,
    val isOneToOneConversation: Boolean
)

sealed class LocalNotificationMessage(open val author: LocalNotificationMessageAuthor, open val time: String) {
    data class Text(override val author: LocalNotificationMessageAuthor, override val time: String, val text: String) :
        LocalNotificationMessage(author, time)

    //shared file, picture, reaction
    data class Comment(override val author: LocalNotificationMessageAuthor, override val time: String, val type: LocalNotificationCommentType) :
        LocalNotificationMessage(author, time)
}

data class LocalNotificationMessageAuthor(val name: String, val imageUri: String?)

enum class LocalNotificationCommentType {
    PICTURE, FILE, REACTION, MISSED_CALL, NOT_SUPPORTED_YET
}
