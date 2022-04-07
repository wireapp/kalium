package com.wire.kalium.logic.data.notification

import com.wire.kalium.logic.data.id.ConversationId

data class DbNotificationConversation(
    val id: ConversationId,
    val name: String,
    val messages: List<DbNotificationMessage>,
    val isOneToOneConversation: Boolean
)

sealed class DbNotificationMessage(open val author: DbNotificationMessageAuthor, open val time: String) {
    data class Text(override val author: DbNotificationMessageAuthor, override val time: String, val text: String) :
        DbNotificationMessage(author, time)

    //shared file, picture, reaction
    data class Comment(override val author: DbNotificationMessageAuthor, override val time: String, val type: DbNotificationCommentType) :
        DbNotificationMessage(author, time)
}

data class DbNotificationMessageAuthor(val name: String, val imageUri: String?)

enum class DbNotificationCommentType {
    PICTURE, FILE, REACTION
}
