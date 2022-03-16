package com.wire.kalium.persistence.dao

import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToList
import com.squareup.sqldelight.runtime.coroutines.mapToOneOrNull
import com.wire.kalium.persistence.db.ConverationsQueries
import com.wire.kalium.persistence.db.MembersQueries
import com.wire.kalium.persistence.db.UsersQueries
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.wire.kalium.persistence.db.Conversation as SQLDelightConversation
import com.wire.kalium.persistence.db.Member as SQLDelightMember

class ConversationMapper {
    fun toModel(conversation: SQLDelightConversation): ConversationEntity {
        return ConversationEntity(conversation.qualified_id, conversation.name, conversation.type, conversation.team_id)
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

    override suspend fun insertConversation(conversationEntity: ConversationEntity) {
        conversationQueries.insertConversation(conversationEntity.id, conversationEntity.name, conversationEntity.type)
    }

    override suspend fun insertConversations(conversationEntities: List<ConversationEntity>) {
        conversationQueries.transaction {
            for (conversationEntity: ConversationEntity in conversationEntities) {
                conversationQueries.insertConversation(conversationEntity.id, conversationEntity.name, conversationEntity.type)
            }
        }
    }

    override suspend fun updateConversation(conversationEntity: ConversationEntity) {
        conversationQueries.updateConversation(conversationEntity.name, conversationEntity.id)
    }

    override suspend fun getAllConversations(): Flow<List<ConversationEntity>> {
        return conversationQueries.selectAllConversations()
            .asFlow()
            .mapToList()
            .map { it.map(conversationMapper::toModel) }
    }

    override suspend fun getConversationByQualifiedID(qualifiedID: QualifiedID): Flow<ConversationEntity?> {
        return conversationQueries.selectByQualifiedId(qualifiedID)
            .asFlow()
            .mapToOneOrNull()
            .map { it?.let { conversationMapper.toModel(it) } }
    }

    override suspend fun deleteConversationByQualifiedID(qualifiedID: QualifiedID) {
        conversationQueries.deleteConversation(qualifiedID)
    }

    override suspend fun insertMember(member: Member, conversationID: QualifiedID) {
        memberQueries.transaction {
            userQueries.insertOrIgnoreUserId(member.user)
            memberQueries.insertMember(member.user, conversationID)
        }
    }

    override suspend fun insertMembers(members: List<Member>, conversationID: QualifiedID) {
        memberQueries.transaction {
            for (member: Member in members) {
                userQueries.insertOrIgnoreUserId(member.user)
                memberQueries.insertMember(member.user, conversationID)
            }
        }

    }

    override suspend fun deleteMemberByQualifiedID(conversationID: QualifiedID, userID: QualifiedID) {
        memberQueries.deleteMember(conversationID, userID)
    }

    override suspend fun getAllMembers(qualifiedID: QualifiedID): Flow<List<Member>> {
        return memberQueries.selectAllMembersByConversation(qualifiedID)
            .asFlow()
            .mapToList()
            .map { it.map(memberMapper::toModel) }
    }

}
