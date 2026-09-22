/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.wire.kalium.logic.data.event

import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.data.conversation.MutedConversationStatus
import com.wire.kalium.network.api.authenticated.notification.EventContentDTO
import kotlin.time.Instant

fun conversationMemberUpdate(
    id: String,
    event: EventContentDTO.Conversation.MemberUpdateDTO,
    roleMapper: com.wire.kalium.logic.data.conversation.ConversationRoleMapper =
        com.wire.kalium.logic.data.conversation.ConversationRoleMapperImpl(),
): Event.Conversation.MemberChanged = if (event.roleChange.role?.isNotEmpty() == true) {
    Event.Conversation.MemberChanged.MemberChangedRole(
        id = id,
        conversationId = event.qualifiedConversation.toModel(),
        dateTime = Instant.parse(event.time),
        member = com.wire.kalium.logic.data.conversation.Conversation.Member(
            id = event.roleChange.qualifiedUserId.toModel(),
            role = roleMapper.fromApi(event.roleChange.role.orEmpty()),
        ),
    )
} else conversationPreferenceUpdate(id, event)

/** The app dispatches role changes before reaching these preference-only variants. */
fun conversationPreferenceUpdate(
    id: String,
    eventContentDTO: EventContentDTO.Conversation.MemberUpdateDTO
): Event.Conversation.MemberChanged {
    require(eventContentDTO.roleChange.role.isNullOrEmpty()) { "Membership role update requires processing" }
    return when {
        eventContentDTO.roleChange.mutedStatus != null -> {
            Event.Conversation.MemberChanged.MemberMutedStatusChanged(
                id = id,
                conversationId = eventContentDTO.qualifiedConversation.toModel(),
                mutedConversationChangedTime = eventContentDTO.roleChange.mutedRef
                    ?.let(Instant::parse)
                    ?: Instant.parse(eventContentDTO.time),
                mutedConversationStatus = mapConversationMutedStatus(eventContentDTO.roleChange.mutedStatus)
            )
        }

        eventContentDTO.roleChange.isArchiving != null -> {
            Event.Conversation.MemberChanged.MemberArchivedStatusChanged(
                id = id,
                conversationId = eventContentDTO.qualifiedConversation.toModel(),
                archivedConversationChangedTime = eventContentDTO.roleChange.archivedRef
                    ?.let(Instant::parse)
                    ?: Instant.parse(eventContentDTO.time),
                isArchiving = eventContentDTO.roleChange.isArchiving ?: false
            )
        }

        else -> {
            Event.Conversation.MemberChanged.IgnoredMemberChanged(
                id,
                eventContentDTO.qualifiedConversation.toModel(),
            )
        }
    }
}

@Suppress("MagicNumber")
private fun mapConversationMutedStatus(status: Int?): MutedConversationStatus = when (status) {
    0 -> MutedConversationStatus.AllAllowed
    1 -> MutedConversationStatus.OnlyMentionsAndRepliesAllowed
    3 -> MutedConversationStatus.AllMuted
    else -> MutedConversationStatus.AllAllowed
}
