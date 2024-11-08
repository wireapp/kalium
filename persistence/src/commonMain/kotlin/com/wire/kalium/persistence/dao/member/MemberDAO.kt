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
package com.wire.kalium.persistence.dao.member

import app.cash.sqldelight.coroutines.asFlow
import com.wire.kalium.persistence.ConversationsQueries
import com.wire.kalium.persistence.MembersQueries
import com.wire.kalium.persistence.UsersQueries
import com.wire.kalium.persistence.cache.FlowCache
import com.wire.kalium.persistence.dao.ConversationIDEntity
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import com.wire.kalium.persistence.dao.conversation.NameAndHandleEntity
import com.wire.kalium.persistence.kaliumLogger
import com.wire.kalium.persistence.util.mapToList
import com.wire.kalium.persistence.util.mapToOneOrNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

@Suppress("TooManyFunctions")
interface MemberDAO {
    suspend fun insertMember(member: MemberEntity, conversationID: QualifiedIDEntity)
    suspend fun updateMemberRole(userId: UserIDEntity, conversationID: QualifiedIDEntity, newRole: MemberEntity.Role)
    suspend fun insertMembersWithQualifiedId(memberList: List<MemberEntity>, conversationID: QualifiedIDEntity)
    suspend fun insertMembers(memberList: List<MemberEntity>, groupId: String)
    suspend fun deleteMemberByQualifiedID(userID: QualifiedIDEntity, conversationID: QualifiedIDEntity)

    /**
     * Deletes a list of user ids from the Conversation.
     * return a list of the users that where actually deleted
     * if the list is empty then no user was deleted
     */
    suspend fun deleteMembersByQualifiedID(userIDList: List<QualifiedIDEntity>, conversationID: QualifiedIDEntity): Long
    suspend fun observeConversationMembers(qualifiedID: QualifiedIDEntity): Flow<List<MemberEntity>>
    suspend fun updateConversationMemberRole(conversationId: QualifiedIDEntity, userId: UserIDEntity, role: MemberEntity.Role)
    suspend fun updateOrInsertOneOnOneMember(
        member: MemberEntity,
        conversationID: QualifiedIDEntity
    )

    suspend fun observeIsUserMember(conversationId: QualifiedIDEntity, userId: UserIDEntity): Flow<Boolean>
    suspend fun updateFullMemberList(memberList: List<MemberEntity>, conversationID: QualifiedIDEntity)

    suspend fun getGroupConversationWithUserIdsWithBothDomains(
        firstDomain: String,
        secondDomain: String
    ): Map<ConversationIDEntity, List<UserIDEntity>>

    suspend fun getOneOneConversationWithFederatedMembers(domain: String): Map<ConversationIDEntity, UserIDEntity>
    suspend fun selectMembersNameAndHandle(conversationId: QualifiedIDEntity): Map<QualifiedIDEntity, NameAndHandleEntity>
}

