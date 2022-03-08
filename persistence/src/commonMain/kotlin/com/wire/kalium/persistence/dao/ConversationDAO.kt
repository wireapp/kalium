package com.wire.kalium.persistence.dao

import kotlinx.coroutines.flow.Flow

data class ConversationEntity(
    val id: QualifiedID,
    val name: String?,
    val type: Type
) {
    enum class Type { SELF, ONE_ON_ONE, GROUP }
}

data class Member(
    val user: QualifiedID
)

interface ConversationDAO {
    suspend fun insertConversation(conversationEntity: ConversationEntity)
    suspend fun insertConversations(conversationEntities: List<ConversationEntity>)
    suspend fun updateConversation(conversationEntity: ConversationEntity)
    suspend fun getAllConversations(): Flow<List<ConversationEntity>>
    suspend fun getConversationByQualifiedID(qualifiedID: QualifiedID): Flow<ConversationEntity?>
    suspend fun deleteConversationByQualifiedID(qualifiedID: QualifiedID)
    suspend fun insertMember(member: Member, conversationID: QualifiedID)
    suspend fun insertMembers(members: List<Member>, conversationID: QualifiedID)
    suspend fun deleteMemberByQualifiedID(conversationID: QualifiedID, userID: QualifiedID)
    suspend fun getAllMembers(qualifiedID: QualifiedID): Flow<List<Member>>
}
