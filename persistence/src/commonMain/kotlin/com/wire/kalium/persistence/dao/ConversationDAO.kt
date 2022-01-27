package com.wire.kalium.persistence.dao

import kotlinx.coroutines.flow.Flow

data class Conversation(
    val id: QualifiedID,
    val name: String?
) { }

data class Member(
    val user: QualifiedID
) { }

interface ConversationDAO {
    suspend fun insertConversation(conversation: Conversation)
    suspend fun insertConversations(conversations: List<Conversation>)
    suspend fun updateConversation(conversation: Conversation)
    suspend fun getAllConversations(): Flow<List<Conversation>>
    suspend fun getConversationByQualifiedID(qualifiedID: QualifiedID): Flow<Conversation?>
    suspend fun deleteConversationByQualifiedID(qualifiedID: QualifiedID)
    suspend fun insertMember(member: Member, conversationID: QualifiedID)
    suspend fun insertMembers(members: List<Member>, conversationID: QualifiedID)
    suspend fun getAllMembers(qualifiedID: QualifiedID): Flow<List<Member>>
}
