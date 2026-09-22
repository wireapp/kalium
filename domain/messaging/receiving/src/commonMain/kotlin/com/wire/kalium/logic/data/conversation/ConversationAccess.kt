/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.logic.data.conversation

import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.network.api.model.ConversationAccessDTO
import com.wire.kalium.network.api.model.ConversationAccessRoleDTO
import com.wire.kalium.persistence.dao.conversation.ConversationDAO
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import kotlin.time.Clock
import kotlin.time.Instant

/** Returns the conversation as it was immediately before the update. */
suspend fun updateConversationAccess(
    conversations: ConversationDAO,
    conversationId: ConversationId,
    access: List<ConversationEntity.Access>,
    roles: List<ConversationEntity.AccessRole>,
): ConversationEntity? {
    val previous = conversations.getConversationById(conversationId.toDao())
    conversations.updateAccess(
        conversationID = conversationId.toDao(),
        accessList = access,
        accessRoleList = roles,
    )
    return previous
}

fun appsAccessChangedMessage(
    id: String,
    conversationId: ConversationId,
    senderUserId: UserId,
    enabled: Boolean,
    date: Instant = Clock.System.now(),
) = Message.System(
    id = id,
    content = MessageContent.ConversationAppsEnabledChanged(enabled),
    conversationId = conversationId,
    date = date,
    senderUserId = senderUserId,
    status = Message.Status.Sent,
    visibility = Message.Visibility.VISIBLE,
    expirationData = null,
)

fun Conversation.AccessRole.toDAO(): ConversationEntity.AccessRole = when (this) {
    Conversation.AccessRole.TEAM_MEMBER -> ConversationEntity.AccessRole.TEAM_MEMBER
    Conversation.AccessRole.NON_TEAM_MEMBER -> ConversationEntity.AccessRole.NON_TEAM_MEMBER
    Conversation.AccessRole.GUEST -> ConversationEntity.AccessRole.GUEST
    Conversation.AccessRole.SERVICE -> ConversationEntity.AccessRole.SERVICE
    Conversation.AccessRole.EXTERNAL -> ConversationEntity.AccessRole.EXTERNAL
}

fun Conversation.Access.toDAO(): ConversationEntity.Access = when (this) {
    Conversation.Access.PRIVATE -> ConversationEntity.Access.PRIVATE
    Conversation.Access.INVITE -> ConversationEntity.Access.INVITE
    Conversation.Access.SELF_INVITE -> ConversationEntity.Access.SELF_INVITE
    Conversation.Access.LINK -> ConversationEntity.Access.LINK
    Conversation.Access.CODE -> ConversationEntity.Access.CODE
}

fun ConversationAccessDTO.toModel(): Conversation.Access = when (this) {
    ConversationAccessDTO.PRIVATE -> Conversation.Access.PRIVATE
    ConversationAccessDTO.CODE -> Conversation.Access.CODE
    ConversationAccessDTO.INVITE -> Conversation.Access.INVITE
    ConversationAccessDTO.SELF_INVITE -> Conversation.Access.SELF_INVITE
    ConversationAccessDTO.LINK -> Conversation.Access.LINK
}

fun ConversationAccessRoleDTO.toModel(): Conversation.AccessRole = when (this) {
    ConversationAccessRoleDTO.TEAM_MEMBER -> Conversation.AccessRole.TEAM_MEMBER
    ConversationAccessRoleDTO.NON_TEAM_MEMBER -> Conversation.AccessRole.NON_TEAM_MEMBER
    ConversationAccessRoleDTO.GUEST -> Conversation.AccessRole.GUEST
    ConversationAccessRoleDTO.SERVICE -> Conversation.AccessRole.SERVICE
    ConversationAccessRoleDTO.EXTERNAL -> Conversation.AccessRole.EXTERNAL
}
