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

import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.user.UserAssetId
import kotlinx.datetime.Instant

/**
 * Kalium local data classes that contains all the necessary data for displaying Message Notifications,
 * and suppose to be mapped (in platform side) into platform-specific objects to show the notification
 */
sealed class LocalNotification(open val conversationId: ConversationId) {
    data class Conversation(
        val id: ConversationId,
        val conversationName: String,
        val messages: List<LocalNotificationMessage>,
        val isOneToOneConversation: Boolean
    ) : LocalNotification(id)

    data class UpdateMessage(
        override val conversationId: ConversationId,
        val messageId: String,
        val action: LocalNotificationUpdateMessageAction
    ) : LocalNotification(conversationId)

    data class ConversationSeen(override val conversationId: ConversationId) : LocalNotification(conversationId)
}

sealed class LocalNotificationUpdateMessageAction {
    data object Delete : LocalNotificationUpdateMessageAction()
    data class Edit(val updateText: String, val newMessageId: String) : LocalNotificationUpdateMessageAction()
}

sealed class LocalNotificationMessage(
    open val messageId: String,
    open val author: LocalNotificationMessageAuthor?,
    open val time: Instant
) {
    data class SelfDeleteMessage(
        override val messageId: String,
        override val time: Instant
    ) : LocalNotificationMessage(messageId, null, time)

    data class SelfDeleteKnock(
        override val messageId: String,
        override val time: Instant
    ) : LocalNotificationMessage(messageId, null, time)

    data class Text(
        override val messageId: String,
        override val author: LocalNotificationMessageAuthor,
        override val time: Instant,
        val text: String,
        val isQuotingSelfUser: Boolean = false
    ) :
        LocalNotificationMessage(messageId, author, time)

    // shared file, picture, reaction
    data class Comment(
        override val messageId: String,
        override val author: LocalNotificationMessageAuthor,
        override val time: Instant,
        val type: LocalNotificationCommentType
    ) : LocalNotificationMessage(messageId, author, time)

    data class Knock(
        override val messageId: String,
        override val author: LocalNotificationMessageAuthor,
        override val time: Instant
    ) : LocalNotificationMessage(messageId, author, time)

    data class ConnectionRequest(
        override val messageId: String,
        override val author: LocalNotificationMessageAuthor,
        override val time: Instant,
        val authorId: QualifiedID
    ) : LocalNotificationMessage(messageId, author, time)

    data class ConversationDeleted(
        override val messageId: String,
        override val author: LocalNotificationMessageAuthor,
        override val time: Instant
    ) : LocalNotificationMessage(messageId, author, time)

}

data class LocalNotificationMessageAuthor(val name: String, val imageUri: UserAssetId?)

enum class LocalNotificationCommentType {
    PICTURE, FILE, REACTION, MISSED_CALL, LOCATION, NOT_SUPPORTED_YET
}
