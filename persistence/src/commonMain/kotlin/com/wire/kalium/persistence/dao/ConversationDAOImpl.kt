package com.wire.kalium.persistence.dao

import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToList
import com.squareup.sqldelight.runtime.coroutines.mapToOneOrNull
import com.wire.kalium.persistence.ConverationsQueries
import com.wire.kalium.persistence.MembersQueries
import com.wire.kalium.persistence.UsersQueries
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import com.wire.kalium.persistence.Conversation as SQLDelightConversation
import com.wire.kalium.persistence.Member as SQLDelightMember

class ConversationMapper {
    fun toModel(conversation: SQLDelightConversation): ConversationEntity {
        return ConversationEntity(
            conversation.qualified_id,
            conversation.name,
            conversation.type,
            conversation.team_id,
            protocolInfo = when (conversation.protocol) {
                ConversationEntity.Protocol.MLS -> ConversationEntity.ProtocolInfo.MLS(conversation.mls_group_id ?: "", conversation.mls_group_state)
                ConversationEntity.Protocol.PROTEUS -> ConversationEntity.ProtocolInfo.Proteus
            },
            conversation.last_notification_date
        )
    }

}

class MemberMapper {
    fun toModel(member: SQLDelightMember): Member {
        return Member(member.user)
    }
}

class ConversationDAOImpl(
    private val conversationQueries: ConverationsQueries,
    private val userQueries: UsersQueries,
    private val memberQueries: MembersQueries
) : ConversationDAO {

    private val memberMapper = MemberMapper()
    private val conversationMapper = ConversationMapper()

    override suspend fun getSelfConversationId() = getAllConversations().first().first { it.type == ConversationEntity.Type.SELF }.id

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
        conversationQueries.insertConversation(
            conversationEntity.id,
            conversationEntity.name,
            conversationEntity.type,
            conversationEntity.teamId,
            if (conversationEntity.protocolInfo is ConversationEntity.ProtocolInfo.MLS) conversationEntity.protocolInfo.groupId else null,
            if (conversationEntity.protocolInfo is ConversationEntity.ProtocolInfo.MLS) conversationEntity.protocolInfo.groupState else ConversationEntity.GroupState.ESTABLISHED,
            if (conversationEntity.protocolInfo is ConversationEntity.ProtocolInfo.MLS) ConversationEntity.Protocol.MLS else ConversationEntity.Protocol.PROTEUS
        )
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

    override suspend fun setConversationAsNonNotified(qualifiedID: QualifiedIDEntity) {
        conversationQueries.updateConversationNotificatiosState(true, qualifiedID)
    }

    override suspend fun setConversationAsNotified(qualifiedID: QualifiedIDEntity, date: String) {
        conversationQueries.updateConversationNotificatiosStateAndDate(date, false, qualifiedID)
    }

    override suspend fun setAllConversationsAsNotified(date: String) {
        conversationQueries.transaction {
            conversationQueries.selectConversationsWithNotification()
                .executeAsList()
                .forEach { conversationQueries.updateConversationNotificatiosStateAndDate(date, false, it.qualified_id) }
        }
    }

    override suspend fun getAllConversations(): Flow<List<ConversationEntity>> {
        return conversationQueries.selectAllConversations()
            .asFlow()
            .mapToList()
            .map { it.map(conversationMapper::toModel) }
    }

    override suspend fun getConversationByQualifiedID(qualifiedID: QualifiedIDEntity): Flow<ConversationEntity?> {
        return conversationQueries.selectByQualifiedId(qualifiedID)
            .asFlow()
            .mapToOneOrNull()
            .map { it?.let { conversationMapper.toModel(it) } }
    }

    override suspend fun getConversationByGroupID(groupID: String): Flow<ConversationEntity?> {
        return conversationQueries.selectByGroupId(groupID)
            .asFlow()
            .mapToOneOrNull()
            .map { it?.let { conversationMapper.toModel(it) } }
    }

    override suspend fun deleteConversationByQualifiedID(qualifiedID: QualifiedIDEntity) {
        conversationQueries.deleteConversation(qualifiedID)
    }

    override suspend fun insertMember(member: Member, conversationID: QualifiedIDEntity) {
        memberQueries.transaction {
            userQueries.insertOrIgnoreUserId(member.user)
            memberQueries.insertMember(member.user, conversationID)
        }
    }

    override suspend fun insertMembers(memberList: List<Member>, conversationID: QualifiedIDEntity) {
        memberQueries.transaction {
            for (member: Member in memberList) {
                userQueries.insertOrIgnoreUserId(member.user)
                memberQueries.insertMember(member.user, conversationID)
            }
        }

    }

    override suspend fun insertOrUpdateOneOnOneMemberWithConnectionStatus(
        userId: UserIDEntity,
        status: UserEntity.ConnectionState,
        conversationID: QualifiedIDEntity
    ) {
        memberQueries.transaction {
            userQueries.insertOrReplaceUserIdWithConnectionStatus(userId, status)
            memberQueries.insertMember(userId, conversationID)
        }
    }

    override suspend fun deleteMemberByQualifiedID(conversationID: QualifiedIDEntity, userID: QualifiedIDEntity) {
        memberQueries.deleteMember(conversationID, userID)
    }

    override suspend fun getAllMembers(qualifiedID: QualifiedIDEntity): Flow<List<Member>> {
        return memberQueries.selectAllMembersByConversation(qualifiedID)
            .asFlow()
            .mapToList()
            .map { it.map(memberMapper::toModel) }
    }

    override suspend fun getConversationsForNotifications(): Flow<List<ConversationEntity>> {
        return conversationQueries.selectConversationsWithNotification()
            .asFlow()
            .mapToList()
            .map { it.map(conversationMapper::toModel) }
    }
}
