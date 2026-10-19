/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.wire.kalium.logic.data.event

import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRoleMapper
import com.wire.kalium.logic.data.conversation.ConversationRoleMapperImpl
import com.wire.kalium.logic.data.conversation.MutedConversationStatus
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.network.api.authenticated.conversation.ConversationRoleChange
import com.wire.kalium.network.api.authenticated.notification.EventContentDTO
import kotlin.time.Instant

fun conversationMemberUpdate(
    id: String,
    event: EventContentDTO.Conversation.MemberUpdateDTO,
    roleMapper: ConversationRoleMapper = ConversationRoleMapperImpl(),
): Event.Conversation.MemberChanged = memberUpdate(id, event.qualifiedConversation.toModel(), event.time, event.roleChange, roleMapper)

fun conversationMemberUpdate(
    id: String,
    event: EventContentDTO.Conversation.SystemMemberUpdateDTO,
    roleMapper: ConversationRoleMapper = ConversationRoleMapperImpl(),
): Event.Conversation.MemberChanged = memberUpdate(id, event.qualifiedConversation.toModel(), event.time, event.roleChange, roleMapper)

private fun memberUpdate(
    id: String,
    conversationId: ConversationId,
    time: String,
    roleChange: ConversationRoleChange,
    roleMapper: ConversationRoleMapper,
): Event.Conversation.MemberChanged = when {
    roleChange.role?.isNotEmpty() == true -> Event.Conversation.MemberChanged.MemberChangedRole(
        id = id,
        conversationId = conversationId,
        dateTime = Instant.parse(time),
        member = Conversation.Member(roleChange.qualifiedUserId.toModel(), roleMapper.fromApi(roleChange.role.orEmpty())),
    )
    else -> preferenceUpdate(id, conversationId, time, roleChange)
}

/** The app dispatches role changes before reaching these preference-only variants. */
fun conversationPreferenceUpdate(
    id: String,
    event: EventContentDTO.Conversation.MemberUpdateDTO,
): Event.Conversation.MemberChanged {
    require(event.roleChange.role.isNullOrEmpty()) { "Membership role update requires processing" }
    return preferenceUpdate(id, event.qualifiedConversation.toModel(), event.time, event.roleChange)
}

private fun preferenceUpdate(
    id: String,
    conversationId: ConversationId,
    time: String,
    roleChange: ConversationRoleChange,
): Event.Conversation.MemberChanged = when {
    roleChange.mutedStatus != null -> Event.Conversation.MemberChanged.MemberMutedStatusChanged(
        id = id,
        conversationId = conversationId,
        mutedConversationChangedTime = roleChange.mutedRef?.let(Instant::parse) ?: Instant.parse(time),
        mutedConversationStatus = mapConversationMutedStatus(roleChange.mutedStatus),
    )
    roleChange.isArchiving != null -> Event.Conversation.MemberChanged.MemberArchivedStatusChanged(
        id = id,
        conversationId = conversationId,
        archivedConversationChangedTime = roleChange.archivedRef?.let(Instant::parse) ?: Instant.parse(time),
        isArchiving = roleChange.isArchiving ?: false,
    )
    else -> Event.Conversation.MemberChanged.IgnoredMemberChanged(id, conversationId)
}

@Suppress("MagicNumber")
private fun mapConversationMutedStatus(status: Int?): MutedConversationStatus = when (status) {
    0 -> MutedConversationStatus.AllAllowed
    1 -> MutedConversationStatus.OnlyMentionsAndRepliesAllowed
    3 -> MutedConversationStatus.AllMuted
    else -> MutedConversationStatus.AllAllowed
}
