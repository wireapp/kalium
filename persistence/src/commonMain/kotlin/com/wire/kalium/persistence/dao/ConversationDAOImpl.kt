package com.wire.kalium.persistence.dao

import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToList
import com.squareup.sqldelight.runtime.coroutines.mapToOneOrNull
import com.wire.kalium.persistence.ConversationsQueries
import com.wire.kalium.persistence.MembersQueries
import com.wire.kalium.persistence.UsersQueries
import kotlinx.coroutines.flow.Flow
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
                ConversationEntity.Protocol.MLS -> ConversationEntity.ProtocolInfo.MLS(
                    conversation.mls_group_id ?: "",
                    conversation.mls_group_state
                )
                ConversationEntity.Protocol.PROTEUS -> ConversationEntity.ProtocolInfo.Proteus
            },
            mutedStatus = conversation.muted_status,
            mutedTime = conversation.muted_time
        )
    }

}

class MemberMapper {
    fun toModel(member: SQLDelightMember): Member {
        return Member(member.user)
    }
}

class ConversationDAOImpl(
    private val conversationQueries: ConversationsQueries,
    private val userQueries: UsersQueries,
    private val memberQueries: MembersQueries
) : ConversationDAO {

    private val memberMapper = MemberMapper()
    private val conversationMapper = ConversationMapper()

    override fun getSelfConversationId() =
        conversationQueries.selectConversationByType(ConversationEntity.Type.SELF).executeAsOneOrNull()?.qualified_id

    override fun insertConversation(conversationEntity: ConversationEntity) {
        nonSuspendingInsertConversation(conversationEntity)
    }

    override fun insertConversations(conversationEntities: List<ConversationEntity>) {
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
            if (conversationEntity.protocolInfo is ConversationEntity.ProtocolInfo.MLS) ConversationEntity.Protocol.MLS else ConversationEntity.Protocol.PROTEUS,
            conversationEntity.mutedStatus,
            conversationEntity.mutedTime
        )
    }

    override fun updateConversation(conversationEntity: ConversationEntity) {
        conversationQueries.updateConversation(
            conversationEntity.name,
            conversationEntity.type,
            conversationEntity.teamId,
            conversationEntity.id
        )
    }

    override fun updateConversationGroupState(groupState: ConversationEntity.GroupState, groupId: String) {
        conversationQueries.updateConversationGroupState(groupState, groupId)
    }

    override fun getAllConversationsFlow(): Flow<List<ConversationEntity>> {
        return conversationQueries.selectAllConversations()
            .asFlow()
            .mapToList()
            .map { it.map(conversationMapper::toModel) }
    }

    override fun getConversationByQualifiedIDFlow(qualifiedID: QualifiedIDEntity): Flow<ConversationEntity?> {
        return conversationQueries.selectByQualifiedId(qualifiedID)
            .asFlow()
            .mapToOneOrNull()
            .map { it?.let { conversationMapper.toModel(it) } }
    }

    override fun getConversationByQualifiedID(qualifiedID: QualifiedIDEntity): ConversationEntity? =
        conversationQueries.selectByQualifiedId(qualifiedID).executeAsOneOrNull()?.let { conversationMapper.toModel(it) }

    override fun getConversationByGroupID(groupID: String): ConversationEntity? =
        conversationQueries.selectByGroupId(groupID).executeAsOneOrNull()?.let { conversationMapper.toModel(it) }

    override fun deleteConversationByQualifiedID(qualifiedID: QualifiedIDEntity) {
        conversationQueries.deleteConversation(qualifiedID)
    }

    override fun insertMember(member: Member, conversationID: QualifiedIDEntity) {
        memberQueries.transaction {
            userQueries.insertOrIgnoreUserId(member.user)
            memberQueries.insertMember(member.user, conversationID)
        }
    }

    override fun insertMembers(memberList: List<Member>, conversationID: QualifiedIDEntity) {
        memberQueries.transaction {
            for (member: Member in memberList) {
                userQueries.insertOrIgnoreUserId(member.user)
                memberQueries.insertMember(member.user, conversationID)
            }
        }
    }

    override fun insertOrUpdateOneOnOneMemberWithConnectionStatus(
        userId: UserIDEntity,
        status: UserEntity.ConnectionState,
        conversationID: QualifiedIDEntity
    ) {
        memberQueries.transaction {
            userQueries.insertOrReplaceUserIdWithConnectionStatus(userId, status)
            memberQueries.insertMember(userId, conversationID)
        }
    }

    override fun deleteMemberByQualifiedID(conversationID: QualifiedIDEntity, userID: QualifiedIDEntity) {
        memberQueries.deleteMember(conversationID, userID)
    }

    override fun getAllMembersFlow(qualifiedID: QualifiedIDEntity): Flow<List<Member>> {
        return memberQueries.selectAllMembersByConversation(qualifiedID)
            .asFlow()
            .mapToList()
            .map { it.map(memberMapper::toModel) }
    }

    override fun getAllMembers(qualifiedID: QualifiedIDEntity): List<Member> =
        memberQueries.selectAllMembersByConversation(qualifiedID).executeAsList().map(memberMapper::toModel)

    override fun updateConversationMutedStatus(
        conversationId: QualifiedIDEntity,
        mutedStatus: ConversationEntity.MutedStatus,
        mutedStatusTimestamp: Long
    ) {
        conversationQueries.updateConversationMutingStatus(mutedStatus, mutedStatusTimestamp, conversationId)
    }
}
