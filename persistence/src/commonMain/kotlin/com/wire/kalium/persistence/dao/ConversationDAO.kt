package com.wire.kalium.persistence.dao

import kotlinx.coroutines.flow.Flow

data class ConversationEntity(
    val id: QualifiedIDEntity,
    val name: String?,
    val type: Type,
    val teamId: String?,
    val protocolInfo: ProtocolInfo,
    val mutedStatus: MutedStatus = MutedStatus.ALL_ALLOWED,
    val mutedTime: Long = 0
) {

    enum class Type { SELF, ONE_ON_ONE, GROUP }

    enum class GroupState { PENDING, PENDING_WELCOME_MESSAGE, ESTABLISHED }

    enum class Protocol { PROTEUS, MLS }

    enum class MutedStatus { ALL_ALLOWED, ONLY_MENTIONS_ALLOWED, MENTIONS_MUTED, ALL_MUTED }

    sealed class ProtocolInfo {
        object Proteus : ProtocolInfo()
        data class MLS(val groupId: String, val groupState: GroupState) : ProtocolInfo()
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
    suspend fun getAllConversations(): Flow<List<ConversationEntity>>
    suspend fun getConversationByQualifiedID(qualifiedID: QualifiedIDEntity): Flow<ConversationEntity?>
    suspend fun getConversationByGroupID(groupID: String): Flow<ConversationEntity?>
    suspend fun deleteConversationByQualifiedID(qualifiedID: QualifiedIDEntity)
    suspend fun insertMember(member: Member, conversationID: QualifiedIDEntity)
    suspend fun insertMembers(memberList: List<Member>, conversationID: QualifiedIDEntity)
    suspend fun deleteMemberByQualifiedID(conversationID: QualifiedIDEntity, userID: QualifiedIDEntity)
    suspend fun getAllMembers(qualifiedID: QualifiedIDEntity): Flow<List<Member>>
    suspend fun updateConversationMutedStatus(
        conversationId: QualifiedIDEntity,
        mutedStatus: ConversationEntity.MutedStatus,
        mutedStatusTimestamp: Long
    )
}
