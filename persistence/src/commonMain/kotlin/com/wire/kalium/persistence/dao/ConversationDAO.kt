package com.wire.kalium.persistence.dao

import kotlinx.coroutines.flow.Flow

data class ConversationEntity(
    val id: QualifiedIDEntity,
    val name: String?,
    val type: Type,
    val teamId: String?
) {
    enum class Type { SELF, ONE_ON_ONE, GROUP }
}

data class Member(
    val user: QualifiedIDEntity
)

interface ConversationDAO {
    suspend fun insertConversation(conversationEntity: ConversationEntity)
    suspend fun insertConversations(conversationEntities: List<ConversationEntity>)
    suspend fun updateConversation(conversationEntity: ConversationEntity)
    suspend fun getAllConversations(): Flow<List<ConversationEntity>>
    suspend fun getConversationByQualifiedID(qualifiedID: QualifiedIDEntity): Flow<ConversationEntity?>
    suspend fun deleteConversationByQualifiedID(qualifiedID: QualifiedIDEntity)
    suspend fun insertMember(member: Member, conversationID: QualifiedIDEntity)
    suspend fun insertMembers(memberList: List<Member>, conversationID: QualifiedIDEntity)
    suspend fun deleteMemberByQualifiedID(conversationID: QualifiedIDEntity, userID: QualifiedIDEntity)
    suspend fun getAllMembers(qualifiedID: QualifiedIDEntity): Flow<List<Member>>
}
