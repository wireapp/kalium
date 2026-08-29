/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

package com.wire.kalium.logic.sync.receiver.conversation

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.error.wrapStorageNullableRequest
import com.wire.kalium.common.error.wrapStorageRequest
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.map
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.MutedConversationStatus
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.persistence.dao.conversation.ConversationDAO
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import com.wire.kalium.persistence.dao.member.MemberDAO
import com.wire.kalium.persistence.dao.member.MemberEntity
import com.wire.kalium.util.InternalKaliumApi
import kotlinx.datetime.Instant

/** Local persistence used only while applying incoming conversation lifecycle events. */
@InternalKaliumApi
public interface ConversationLifecycleEventRepository {
    public suspend fun updateConversationName(
        conversationId: ConversationId,
        conversationName: String,
        dateTime: Instant,
    ): Either<StorageFailure, Unit>

    public suspend fun updateConversationModifiedDate(
        conversationId: ConversationId,
        date: Instant,
    ): Either<StorageFailure, Unit>

    public suspend fun deleteConversationLocally(
        conversationId: ConversationId,
    ): Either<CoreFailure, Boolean>

    public suspend fun setConversationDeletedLocally(
        conversationId: ConversationId,
        deletedLocally: Boolean,
    ): Either<CoreFailure, Unit>

    public suspend fun persistMembers(
        members: List<Conversation.Member>,
        conversationId: ConversationId,
    ): Either<CoreFailure, Unit>

    public suspend fun deleteMembers(
        userIds: List<UserId>,
        conversationId: ConversationId,
    ): Either<CoreFailure, Long>

    public suspend fun getConversationMemberRole(
        conversationId: ConversationId,
        userId: UserId,
    ): Either<StorageFailure, Conversation.Member.Role?>

    public suspend fun updateMemberFromEvent(
        member: Conversation.Member,
        conversationId: ConversationId,
    ): Either<CoreFailure, Unit>

    public suspend fun updateMutedStatusLocally(
        conversationId: ConversationId,
        mutedStatus: MutedConversationStatus,
        mutedStatusTimestamp: Instant,
    ): Either<StorageFailure, Unit>

    public suspend fun updateArchivedStatusLocally(
        conversationId: ConversationId,
        isArchived: Boolean,
        archivedStatusTimestamp: Instant,
    ): Either<StorageFailure, Unit>
}

/** DAO-backed lifecycle persistence shared by continuous and bounded event processing. */
@InternalKaliumApi
public class ConversationLifecycleEventRepositoryImpl public constructor(
    private val conversationDAO: ConversationDAO,
    private val memberDAO: MemberDAO,
) : ConversationLifecycleEventRepository {

    override suspend fun updateConversationName(
        conversationId: ConversationId,
        conversationName: String,
        dateTime: Instant,
    ): Either<StorageFailure, Unit> = wrapStorageRequest {
        conversationDAO.updateConversationName(conversationId.toDao(), conversationName, dateTime)
    }

    override suspend fun updateConversationModifiedDate(
        conversationId: ConversationId,
        date: Instant,
    ): Either<StorageFailure, Unit> = wrapStorageRequest {
        conversationDAO.updateConversationModifiedDate(conversationId.toDao(), date)
    }

    override suspend fun deleteConversationLocally(
        conversationId: ConversationId,
    ): Either<CoreFailure, Boolean> = wrapStorageRequest {
        conversationDAO.deleteConversationByQualifiedID(conversationId.toDao())
    }

    override suspend fun setConversationDeletedLocally(
        conversationId: ConversationId,
        deletedLocally: Boolean,
    ): Either<CoreFailure, Unit> = wrapStorageRequest {
        conversationDAO.setConversationDeletedLocally(conversationId.toDao(), deletedLocally)
    }

    override suspend fun persistMembers(
        members: List<Conversation.Member>,
        conversationId: ConversationId,
    ): Either<CoreFailure, Unit> = wrapStorageRequest {
        memberDAO.insertMembersWithQualifiedId(
            members.map { MemberEntity(it.id.toDao(), it.role.toEntity()) },
            conversationId.toDao(),
        )
    }

    override suspend fun deleteMembers(
        userIds: List<UserId>,
        conversationId: ConversationId,
    ): Either<CoreFailure, Long> = wrapStorageRequest {
        memberDAO.deleteMembersByQualifiedID(
            userIds.map { it.toDao() },
            conversationId.toDao(),
        )
    }

    override suspend fun getConversationMemberRole(
        conversationId: ConversationId,
        userId: UserId,
    ): Either<StorageFailure, Conversation.Member.Role?> = wrapStorageNullableRequest {
        memberDAO.getMemberRole(userId.toDao(), conversationId.toDao())
    }.map { it?.toModel() }

    override suspend fun updateMemberFromEvent(
        member: Conversation.Member,
        conversationId: ConversationId,
    ): Either<CoreFailure, Unit> = wrapStorageRequest {
        memberDAO.updateMemberRole(member.id.toDao(), conversationId.toDao(), member.role.toEntity())
    }

    override suspend fun updateMutedStatusLocally(
        conversationId: ConversationId,
        mutedStatus: MutedConversationStatus,
        mutedStatusTimestamp: Instant,
    ): Either<StorageFailure, Unit> = wrapStorageRequest {
        conversationDAO.updateConversationMutedStatus(
            conversationId = conversationId.toDao(),
            mutedStatus = mutedStatus.toEntity(),
            mutedStatusTimestamp = mutedStatusTimestamp,
        )
    }

    override suspend fun updateArchivedStatusLocally(
        conversationId: ConversationId,
        isArchived: Boolean,
        archivedStatusTimestamp: Instant,
    ): Either<StorageFailure, Unit> = wrapStorageRequest {
        conversationDAO.updateConversationArchivedStatus(
            conversationId = conversationId.toDao(),
            isArchived = isArchived,
            archivedStatusTimestamp = archivedStatusTimestamp,
        )
    }

    private fun Conversation.Member.Role.toEntity(): MemberEntity.Role = when (this) {
        Conversation.Member.Role.Admin -> MemberEntity.Role.Admin
        Conversation.Member.Role.Member -> MemberEntity.Role.Member
        is Conversation.Member.Role.Unknown -> MemberEntity.Role.Unknown(name)
    }

    private fun MemberEntity.Role.toModel(): Conversation.Member.Role = when (this) {
        MemberEntity.Role.Admin -> Conversation.Member.Role.Admin
        MemberEntity.Role.Member -> Conversation.Member.Role.Member
        is MemberEntity.Role.Unknown -> Conversation.Member.Role.Unknown(name)
    }

    private fun MutedConversationStatus.toEntity(): ConversationEntity.MutedStatus = when (this) {
        MutedConversationStatus.AllAllowed -> ConversationEntity.MutedStatus.ALL_ALLOWED
        MutedConversationStatus.OnlyMentionsAndRepliesAllowed -> ConversationEntity.MutedStatus.ONLY_MENTIONS_AND_REPLIES_ALLOWED
        MutedConversationStatus.AllMuted -> ConversationEntity.MutedStatus.ALL_MUTED
    }
}
