package com.wire.kalium.persistence.dao

import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToList
import com.squareup.sqldelight.runtime.coroutines.mapToOneOrNull
import com.wire.kalium.persistence.db.ConverationsQueries
import com.wire.kalium.persistence.db.MembersQueries
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.wire.kalium.persistence.db.Conversation as SQLDelightConversation
import com.wire.kalium.persistence.db.Member as SQLDelightMember

class ConverationMapper {
    fun toDAO(conversation: SQLDelightConversation): Conversation {
        return Conversation(conversation.qualified_id, conversation.name)
    }
}

class MemberMapper {
    fun toDAO(member: SQLDelightMember): Member {
        return Member(member.user)
    }
}

class ConversationDAOImpl(private val conversationQueries: ConverationsQueries,
                          private val memberQueries: MembersQueries): ConversationDAO {

    val memberMapper = MemberMapper()
    val conversationMapper = ConverationMapper()

    override suspend fun insertConversation(conversation: Conversation) {
        conversationQueries.insertConversation(conversation.id, conversation.name)
    }

    override suspend fun insertConversations(conversations: List<Conversation>) {
        conversationQueries.transaction {
            for (conversation: Conversation in conversations) {
                conversationQueries.insertConversation(conversation.id, conversation.name)
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
            .map { it.map(conversationMapper::toDAO) }
    }

    override suspend fun getConversationByQualifiedID(qualifiedID: QualifiedID): Flow<Conversation?> {
        return conversationQueries.selectByQualifiedId(qualifiedID)
            .asFlow()
            .mapToOneOrNull()
            .map { it?.let { conversationMapper.toDAO(it) } }
    }

    override suspend fun deleteConversationByQualifiedID(qualifiedID: QualifiedID) {
        conversationQueries.deleteConversation(qualifiedID)
    }

    override suspend fun insertMember(member: Member, conversationID: QualifiedID) {
        memberQueries.insertMember(member.user, conversationID)
    }

    override suspend fun insertMembers(members: List<Member>, conversationID: QualifiedID) {
        memberQueries.transaction {
            for (member: Member in members) {
                memberQueries.insertMember(member.user, conversationID)
            }
        }

    }

    override suspend fun getAllMembers(qualifiedID: QualifiedID): Flow<List<Member>> {
        return memberQueries.selectAllMembersByConversation(qualifiedID)
            .asFlow()
            .mapToList()
            .map { it.map(memberMapper::toDAO)}
    }

}
