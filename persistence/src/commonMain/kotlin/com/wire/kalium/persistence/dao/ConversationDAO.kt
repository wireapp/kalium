package com.wire.kalium.persistence.dao

import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration

// TODO: Regardless of how we store this in SQLite we can convert it to an Instant at this level and above.
data class ConversationEntity(
    val id: QualifiedIDEntity,
    val name: String?,
    val type: Type,
    val teamId: String?,
    val protocolInfo: ProtocolInfo,
    val mutedStatus: MutedStatus = MutedStatus.ALL_ALLOWED,
    val mutedTime: Long = 0,
    val lastNotificationDate: String?,
    val lastModifiedDate: String,
    // Date that indicates when the user has seen the conversation,
    val lastReadDate: String,
    val access: List<Access>,
    val accessRole: List<AccessRole>
) {
    enum class AccessRole { TEAM_MEMBER, NON_TEAM_MEMBER, GUEST, SERVICE, EXTERNAL; }

    enum class Access { PRIVATE, INVITE, LINK, CODE; }

    enum class Type { SELF, ONE_ON_ONE, GROUP, CONNECTION_PENDING }

    enum class GroupState { PENDING_CREATION, PENDING_JOIN, PENDING_WELCOME_MESSAGE, ESTABLISHED }

    enum class Protocol { PROTEUS, MLS }

    enum class MutedStatus { ALL_ALLOWED, ONLY_MENTIONS_ALLOWED, MENTIONS_MUTED, ALL_MUTED }

    sealed class ProtocolInfo {
        object Proteus : ProtocolInfo()
        data class MLS(
            val groupId: String,
            val groupState: GroupState,
            val epoch: ULong,
            val keyingMaterialLastUpdate: ULong
        ) : ProtocolInfo()
    }
}

// TODO: rename to MemberEntity
data class Member(
    val user: QualifiedIDEntity,
    val role: Role
) {
    sealed class Role {
        object Member : Role()
        object Admin : Role()
        data class Unknown(val name: String) : Role()
    }
}

interface ConversationDAO {
    suspend fun getSelfConversationId(): QualifiedIDEntity
    suspend fun insertConversation(conversationEntity: ConversationEntity)
    suspend fun insertConversations(conversationEntities: List<ConversationEntity>)
    suspend fun updateConversation(conversationEntity: ConversationEntity)
    suspend fun updateConversationGroupState(groupState: ConversationEntity.GroupState, groupId: String)
    suspend fun updateConversationModifiedDate(qualifiedID: QualifiedIDEntity, date: String)
    suspend fun updateConversationNotificationDate(qualifiedID: QualifiedIDEntity, date: String)
    suspend fun updateConversationReadDate(conversationID: QualifiedIDEntity, date: String)
    suspend fun updateAllConversationsNotificationDate(date: String)
    suspend fun getAllConversations(): Flow<List<ConversationEntity>>
    suspend fun observeGetConversationByQualifiedID(qualifiedID: QualifiedIDEntity): Flow<ConversationEntity?>
    suspend fun getConversationByQualifiedID(qualifiedID: QualifiedIDEntity): ConversationEntity?
    suspend fun getAllConversationWithOtherUser(userId: UserIDEntity): List<ConversationEntity>
    suspend fun getConversationByGroupID(groupID: String): Flow<ConversationEntity?>
    suspend fun getConversationIdByGroupID(groupID: String): QualifiedIDEntity?
    suspend fun getConversationsByGroupState(groupState: ConversationEntity.GroupState): List<ConversationEntity>
    suspend fun deleteConversationByQualifiedID(qualifiedID: QualifiedIDEntity)
    suspend fun insertMember(member: Member, conversationID: QualifiedIDEntity)
    suspend fun insertMembers(memberList: List<Member>, conversationID: QualifiedIDEntity)
    suspend fun insertMembers(memberList: List<Member>, groupId: String)
    suspend fun deleteMemberByQualifiedID(userID: QualifiedIDEntity, conversationID: QualifiedIDEntity)
    suspend fun deleteMembersByQualifiedID(userIDList: List<QualifiedIDEntity>, conversationID: QualifiedIDEntity)
    suspend fun deleteMembersByQualifiedID(userIDList: List<QualifiedIDEntity>, groupId: String)
    suspend fun getAllMembers(qualifiedID: QualifiedIDEntity): Flow<List<Member>>
    suspend fun updateOrInsertOneOnOneMemberWithConnectionStatus(
        member: Member,
        status: ConnectionEntity.State,
        conversationID: QualifiedIDEntity
    )
    suspend fun updateConversationMutedStatus(
        conversationId: QualifiedIDEntity,
        mutedStatus: ConversationEntity.MutedStatus,
        mutedStatusTimestamp: Long
    )
    suspend fun getConversationsForNotifications(): Flow<List<ConversationEntity>>

    suspend fun updateAccess(
        conversationID: QualifiedIDEntity,
        accessList: List<ConversationEntity.Access>,
        accessRoleList: List<ConversationEntity.AccessRole>
    )
    suspend fun getUnreadMessageCount(conversationID: QualifiedIDEntity): Long
    suspend fun getUnreadConversationCount(): Long
    suspend fun updateConversationMemberRole(conversationId: QualifiedIDEntity, userId: UserIDEntity, role: Member.Role)
    suspend fun updateKeyingMaterial(groupId: String, lastUpdate: Duration)
    suspend fun getConversationsByKeyingMaterialUpdate(threshold: Duration): List<String>
}
