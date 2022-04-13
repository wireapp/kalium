package com.wire.kalium.persistence.dao

import kotlinx.coroutines.flow.Flow

data class ConversationEntity(
    val id: QualifiedIDEntity,
    val name: String?,
    val type: Type,
    val teamId: String?,
    val protocolInfo: ProtocolInfo,
    val lastNotificationDate: String?
) {

    enum class Type { SELF, ONE_ON_ONE, GROUP }

    enum class GroupState { PENDING, PENDING_WELCOME_MESSAGE, ESTABLISHED }

    enum class Protocol { PROTEUS, MLS }

    sealed class ProtocolInfo {
        object Proteus: ProtocolInfo()
        data class MLS(val groupId: String, val groupState: GroupState): ProtocolInfo()
    }
}

data class Member(
    val user: QualifiedIDEntity
)

interface ConversationDAO {
    suspend fun getSelfConversationId(): QualifiedIDEntity
    suspend fun insertConversation(conversationEntity: ConversationEntity)
    suspend fun insertConversations(conversationEntities: List<ConversationEntity>)
    suspend fun updateConversation(conversationEntity: ConversationEntity)
    suspend fun updateConversationGroupState(groupState: ConversationEntity.GroupState, groupId: String)
    suspend fun setConversationAsNonNotified(qualifiedID: QualifiedIDEntity)
    suspend fun setConversationAsNotified(qualifiedID: QualifiedIDEntity, date: String)
    suspend fun setAllConversationsAsNotified(date: String)
    suspend fun getAllConversations(): Flow<List<ConversationEntity>>
    suspend fun getConversationByQualifiedID(qualifiedID: QualifiedIDEntity): Flow<ConversationEntity?>
    suspend fun getConversationByGroupID(groupID: String): Flow<ConversationEntity?>
    suspend fun deleteConversationByQualifiedID(qualifiedID: QualifiedIDEntity)
    suspend fun insertMember(member: Member, conversationID: QualifiedIDEntity)
    suspend fun insertMembers(memberList: List<Member>, conversationID: QualifiedIDEntity)
    suspend fun deleteMemberByQualifiedID(conversationID: QualifiedIDEntity, userID: QualifiedIDEntity)
    suspend fun getAllMembers(qualifiedID: QualifiedIDEntity): Flow<List<Member>>
    suspend fun getConversationsForNotifications(): Flow<List<ConversationEntity>>
}
