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
    fun getSelfConversationId(): QualifiedIDEntity?
    fun insertConversation(conversationEntity: ConversationEntity)
    fun insertConversations(conversationEntities: List<ConversationEntity>)
    fun updateConversation(conversationEntity: ConversationEntity)
    fun updateConversationGroupState(groupState: ConversationEntity.GroupState, groupId: String)
    fun getAllConversationsFlow(): Flow<List<ConversationEntity>>
    fun getConversationByQualifiedIDFlow(qualifiedID: QualifiedIDEntity): Flow<ConversationEntity?>
    fun getConversationByQualifiedID(qualifiedID: QualifiedIDEntity): ConversationEntity?
    fun getConversationByGroupID(groupID: String): ConversationEntity?
    fun deleteConversationByQualifiedID(qualifiedID: QualifiedIDEntity)
    fun insertMember(member: Member, conversationID: QualifiedIDEntity)
    fun insertMembers(memberList: List<Member>, conversationID: QualifiedIDEntity)
    fun deleteMemberByQualifiedID(conversationID: QualifiedIDEntity, userID: QualifiedIDEntity)
    fun getAllMembersFlow(qualifiedID: QualifiedIDEntity): Flow<List<Member>>
    fun getAllMembers(qualifiedID: QualifiedIDEntity): List<Member>
    fun insertOrUpdateOneOnOneMemberWithConnectionStatus(
        userId: UserIDEntity,
        status: UserEntity.ConnectionState,
        conversationID: QualifiedIDEntity
    )
    fun updateConversationMutedStatus(
        conversationId: QualifiedIDEntity,
        mutedStatus: ConversationEntity.MutedStatus,
        mutedStatusTimestamp: Long
    )
}
