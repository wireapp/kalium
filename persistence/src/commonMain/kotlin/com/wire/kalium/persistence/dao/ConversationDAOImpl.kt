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
    fun toModel(conversation: SQLDelightConversation): Conversation {
        return Conversation(conversation.qualified_id, conversation.name)
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

    override suspend fun insertConversation(conversation: Conversation) {
        conversationQueries.insertConversation(conversation.id, conversation.name)
    }

    override suspend fun insertConversations(conversationList: List<Conversation>) {
        conversationQueries.transaction {
            for (conversationEntity: Conversation in conversationList) {
                conversationQueries.insertConversation(conversationEntity.id, conversationEntity.name)
            }
        }
    }

    override suspend fun updateConversation(conversation: Conversation) {
        conversationQueries.updateConversation(conversation.name, conversation.id)
    }

    override suspend fun getAllConversations(): Flow<List<Conversation>> {
        return conversationQueries.selectAllConversations()
            .asFlow()
            .mapToList()
            .map { it.map(conversationMapper::toModel) }
    }

    override suspend fun getConversationByQualifiedID(qualifiedID: QualifiedIDEntity): Flow<Conversation?> {
        return conversationQueries.selectByQualifiedId(qualifiedID)
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

    override suspend fun deleteMemberByQualifiedID(conversationID: QualifiedIDEntity, userID: QualifiedIDEntity) {
        memberQueries.deleteMember(conversationID, userID)
    }


    override suspend fun getAllMembers(qualifiedID: QualifiedIDEntity): Flow<List<Member>> {
        return memberQueries.selectAllMembersByConversation(qualifiedID)
            .asFlow()
            .mapToList()
            .map { it.map(memberMapper::toModel) }
    }
}
