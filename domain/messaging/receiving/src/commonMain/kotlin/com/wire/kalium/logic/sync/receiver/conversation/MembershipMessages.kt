/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.wire.kalium.logic.sync.receiver.conversation

import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.event.MemberLeaveReason
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.user.UserId
import kotlin.uuid.Uuid
import kotlin.time.Instant

fun Event.Conversation.MemberJoin.memberAddedMessage() = membershipMessage(
    systemMessageId.ifEmpty { Uuid.random().toString() },
    conversationId,
    addedBy,
    dateTime,
    MessageContent.MemberChange.Added(members.map { it.id }),
)

fun Event.Conversation.MemberLeave.memberRemovedMessage(content: MessageContent.System) =
    membershipMessage(systemMessageId, conversationId, removedBy, dateTime, content)

fun isPromotionToAdmin(previousRole: Conversation.Member.Role?, newRole: Conversation.Member.Role?): Boolean =
    previousRole != Conversation.Member.Role.Admin && newRole == Conversation.Member.Role.Admin

fun Event.Conversation.MemberChanged.MemberChangedRole.memberPromotedMessage(self: UserId): Message.System? = member?.let {
    membershipMessage(id, conversationId, self, dateTime, MessageContent.MemberChange.UserPromotedToAdmin(listOf(it.id)))
}

fun memberRemovalContent(reason: MemberLeaveReason, members: List<UserId>, removedFromSelfTeam: Boolean): MessageContent.System =
    if (reason == MemberLeaveReason.UserDeleted && removedFromSelfTeam) MessageContent.MemberChange.RemovedFromTeam(members)
    else MessageContent.MemberChange.Removed(members)

fun membershipMessage(id: String, conversationId: ConversationId, sender: UserId, date: Instant, content: MessageContent.System) =
    Message.System(id, content, conversationId, date, sender, Message.Status.Sent, Message.Visibility.VISIBLE, expirationData = null)
