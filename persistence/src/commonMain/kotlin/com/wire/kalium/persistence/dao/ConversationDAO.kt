/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

package com.wire.kalium.persistence.dao

import com.wire.kalium.persistence.dao.call.CallEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant
import kotlin.time.Duration

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
    val lastNotificationDate: Instant?,
    val lastModifiedDate: Instant,
    // Date that indicates when the user has seen the conversation,
    val lastReadDate: Instant,
    val access: List<Access>,
    val accessRole: List<AccessRole>,
    val receiptMode: ReceiptMode,
    val guestRoomLink: String? = null,
    val messageTimer: Long?,
    val userMessageTimer: Long?
) {
    enum class AccessRole { TEAM_MEMBER, NON_TEAM_MEMBER, GUEST, SERVICE, EXTERNAL; }

    enum class Access { PRIVATE, INVITE, SELF_INVITE, LINK, CODE; }

    enum class Type { SELF, ONE_ON_ONE, GROUP, CONNECTION_PENDING, GLOBAL_TEAM }

    enum class GroupState { PENDING_CREATION, PENDING_JOIN, PENDING_WELCOME_MESSAGE, ESTABLISHED }

    enum class Protocol { PROTEUS, MLS, MIXED }
    enum class ReceiptMode { DISABLED, ENABLED }

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

    enum class MutedStatus { ALL_ALLOWED, ONLY_MENTIONS_AND_REPLIES_ALLOWED, MENTIONS_MUTED, ALL_MUTED }

    sealed interface ProtocolInfo {
        object Proteus : ProtocolInfo
        data class MLS(
            override val groupId: String,
            override val groupState: GroupState,
            override val epoch: ULong,
            override val keyingMaterialLastUpdate: Instant,
            override val cipherSuite: CipherSuite
        ) : MLSCapable
        data class Mixed(
            override val groupId: String,
            override val groupState: GroupState,
            override val epoch: ULong,
            override val keyingMaterialLastUpdate: Instant,
            override val cipherSuite: CipherSuite
        ) : MLSCapable

        sealed interface MLSCapable : ProtocolInfo {
            val groupId: String
            val groupState: GroupState
            val epoch: ULong
            val keyingMaterialLastUpdate: Instant
            val cipherSuite: CipherSuite
        }
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
    val lastModifiedDate: Instant?,
    val lastReadDate: Instant,
    val userAvailabilityStatus: UserAvailabilityStatusEntity?,
    val userType: UserTypeEntity?,
    val botService: BotIdEntity?,
    val userDeleted: Boolean?,
    val connectionStatus: ConnectionEntity.State? = ConnectionEntity.State.NOT_CONNECTED,
    val otherUserId: QualifiedIDEntity?,
    val isCreator: Long,
    val lastNotificationDate: Instant?,
    val selfRole: Member.Role?,
    val protocolInfo: ConversationEntity.ProtocolInfo,
    val accessList: List<ConversationEntity.Access>,
    val accessRoleList: List<ConversationEntity.AccessRole>,
    val protocol: ConversationEntity.Protocol,
    val mlsCipherSuite: ConversationEntity.CipherSuite,
    val mlsEpoch: Long,
    val mlsGroupId: String?,
    val mlsLastKeyingMaterialUpdateDate: Instant,
    val mlsGroupState: ConversationEntity.GroupState,
    val mlsProposalTimer: String?,
    val mutedTime: Long,
    val creatorId: String,
    val removedBy: UserIDEntity? = null, // TODO how to calculate?,
    val receiptMode: ConversationEntity.ReceiptMode,
    val messageTimer: Long?,
    val userMessageTimer: Long?,
    val userSupportedProtocols: Set<SupportedProtocolEntity>?
) {
    val isMember: Boolean get() = selfRole != null

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

data class ProposalTimerEntity(
    val groupID: String,
    val firingDate: Instant
)

interface ConversationDAO {
    suspend fun getSelfConversationId(protocol: ConversationEntity.Protocol): QualifiedIDEntity?
    suspend fun insertConversation(conversationEntity: ConversationEntity)
    suspend fun insertConversations(conversationEntities: List<ConversationEntity>)
    suspend fun updateConversation(conversationEntity: ConversationEntity)
    suspend fun updateConversationGroupState(groupState: ConversationEntity.GroupState, groupId: String)
    suspend fun updateConversationModifiedDate(qualifiedID: QualifiedIDEntity, date: Instant)
    suspend fun updateConversationNotificationDate(qualifiedID: QualifiedIDEntity)
    suspend fun updateConversationReadDate(conversationID: QualifiedIDEntity, date: Instant)
    suspend fun updateAllConversationsNotificationDate()
    suspend fun getAllConversations(): Flow<List<ConversationViewEntity>>
    suspend fun getAllConversationDetails(): Flow<List<ConversationViewEntity>>
    suspend fun getAllProteusTeamConversations(teamId: String): Flow<List<ConversationViewEntity>>
    suspend fun observeGetConversationByQualifiedID(qualifiedID: QualifiedIDEntity): Flow<ConversationViewEntity?>
    suspend fun observeGetConversationBaseInfoByQualifiedID(qualifiedID: QualifiedIDEntity): Flow<ConversationEntity?>
    suspend fun getConversationBaseInfoByQualifiedID(qualifiedID: QualifiedIDEntity): ConversationEntity?
    suspend fun getConversationByQualifiedID(qualifiedID: QualifiedIDEntity): ConversationViewEntity?
    suspend fun observeConversationWithOtherUser(userId: UserIDEntity): Flow<ConversationViewEntity?>
    suspend fun getConversationProtocolInfo(qualifiedID: QualifiedIDEntity): ConversationEntity.ProtocolInfo?
    suspend fun getConversationByGroupID(groupID: String): Flow<ConversationViewEntity?>
    suspend fun getConversationIdByGroupID(groupID: String): QualifiedIDEntity?
    suspend fun getConversationsByGroupState(groupState: ConversationEntity.GroupState): List<ConversationViewEntity>
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

    suspend fun updateAccess(
        conversationID: QualifiedIDEntity,
        accessList: List<ConversationEntity.Access>,
        accessRoleList: List<ConversationEntity.AccessRole>
    )

    suspend fun updateConversationMemberRole(conversationId: QualifiedIDEntity, userId: UserIDEntity, role: Member.Role)
    suspend fun updateKeyingMaterial(groupId: String, timestamp: Instant)
    suspend fun getConversationsByKeyingMaterialUpdate(threshold: Duration): List<String>
    suspend fun setProposalTimer(proposalTimer: ProposalTimerEntity)
    suspend fun clearProposalTimer(groupID: String)
    suspend fun getProposalTimers(): Flow<List<ProposalTimerEntity>>
    suspend fun observeIsUserMember(conversationId: QualifiedIDEntity, userId: UserIDEntity): Flow<Boolean>
    suspend fun whoDeletedMeInConversation(conversationId: QualifiedIDEntity, selfUserIdString: String): UserIDEntity?
    suspend fun updateConversationName(conversationId: QualifiedIDEntity, conversationName: String, timestamp: String)
    suspend fun updateConversationType(conversationID: QualifiedIDEntity, type: ConversationEntity.Type)
    suspend fun revokeOneOnOneConversationsWithDeletedUser(userId: UserIDEntity)
    suspend fun getConversationIdsByUserId(userId: UserIDEntity): List<QualifiedIDEntity>
    suspend fun updateConversationReceiptMode(conversationID: QualifiedIDEntity, receiptMode: ConversationEntity.ReceiptMode)
    suspend fun updateGuestRoomLink(conversationId: QualifiedIDEntity, link: String?)
    suspend fun observeGuestRoomLinkByConversationId(conversationId: QualifiedIDEntity): Flow<String?>
    suspend fun updateMessageTimer(conversationId: QualifiedIDEntity, messageTimer: Long?): Boolean
    suspend fun updateUserMessageTimer(conversationId: QualifiedIDEntity, messageTimer: Long?)
}
