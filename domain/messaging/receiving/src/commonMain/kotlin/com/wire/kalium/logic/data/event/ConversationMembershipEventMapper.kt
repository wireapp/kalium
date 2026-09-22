/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.wire.kalium.logic.data.event

import com.wire.kalium.logic.data.conversation.ConversationRoleMapperImpl
import com.wire.kalium.logic.data.conversation.MemberMapperImpl
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.network.api.authenticated.notification.EventContentDTO
import com.wire.kalium.network.api.authenticated.notification.MemberLeaveReasonDTO

fun conversationMemberJoin(
        id: String,
        eventContentDTO: EventContentDTO.Conversation.MemberJoinDTO,
        payloadIndex: Int = 0,
    ) = Event.Conversation.MemberJoin(
        id = id,
        systemMessageId = metadataSystemMessageId(id, payloadIndex),
        conversationId = eventContentDTO.qualifiedConversation.toModel(),
        addedBy = eventContentDTO.qualifiedFrom.toModel(),
        members = eventContentDTO.members.users.map {
            MemberMapperImpl(IdMapper(), ConversationRoleMapperImpl()).fromApiModel(it)
        },
        dateTime = eventContentDTO.time,
    )

fun conversationMemberLeave(
        id: String,
        eventContentDTO: EventContentDTO.Conversation.MemberLeaveDTO,
        payloadIndex: Int = 0,
    ) = Event.Conversation.MemberLeave(
        id = id,
        systemMessageId = metadataSystemMessageId(id, payloadIndex),
        conversationId = eventContentDTO.qualifiedConversation.toModel(),
        removedBy = eventContentDTO.qualifiedFrom.toModel(),
        removedList = eventContentDTO.removedUsers.qualifiedUserIds.map { it.toModel() },
        dateTime = eventContentDTO.time,
        reason = eventContentDTO.removedUsers.reason.toModel()
    )

private fun MemberLeaveReasonDTO.toModel(): MemberLeaveReason = when (this) {
    MemberLeaveReasonDTO.LEFT -> MemberLeaveReason.Left
    MemberLeaveReasonDTO.REMOVED -> MemberLeaveReason.Removed
    MemberLeaveReasonDTO.USER_DELETED -> MemberLeaveReason.UserDeleted
}
