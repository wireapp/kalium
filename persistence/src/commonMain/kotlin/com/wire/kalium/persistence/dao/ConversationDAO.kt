package com.wire.kalium.persistence.dao

import kotlinx.coroutines.flow.Flow

// TODO: add conversation / user table
data class ConversationEntity(
    val id: QualifiedID,
    val name: String?
)

data class MemberEntity(
    val user: QualifiedID
)

interface ConversationDAO {
    suspend fun insertConversation(conversationEntity: ConversationEntity)
    suspend fun insertConversations(conversationEntities: List<ConversationEntity>)
    suspend fun updateConversation(conversationEntity: ConversationEntity)
    suspend fun getAllConversations(): Flow<List<ConversationEntity>>
    suspend fun getConversationByQualifiedID(qualifiedID: QualifiedID): Flow<ConversationEntity?>
    suspend fun deleteConversationByQualifiedID(qualifiedID: QualifiedID)
    suspend fun insertMember(member: MemberEntity, conversationID: QualifiedID)
    suspend fun insertMembers(members: List<MemberEntity>, conversationID: QualifiedID)
    suspend fun deleteMemberByQualifiedID(conversationID: QualifiedID, userID: QualifiedID)
    suspend fun getAllMembers(qualifiedID: QualifiedID): Flow<List<MemberEntity>>
}
