package com.wire.kalium.persistence.dao

import kotlinx.coroutines.flow.Flow

data class Conversation(
    val id: QualifiedIDEntity,
    val name: String?
) { }

data class Member(
    val user: QualifiedIDEntity
) { }

interface ConversationDAO {
    suspend fun insertConversation(conversation: Conversation)
    suspend fun insertConversations(conversations: List<Conversation>)
    suspend fun updateConversation(conversation: Conversation)
    suspend fun getAllConversations(): Flow<List<Conversation>>
    suspend fun getConversationByQualifiedID(qualifiedID: QualifiedIDEntity): Flow<Conversation?>
    suspend fun deleteConversationByQualifiedID(qualifiedID: QualifiedIDEntity)
    suspend fun insertMember(member: Member, conversationID: QualifiedIDEntity)
    suspend fun insertMembers(members: List<Member>, conversationID: QualifiedIDEntity)
    suspend fun deleteMemberByQualifiedID(conversationID: QualifiedIDEntity, userID: QualifiedIDEntity)
    suspend fun getAllMembers(qualifiedID: QualifiedIDEntity): Flow<List<Member>>
}
