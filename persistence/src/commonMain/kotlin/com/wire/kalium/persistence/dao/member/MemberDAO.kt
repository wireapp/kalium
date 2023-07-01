/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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
package com.wire.kalium.persistence.dao.member

import app.cash.sqldelight.coroutines.asFlow
import com.wire.kalium.persistence.ConversationsQueries
import com.wire.kalium.persistence.MembersQueries
import com.wire.kalium.persistence.UsersQueries
import com.wire.kalium.persistence.dao.ConnectionEntity
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import com.wire.kalium.persistence.util.mapToList
import com.wire.kalium.persistence.util.mapToOneOrNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

interface MemberDAO {
    suspend fun insertMember(member: MemberEntity, conversationID: QualifiedIDEntity)
    suspend fun updateMemberRole(userId: UserIDEntity, conversationID: QualifiedIDEntity, newRole: MemberEntity.Role)
    suspend fun insertMembersWithQualifiedId(memberList: List<MemberEntity>, conversationID: QualifiedIDEntity)
    suspend fun insertMembers(memberList: List<MemberEntity>, groupId: String)
    suspend fun deleteMemberByQualifiedID(userID: QualifiedIDEntity, conversationID: QualifiedIDEntity)
    suspend fun deleteMembersByQualifiedID(userIDList: List<QualifiedIDEntity>, conversationID: QualifiedIDEntity)
    suspend fun deleteMembersByQualifiedID(userIDList: List<QualifiedIDEntity>, groupId: String)
    suspend fun observeConversationMembers(qualifiedID: QualifiedIDEntity): Flow<List<MemberEntity>>
    suspend fun updateConversationMemberRole(conversationId: QualifiedIDEntity, userId: UserIDEntity, role: MemberEntity.Role)
    suspend fun updateOrInsertOneOnOneMemberWithConnectionStatus(
        member: MemberEntity,
        status: ConnectionEntity.State,
        conversationID: QualifiedIDEntity
    )

    suspend fun observeIsUserMember(conversationId: QualifiedIDEntity, userId: UserIDEntity): Flow<Boolean>
}

internal class MemberDAOImpl internal constructor(
    private val memberQueries: MembersQueries,
    private val userQueries: UsersQueries,
    private val conversationsQueries: ConversationsQueries,
    private val coroutineContext: CoroutineContext,
    private val memberMapper: MemberMapper = MemberMapper()
) : MemberDAO {

    override suspend fun insertMember(member: MemberEntity, conversationID: QualifiedIDEntity) = withContext(coroutineContext) {
        memberQueries.transaction {
            userQueries.insertOrIgnoreUserId(member.user)
            memberQueries.insertMember(member.user, conversationID, member.role)
        }
    }

    override suspend fun updateMemberRole(userId: UserIDEntity, conversationID: QualifiedIDEntity, newRole: MemberEntity.Role) =
        withContext(coroutineContext) {
            memberQueries.updateMemberRole(newRole, userId, conversationID)
        }

    override suspend fun insertMembersWithQualifiedId(memberList: List<MemberEntity>, conversationID: QualifiedIDEntity) =
        withContext(coroutineContext) {
            nonSuspendInsertMembersWithQualifiedId(memberList, conversationID)
        }

    private fun nonSuspendInsertMembersWithQualifiedId(memberList: List<MemberEntity>, conversationID: QualifiedIDEntity) =
        memberQueries.transaction {
            for (member: MemberEntity in memberList) {
                userQueries.insertOrIgnoreUserId(member.user)
                memberQueries.insertMember(member.user, conversationID, member.role)
            }
        }

    override suspend fun insertMembers(memberList: List<MemberEntity>, groupId: String) {
        withContext(coroutineContext) {
            conversationsQueries.selectByGroupId(groupId).executeAsOneOrNull()?.let {
                nonSuspendInsertMembersWithQualifiedId(memberList, it.qualifiedId)
            }
        }
    }

    override suspend fun deleteMemberByQualifiedID(userID: QualifiedIDEntity, conversationID: QualifiedIDEntity) =
        withContext(coroutineContext) {
            memberQueries.deleteMember(conversationID, userID)
        }

    override suspend fun deleteMembersByQualifiedID(userIDList: List<QualifiedIDEntity>, conversationID: QualifiedIDEntity) =
        withContext(coroutineContext) {
            nonSuspendDeleteMembersByQualifiedID(userIDList, conversationID)
        }

    private fun nonSuspendDeleteMembersByQualifiedID(userIDList: List<QualifiedIDEntity>, conversationID: QualifiedIDEntity) =
        memberQueries.transaction {
            userIDList.forEach {
                memberQueries.deleteMember(conversationID, it)
            }
        }

    override suspend fun deleteMembersByQualifiedID(userIDList: List<QualifiedIDEntity>, groupId: String) {
        withContext(coroutineContext) {
            conversationsQueries.selectByGroupId(groupId).executeAsOneOrNull()?.let {
                nonSuspendDeleteMembersByQualifiedID(userIDList, it.qualifiedId)
            }
        }
    }

    override suspend fun observeConversationMembers(qualifiedID: QualifiedIDEntity): Flow<List<MemberEntity>> {
        return memberQueries.selectAllMembersByConversation(qualifiedID.value)
            .asFlow()
            .flowOn(coroutineContext)
            .mapToList()
            .map { it.map(memberMapper::toModel) }
    }

    override suspend fun updateConversationMemberRole(conversationId: QualifiedIDEntity, userId: UserIDEntity, role: MemberEntity.Role) =
        withContext(coroutineContext) {
            memberQueries.updateMemberRole(role, userId, conversationId)
        }

    override suspend fun updateOrInsertOneOnOneMemberWithConnectionStatus(
        member: MemberEntity,
        status: ConnectionEntity.State,
        conversationID: QualifiedIDEntity
    ) = withContext(coroutineContext) {
        memberQueries.transaction {
            userQueries.updateUserConnectionStatus(status, member.user)
            val recordDidNotExist = userQueries.selectChanges().executeAsOne() == 0L
            if (recordDidNotExist) {
                userQueries.insertOrIgnoreUserIdWithConnectionStatus(member.user, status)
            }
            conversationsQueries.updateConversationType(ConversationEntity.Type.ONE_ON_ONE, conversationID)
            memberQueries.insertMember(member.user, conversationID, member.role)
        }
    }

    override suspend fun observeIsUserMember(conversationId: QualifiedIDEntity, userId: UserIDEntity): Flow<Boolean> =
        memberQueries.isUserMember(conversationId, userId)
            .asFlow()
            .flowOn(coroutineContext)
            .mapToOneOrNull()
            .map { it != null }
}