@Suppress("TooManyFunctions")
internal class MemberDAOImpl internal constructor(
    private val membersCache: FlowCache<ConversationIDEntity, List<MemberEntity>>,
    private val memberQueries: MembersQueries,
    private val userQueries: UsersQueries,
    private val conversationsQueries: ConversationsQueries,
    private val coroutineContext: CoroutineContext,
    private val memberMapper: MemberMapper = MemberMapper()
) : MemberDAO {

    override suspend fun insertMember(member: MemberEntity, conversationID: QualifiedIDEntity) = withContext(coroutineContext) {
        memberQueries.transaction {
            userQueries.insertOrIgnoreUserId(member.user)
            val conversationExist = conversationsQueries.selectByQualifiedId(conversationID).executeAsList().firstOrNull() != null
            if (conversationExist) {
                memberQueries.insertMember(member.user, conversationID, member.role)
            } else {
                kaliumLogger.w(
                    "conversation ${conversationID.toLogString()} " +
                            "doest not exist for user ${member.user.toLogString()}"
                )
            }
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
            val conversationExist = conversationsQueries.selectByQualifiedId(conversationID).executeAsList().firstOrNull() != null
            for (member: MemberEntity in memberList) {
                userQueries.insertOrIgnoreUserId(member.user)
                if (conversationExist) {
                    memberQueries.insertMember(member.user, conversationID, member.role)
                } else {
                    kaliumLogger.w(
                        "conversation ${conversationID.toLogString()} " +
                                "doest not exist for user ${member.user.toLogString()}"
                    )
                }
            }
        }

    override suspend fun insertMembers(memberList: List<MemberEntity>, groupId: String) {
        withContext(coroutineContext) {
            conversationsQueries.selectByGroupId(groupId).executeAsOneOrNull()?.let {
                nonSuspendInsertMembersWithQualifiedId(memberList, it.qualified_id)
            }
        }
    }

    override suspend fun deleteMemberByQualifiedID(userID: QualifiedIDEntity, conversationID: QualifiedIDEntity) =
        withContext(coroutineContext) {
            memberQueries.deleteMember(conversationID, userID)
        }

    override suspend fun deleteMembersByQualifiedID(
        userIDList: List<QualifiedIDEntity>,
        conversationID: QualifiedIDEntity
    ): Long =
        withContext(coroutineContext) {
            memberQueries.transactionWithResult {
                memberQueries.deleteMembers(conversationID, userIDList)
                memberQueries.selectChanges().executeAsOne()
            }
        }

    override suspend fun observeConversationMembers(
        qualifiedID: QualifiedIDEntity
    ): Flow<List<MemberEntity>> = membersCache.get(qualifiedID) {
        memberQueries.selectAllMembersByConversation(qualifiedID)
            .asFlow()
            .flowOn(coroutineContext)
            .mapToList()
            .map { it.map(memberMapper::toModel) }
    }

    override suspend fun updateConversationMemberRole(conversationId: QualifiedIDEntity, userId: UserIDEntity, role: MemberEntity.Role) =
        withContext(coroutineContext) {
            memberQueries.updateMemberRole(role, userId, conversationId)
        }

    override suspend fun updateOrInsertOneOnOneMember(
        member: MemberEntity,
        conversationID: QualifiedIDEntity
    ) = withContext(coroutineContext) {
        memberQueries.transaction {
            conversationsQueries.updateConversationType(ConversationEntity.Type.ONE_ON_ONE, conversationID)
            val conversationRecordExist = conversationsQueries.selectChanges().executeAsOne() != 0L
            if (conversationRecordExist) {
                memberQueries.insertMember(member.user, conversationID, member.role)
            } else {
                kaliumLogger.w(
                    "conversation ${conversationID.toLogString()} " +
                            "doest not exist for user ${member.user.toLogString()}"
                )
            }
        }
    }

    override suspend fun observeIsUserMember(conversationId: QualifiedIDEntity, userId: UserIDEntity): Flow<Boolean> =
        memberQueries.isUserMember(conversationId, userId)
            .asFlow()
            .flowOn(coroutineContext)
            .mapToOneOrNull()
            .map { it != null }

    override suspend fun updateFullMemberList(memberList: List<MemberEntity>, conversationID: QualifiedIDEntity) =
        withContext(coroutineContext) {
            memberQueries.transaction {
                val membersToDelete = memberQueries.selectAllMembersByConversation(conversationID)
                    .executeAsList()
                    .filter { member -> memberList.none { it.user == member.user } }

                memberList.forEach { member ->
                    userQueries.insertOrIgnoreUserId(member.user)
                    memberQueries.insertOrUpdateMember(member.user, conversationID, member.role)
                }

                membersToDelete.forEach { member ->
                    memberQueries.deleteMember(conversationID, member.user)
                }
            }
        }

    override suspend fun getGroupConversationWithUserIdsWithBothDomains(
        firstDomain: String,
        secondDomain: String
    ): Map<ConversationIDEntity, List<UserIDEntity>> = withContext(coroutineContext) {
        memberQueries.selectFederatedMembersWithOneOfDomainsFromGroupConversation(firstDomain, secondDomain)
            .executeAsList()
            .groupBy { it.conversation }
            .filter { (_, members) ->
                members.any { it.user.domain == firstDomain } &&
                        members.any { it.user.domain == secondDomain }
            }
            .mapValues { it.value.map { membersFromOneOfTwoDomains -> membersFromOneOfTwoDomains.user } }
    }

    override suspend fun getOneOneConversationWithFederatedMembers(
        domain: String,
    ): Map<ConversationIDEntity, UserIDEntity> = withContext(coroutineContext) {
        memberQueries.selectFederatedMembersFromOneOnOneConversations(domain)
            .executeAsList()
            .associateBy({ it.conversation }, { it.user })
    }

    override suspend fun selectMembersNameAndHandle(conversationId: QualifiedIDEntity) = withContext(coroutineContext) {
        memberQueries.selectMembersNamesAndHandle(conversationId).executeAsList()
            .let { members -> members.associate { it.user to NameAndHandleEntity(it.name, it.handle) } }
    }
}
