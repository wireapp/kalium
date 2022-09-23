package com.wire.kalium.persistence.dao

import com.wire.kalium.persistence.dao.call.CallEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant
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
    val removedBy: UserIDEntity? = null,
    val creatorId: String,
    val lastNotificationDate: String?,
    val lastModifiedDate: String,
    // Date that indicates when the user has seen the conversation,
    val lastReadDate: String,
    val access: List<Access>,
    val accessRole: List<AccessRole>,
    val isCreator: Boolean = false
) {
    enum class AccessRole { TEAM_MEMBER, NON_TEAM_MEMBER, GUEST, SERVICE, EXTERNAL; }

    enum class Access { PRIVATE, INVITE, LINK, CODE; }

    enum class Type { SELF, ONE_ON_ONE, GROUP, CONNECTION_PENDING }

    enum class GroupState { PENDING_CREATION, PENDING_JOIN, PENDING_WELCOME_MESSAGE, ESTABLISHED }

    enum class Protocol { PROTEUS, MLS }

    @Suppress("MagicNumber")
    enum class CipherSuite(val cipherSuiteTag: Int) {
        UNKNOWN(0),
        MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519(1),
        MLS_128_DHKEMP256_AES128GCM_SHA256_P256(2),
        MLS_128_DHKEMX25519_CHACHA20POLY1305_SHA256_Ed25519(3),
        MLS_256_DHKEMX448_AES256GCM_SHA512_Ed448(4),
        MLS_256_DHKEMP521_AES256GCM_SHA512_P521(5),
        MLS_256_DHKEMX448_CHACHA20POLY1305_SHA512_Ed448(6),
        MLS_256_DHKEMP384_AES256GCM_SHA384_P384(7);

        companion object {
            fun fromTag(tag: Int?): CipherSuite =
                if (tag != null) values().first { type -> type.cipherSuiteTag == tag } else UNKNOWN
        }
    }

    enum class MutedStatus { ALL_ALLOWED, ONLY_MENTIONS_ALLOWED, MENTIONS_MUTED, ALL_MUTED }

    sealed class ProtocolInfo {
        object Proteus : ProtocolInfo()
        data class MLS(
            val groupId: String,
            val groupState: GroupState,
            val epoch: ULong,
            val keyingMaterialLastUpdate: Instant,
            val cipherSuite: CipherSuite
        ) : ProtocolInfo()
    }
}

@Suppress("FunctionParameterNaming")
data class ConversationViewEntity(
    val id: QualifiedIDEntity,
    val name: String?,
    val type: ConversationEntity.Type,
    val callStatus: CallEntity.Status?,
    val previewAssetId: QualifiedIDEntity?,
    val mutedStatus: ConversationEntity.MutedStatus,
    val teamId: String?,
    val lastModifiedDate: String,
    val lastReadDate: String,
    val userAvailabilityStatus: UserAvailabilityStatusEntity?,
    val userType: UserTypeEntity?,
    val botService: BotEntity?,
    val userDeleted: Boolean?,
    val connectionStatus: ConnectionEntity.State? = ConnectionEntity.State.NOT_CONNECTED,
    val otherUserId: QualifiedIDEntity?,
    val isCreator: Long,
    val lastNotificationDate: String?,
    val unreadMessageCount: Long,
    val isMember: Long,
    val protocolInfo: ConversationEntity.ProtocolInfo,
    val accessList: List<ConversationEntity.Access>,
    val accessRoleList: List<ConversationEntity.AccessRole>
)

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

data class ProposalTimerEntity(
    val groupID: String,
    val firingDate: Instant
)

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
    suspend fun getAllConversationsView(): Flow<List<ConversationViewEntity>>
    suspend fun observeGetConversationByQualifiedID(qualifiedID: QualifiedIDEntity): Flow<ConversationEntity?>
    suspend fun getConversationByQualifiedID(qualifiedID: QualifiedIDEntity): ConversationEntity?
    suspend fun getConversationWithOtherUser(userId: UserIDEntity): ConversationEntity?
    suspend fun getConversationByGroupID(groupID: String): Flow<ConversationEntity?>
    suspend fun getConversationIdByGroupID(groupID: String): QualifiedIDEntity?
    suspend fun getConversationsByGroupState(groupState: ConversationEntity.GroupState): List<ConversationEntity>
    suspend fun deleteConversationByQualifiedID(qualifiedID: QualifiedIDEntity)
    suspend fun insertMember(member: Member, conversationID: QualifiedIDEntity)
    suspend fun updateMember(member: Member, conversationID: QualifiedIDEntity)
    suspend fun insertMembersWithQualifiedId(memberList: List<Member>, conversationID: QualifiedIDEntity)
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

    suspend fun getUnreadConversationCount(): Long
    suspend fun updateConversationMemberRole(conversationId: QualifiedIDEntity, userId: UserIDEntity, role: Member.Role)
    suspend fun updateKeyingMaterial(groupId: String, timestamp: Instant)
    suspend fun getConversationsByKeyingMaterialUpdate(threshold: Duration): List<String>
    suspend fun setProposalTimer(proposalTimer: ProposalTimerEntity)
    suspend fun clearProposalTimer(groupID: String)
    suspend fun getProposalTimers(): Flow<List<ProposalTimerEntity>>
    suspend fun observeIsUserMember(conversationId: QualifiedIDEntity, userId: UserIDEntity): Flow<Boolean>
    suspend fun whoDeletedMeInConversation(conversationId: QualifiedIDEntity, selfUserIdString: String): UserIDEntity?
    suspend fun updateConversationName(conversationId: QualifiedIDEntity, conversationName: String, timestamp: String)
}
