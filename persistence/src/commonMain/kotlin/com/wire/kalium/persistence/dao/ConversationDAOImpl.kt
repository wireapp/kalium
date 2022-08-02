package com.wire.kalium.persistence.dao

import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToList
import com.squareup.sqldelight.runtime.coroutines.mapToOneOrNull
import com.wire.kalium.persistence.ConversationsQueries
import com.wire.kalium.persistence.MembersQueries
import com.wire.kalium.persistence.UsersQueries
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import com.wire.kalium.persistence.Conversation as SQLDelightConversation
import com.wire.kalium.persistence.Member as SQLDelightMember

private class ConversationMapper {
    fun toModel(conversation: SQLDelightConversation): ConversationEntity = with(conversation) {
        ConversationEntity(
            qualified_id,
            name,
            type,
            team_id,
            protocolInfo = when (protocol) {
                ConversationEntity.Protocol.MLS -> ConversationEntity.ProtocolInfo.MLS(
                    mls_group_id ?: "",
                    mls_group_state,
                    mls_epoch.toULong()
                )
                ConversationEntity.Protocol.PROTEUS -> ConversationEntity.ProtocolInfo.Proteus
            },
            mutedStatus = muted_status,
            mutedTime = muted_time,
            lastNotificationDate = last_notified_message_date,
            lastModifiedDate = last_modified_date,
            access = access_list,
            accessRole = access_role_list
        )
    }
}

class MemberMapper {
    fun toModel(member: SQLDelightMember): Member {
        return Member(member.user, member.role)
    }
}

private const val MLS_DEFAULT_EPOCH = 0L

