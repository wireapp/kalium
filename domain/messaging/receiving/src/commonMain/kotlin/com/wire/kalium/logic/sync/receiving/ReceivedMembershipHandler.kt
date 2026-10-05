/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.wire.kalium.logic.sync.receiving

import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRoleMapperImpl
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.event.MemberLeaveReason
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.toEntity
import com.wire.kalium.logic.data.message.matchesSystemMessage
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserProfiles
import com.wire.kalium.logic.sync.receiver.conversation.isPromotionToAdmin
import com.wire.kalium.logic.sync.receiver.conversation.memberPromotedMessage
import com.wire.kalium.logic.sync.receiver.conversation.memberAddedMessage
import com.wire.kalium.logic.sync.receiver.conversation.memberRemovalContent
import com.wire.kalium.logic.sync.receiver.conversation.memberRemovedMessage
import com.wire.kalium.logic.sync.receiver.conversation.membershipMessage
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import com.wire.kalium.persistence.dao.member.MemberEntity
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.persistence.dao.message.MessageEntityContent
import com.wire.kalium.persistence.db.MessageEffectDAOs

/** Canonical membership writes and system messages share the notification's SQLite transaction. */
internal class ReceivedMembershipHandler(
    private val data: MessageEffectDAOs,
    private val persistMessage: PersistMessageUseCase,
    private val selfUserId: UserId,
    private val drivePermissionsEnabled: Boolean,
) {
    suspend fun previousPromotion(event: Event.Conversation.MemberChanged.MemberChangedRole): MessageEntity? =
        data.messages.getMessageById(event.id, event.conversationId.toDao())?.also { existing ->
            check(event.member?.role == Conversation.Member.Role.Admin) { "Promotion conflicts with the incoming role" }
            val expected = checkNotNull(event.memberPromotedMessage(selfUserId)).toEntity()
            check(existing.matchesSystemMessage(expected.senderUserId, expected.content)) { "Promotion message identity conflicts" }
        }

    suspend fun changeRole(received: ReceivedPayload.MemberRole, previous: MessageEntity?) {
        val event = received.event
        val member = checkNotNull(event.member)
        val conversationId = event.conversationId.toDao()
        // The app reads the role before its conditional metadata fetch persists members.
        val oldRole = data.members.getMemberRole(member.id.toDao(), conversationId)
        received.fetched?.persist(data, selfUserId, persistMessage)
        data.members.updateMemberRole(
            userId = member.id.toDao(),
            conversationID = conversationId,
            newRole = ConversationRoleMapperImpl().toDAO(member.role),
        )
        if (previous != null || isPromotionToAdmin(oldRole?.let(ConversationRoleMapperImpl()::fromDAO), member.role)) {
            persistMessage(checkNotNull(event.memberPromotedMessage(selfUserId))).orThrow()
        }
    }

    @Suppress("CyclomaticComplexMethod")
    suspend fun join(event: Event.Conversation.MemberJoin, profiles: UserProfiles, later: List<Event.Conversation.MemberLeave>) {
        val conversationId = event.conversationId.toDao()
        val conversation = checkNotNull(data.conversations.getConversationById(conversationId)) {
            "Conversation must already exist"
        }
        check(!conversation.hasIncompleteMetadata) { "Conversation requires synchronization" }
        validateCanonicalJoin(event)
        // A preceding payload can remove these members again during retry. Repeat the
        // required writes even when this join's canonical system message is already stored.
        check(profiles.users.none { it.deleted }) { "A deleted user cannot join a conversation" }
        profiles.persist(data.users, data.apps)
        val roleMapper = ConversationRoleMapperImpl()
        val members = event.members.filterNot { member ->
            // A one-to-one join has no member-added message of its own. A canonical later
            // leave still guards replay when this notification's final state is already stored.
            later.any { leave ->
                leave.conversationId == event.conversationId && member.id in leave.removedList &&
                    data.messages.getMessageById(leave.systemMessageId, conversationId) != null
            }
        }
        data.members.insertMembersWithQualifiedId(
            members.map { MemberEntity(it.id.toDao(), roleMapper.toDAO(it.role)) },
            conversationId
        )
        if (members.any { it.id == selfUserId }) data.conversations.setConversationDeletedLocally(conversationId, false)
        if (conversation.type == ConversationEntity.Type.ONE_ON_ONE && event.members.size == 1) {
            data.users.updateActiveOneOnOneConversationIfNotSet(event.members.single().id.toDao(), conversationId)
        }
        if (conversation.type.isGroup) persistMessage(event.memberAddedMessage()).orThrow()
        if ((conversation.type.isGroup || conversation.type == ConversationEntity.Type.ONE_ON_ONE) &&
            event.members.any { it.id == selfUserId }
        ) {
            if (conversation.type.isGroup && drivePermissionsEnabled && conversation.wireCell != null) {
                val selfTeam = data.users.getUserDetailsByQualifiedID(selfUserId.toDao())?.team
                persistMessage(
                    membershipMessage(
                        "${event.systemMessageId}:cell-access",
                        event.conversationId,
                        selfUserId,
                        event.dateTime,
                        if (conversation.teamId != selfTeam)
                            MessageContent.CellViewerAccessMessage
                        else MessageContent.CellEditorAccessMessage
                    )
                ).orThrow()
            }
            persistMessage(
                membershipMessage(
                    "${event.systemMessageId}:unverified",
                    event.conversationId,
                    selfUserId,
                    event.dateTime,
                    MessageContent.ConversationStartedUnverifiedWarning
                )
            ).orThrow()
        }
        data.requireUnchangedLegalHold(conversationId, selfUserId.toDao())
    }

    @Suppress("ReturnCount")
    private suspend fun validateCanonicalJoin(event: Event.Conversation.MemberJoin) {
        val conversationId = event.conversationId.toDao()
        data.messages.getMessageById(event.systemMessageId, conversationId)?.let { existing ->
            check(
                existing.matchesSystemMessage(
                    event.addedBy.toDao(),
                    MessageEntityContent.MemberChange(event.members.map { it.id.toDao() }, MessageEntity.MemberChangeType.ADDED)
                )
            ) {
                "Member join identity conflicts with stored state"
            }
            return
        }
        data.messages.getMessageById("${event.systemMessageId}:unverified", conversationId)?.let { existing ->
            check(
                existing.matchesSystemMessage(selfUserId.toDao(), MessageEntityContent.ConversationStartedUnverifiedWarning)
            ) {
                "Self join identity conflicts with stored state"
            }
            return
        }
    }

    suspend fun leave(event: Event.Conversation.MemberLeave, profiles: UserProfiles) {
        val conversationId = event.conversationId.toDao()
        val conversation = checkNotNull(data.conversations.getConversationById(conversationId)) {
            "Conversation must already exist"
        }
        check(!conversation.hasIncompleteMetadata) { "Conversation requires synchronization" }
        val current = data.messages.getMessageById(event.systemMessageId, conversationId)
        current?.let { existing ->
            val removed = MessageEntityContent.MemberChange(
                event.removedList.map { it.toDao() },
                MessageEntity.MemberChangeType.REMOVED
            )
            val removedFromTeam = MessageEntityContent.MemberChange(
                event.removedList.map { it.toDao() },
                MessageEntity.MemberChangeType.REMOVED_FROM_TEAM
            )
            check(
                existing.matchesSystemMessage(event.removedBy.toDao(), removed) ||
                    event.reason == MemberLeaveReason.UserDeleted && existing.matchesSystemMessage(event.removedBy.toDao(), removedFromTeam)
            ) {
                "Member leave identity conflicts with stored state"
            }
        }
        if (event.reason == MemberLeaveReason.UserDeleted) data.users.markAsDeleted(event.removedList.map { it.toDao() })
        val deleted = data.members.deleteMembersByQualifiedID(event.removedList.map { it.toDao() }, conversationId)
        // App leave handling counts removed rows before fetching profiles, whose deleted flag
        // can itself remove memberships. Preserve that ordering for the required system message.
        profiles.persist(data.users, data.apps)
        if (selfUserId in event.removedList) {
            (conversation.protocolInfo as? ConversationEntity.ProtocolInfo.MLSCapable)?.let {
                data.conversations.clearProposalTimer(it.groupId)
            }
            data.meetings.deleteMeetingsByConversationId(conversationId)
        }
        if (current == null && deleted > 0) {
            val selfTeam = data.users.getUserDetailsByQualifiedID(selfUserId.toDao())?.team
            val fromTeam = event.reason == MemberLeaveReason.UserDeleted && selfTeam != null &&
                data.users.isAtLeastOneUserATeamMember(event.removedList.map { it.toDao() }, selfTeam)
            persistMessage(event.memberRemovedMessage(memberRemovalContent(event.reason, event.removedList, fromTeam))).orThrow()
        }
        if (deleted > 0) data.requireUnchangedLegalHold(conversationId, selfUserId.toDao())
    }
}