class ConversationDAOImpl(
    private val conversationQueries: ConversationsQueries,
    private val userQueries: UsersQueries,
    private val memberQueries: MembersQueries
) : ConversationDAO {

    private val memberMapper = MemberMapper()
    private val conversationMapper = ConversationMapper()

    // TODO: the DB holds information about the conversation type Self, OneOnOne...ect
    override suspend fun getSelfConversationId() =
        getAllConversations().first().first { it.type == ConversationEntity.Type.SELF }.id

    override suspend fun insertConversation(conversationEntity: ConversationEntity) {
        nonSuspendingInsertConversation(conversationEntity)
    }

    override suspend fun insertConversations(conversationEntities: List<ConversationEntity>) {
        conversationQueries.transaction {

            for (conversationEntity: ConversationEntity in conversationEntities) {
                nonSuspendingInsertConversation(conversationEntity)
            }
        }
    }

    private fun nonSuspendingInsertConversation(conversationEntity: ConversationEntity) {
        with(conversationEntity) {
            conversationQueries.insertConversation(
                id,
                name,
                type,
                teamId,
                if (protocolInfo is ConversationEntity.ProtocolInfo.MLS) protocolInfo.groupId
                else null,
                if (protocolInfo is ConversationEntity.ProtocolInfo.MLS) protocolInfo.groupState
                else ConversationEntity.GroupState.ESTABLISHED,
                if (protocolInfo is ConversationEntity.ProtocolInfo.MLS) protocolInfo.epoch.toLong()
                else MLS_DEFAULT_EPOCH,
                if (protocolInfo is ConversationEntity.ProtocolInfo.MLS) ConversationEntity.Protocol.MLS
                else ConversationEntity.Protocol.PROTEUS,
                mutedStatus,
                mutedTime,
                lastModifiedDate,
                lastNotificationDate,
                access,
                accessRole
            )
        }
    }

    override suspend fun updateConversation(conversationEntity: ConversationEntity) {
        conversationQueries.updateConversation(
            conversationEntity.name,
            conversationEntity.type,
            conversationEntity.teamId,
            conversationEntity.id
        )
    }

    override suspend fun updateConversationGroupState(groupState: ConversationEntity.GroupState, groupId: String) {
        conversationQueries.updateConversationGroupState(groupState, groupId)
    }

    override suspend fun updateConversationModifiedDate(qualifiedID: QualifiedIDEntity, date: String) {
        conversationQueries.updateConversationModifiedDate(date, qualifiedID)
    }

    override suspend fun updateConversationNotificationDate(qualifiedID: QualifiedIDEntity, date: String) {
        conversationQueries.updateConversationNotificationsDate(date, qualifiedID)
    }

    override suspend fun updateAllConversationsNotificationDate(date: String) {
        conversationQueries.transaction {
            conversationQueries.selectConversationsWithUnnotifiedMessages()
                .executeAsList()
                .forEach { conversationQueries.updateConversationNotificationsDate(date, it.qualified_id) }
        }
    }

    override suspend fun getAllConversations(): Flow<List<ConversationEntity>> {
        return conversationQueries.selectAllConversations()
            .asFlow()
            .mapToList()
            .map { it.map(conversationMapper::toModel) }
    }

    override suspend fun observeGetConversationByQualifiedID(qualifiedID: QualifiedIDEntity): Flow<ConversationEntity?> {
        return conversationQueries.selectByQualifiedId(qualifiedID)
            .asFlow()
            .mapToOneOrNull()
            .map { it?.let { conversationMapper.toModel(it) } }
    }

    override suspend fun getConversationByQualifiedID(qualifiedID: QualifiedIDEntity): ConversationEntity {
        return conversationQueries.selectByQualifiedId(qualifiedID).executeAsOne().let {
            conversationMapper.toModel(it)
        }
    }

    override suspend fun getAllConversationWithOtherUser(userId: UserIDEntity): List<ConversationEntity> {
        val allMemberConversations = memberQueries.selectAllConversationsByMember(userId)
            .executeAsList()

        return allMemberConversations.map { getConversationByQualifiedID(it.conversation) }
    }

    override suspend fun getConversationByGroupID(groupID: String): Flow<ConversationEntity?> {
        return conversationQueries.selectByGroupId(groupID)
            .asFlow()
            .mapToOneOrNull()
            .map { it?.let { conversationMapper.toModel(it) } }
    }

    override suspend fun getConversationIdByGroupID(groupID: String) =
        conversationQueries.getConversationIdByGroupId(groupID).executeAsOne()

    override suspend fun getConversationsByGroupState(groupState: ConversationEntity.GroupState): List<ConversationEntity> =
        conversationQueries.selectByGroupState(groupState, ConversationEntity.Protocol.MLS)
            .executeAsList()
            .map(conversationMapper::toModel)

    override suspend fun deleteConversationByQualifiedID(qualifiedID: QualifiedIDEntity) {
        conversationQueries.deleteConversation(qualifiedID)
    }

    override suspend fun insertMember(member: Member, conversationID: QualifiedIDEntity) {
        memberQueries.transaction {
            userQueries.insertOrIgnoreUserId(member.user)
            memberQueries.insertMember(member.user, conversationID, member.role)
        }
    }

    override suspend fun insertMembers(memberList: List<Member>, conversationID: QualifiedIDEntity) {
        memberQueries.transaction {
            for (member: Member in memberList) {
                userQueries.insertOrIgnoreUserId(member.user)
                memberQueries.insertMember(member.user, conversationID, member.role)
            }
        }
    }

    override suspend fun insertMembers(memberList: List<Member>, groupId: String) {
        getConversationByGroupID(groupId).firstOrNull()?.let {
            insertMembers(memberList, it.id)
        }
    }

    override suspend fun updateOrInsertOneOnOneMemberWithConnectionStatus(
        member: Member,
        status: ConnectionEntity.State,
        conversationID: QualifiedIDEntity
    ) {
        memberQueries.transaction {
            userQueries.updateUserConnectionStatus(status, member.user)
            val recordDidNotExist = userQueries.selectChanges().executeAsOne() == 0L
            if (recordDidNotExist) {
                userQueries.insertOrIgnoreUserIdWithConnectionStatus(member.user, status)
            }
            memberQueries.insertMember(member.user, conversationID, member.role)
        }
    }

    override suspend fun deleteMemberByQualifiedID(userID: QualifiedIDEntity, conversationID: QualifiedIDEntity) {
        memberQueries.deleteMember(conversationID, userID)
    }

    override suspend fun deleteMembersByQualifiedID(userIDList: List<QualifiedIDEntity>, conversationID: QualifiedIDEntity) {
        memberQueries.transaction {
            userIDList.forEach {
                memberQueries.deleteMember(conversationID, it)
            }
        }
    }

    override suspend fun deleteMembersByQualifiedID(userIDList: List<QualifiedIDEntity>, groupId: String) {
        getConversationByGroupID(groupId).firstOrNull()?.let {
            deleteMembersByQualifiedID(userIDList, it.id)
        }
    }

    override suspend fun getAllMembers(qualifiedID: QualifiedIDEntity): Flow<List<Member>> {
        return memberQueries.selectAllMembersByConversation(qualifiedID.value)
            .asFlow()
            .mapToList()
            .map { it.map(memberMapper::toModel) }
    }

    override suspend fun updateConversationMutedStatus(
        conversationId: QualifiedIDEntity,
        mutedStatus: ConversationEntity.MutedStatus,
        mutedStatusTimestamp: Long
    ) {
        conversationQueries.updateConversationMutingStatus(mutedStatus, mutedStatusTimestamp, conversationId)
    }

    override suspend fun getConversationsForNotifications(): Flow<List<ConversationEntity>> {
        return conversationQueries.selectConversationsWithUnnotifiedMessages()
            .asFlow()
            .mapToList()
            .map { it.map(conversationMapper::toModel) }
    }

    override suspend fun updateAccess(
        conversationID: QualifiedIDEntity,
        accessList: List<ConversationEntity.Access>,
        accessRoleList: List<ConversationEntity.AccessRole>
    ) {
        conversationQueries.updateAccess(accessList, accessRoleList, conversationID)
    }

    override suspend fun updateConversationMemberRole(conversationId: QualifiedIDEntity, userId: UserIDEntity, role: Member.Role) =
        memberQueries.updateMemberRole(role, userId, conversationId)
}
