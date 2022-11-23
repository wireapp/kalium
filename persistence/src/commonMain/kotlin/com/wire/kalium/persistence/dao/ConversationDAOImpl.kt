package com.wire.kalium.persistence.dao

import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToList
import com.squareup.sqldelight.runtime.coroutines.mapToOneOrNull
import com.wire.kalium.persistence.ConversationsQueries
import com.wire.kalium.persistence.MembersQueries
import com.wire.kalium.persistence.SelectConversationByMember
import com.wire.kalium.persistence.UsersQueries
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.toInstant
import kotlin.time.Duration
import com.wire.kalium.persistence.ConversationDetails as SQLDelightConversationView
import com.wire.kalium.persistence.Member as SQLDelightMember

private class ConversationMapper {
    fun toModel(conversation: SQLDelightConversationView): ConversationViewEntity = with(conversation) {
        val unreadContentType = UnreadContentMapper.unreadContentTypeFromJsonString(unreadContentTypeJson)
        ConversationViewEntity(
            id = qualifiedId,
            name = name,
            type = type,
            teamId = teamId,
            protocolInfo = mapProtocolInfo(
                protocol,
                mls_group_id,
                mls_group_state,
                mls_epoch,
                mls_last_keying_material_update,
                mls_cipher_suite
            ),
            isCreator = isCreator,
            mutedStatus = mutedStatus,
            mutedTime = muted_time,
            creatorId = creator_id,
            lastNotificationDate = lastNotifiedMessageDate,
            lastModifiedDate = last_modified_date,
            lastReadDate = lastReadDate,
            firstUnreadMessageDate = firstUnreadMessageDate,
            accessList = access_list,
            accessRoleList = access_role_list,
            protocol = protocol,
            mlsCipherSuite = mls_cipher_suite,
            mlsEpoch = mls_epoch,
            mlsGroupId = mls_group_id,
            mlsLastKeyingMaterialUpdate = mls_last_keying_material_update,
            mlsGroupState = mls_group_state,
            mlsProposalTimer = mls_proposal_timer,
            callStatus = callStatus,
            previewAssetId = previewAssetId,
            userAvailabilityStatus = userAvailabilityStatus,
            userType = userType,
            botService = botService,
            userDeleted = userDeleted,
            connectionStatus = connectionStatus,
            otherUserId = otherUserId,
            unreadMentionsCount = unreadMentionsCount,
            isMember = isMember,
            unreadContentCountEntity = unreadContentType
        )
    }

    fun fromOneToOneToModel(conversation: SelectConversationByMember?): ConversationViewEntity? {
        return conversation?.run {
            val unreadContentType = UnreadContentMapper.unreadContentTypeFromJsonString(unreadContentTypeJson)
            ConversationViewEntity(
                id = qualifiedId,
                name = name,
                type = type,
                teamId = teamId,
                protocolInfo = mapProtocolInfo(
                    protocol,
                    mls_group_id,
                    mls_group_state,
                    mls_epoch,
                    mls_last_keying_material_update,
                    mls_cipher_suite
                ),
                isCreator = isCreator,
                mutedStatus = mutedStatus,
                mutedTime = muted_time,
                creatorId = creator_id,
                lastNotificationDate = lastNotifiedMessageDate,
                lastModifiedDate = last_modified_date,
                lastReadDate = lastReadDate,
                firstUnreadMessageDate = firstUnreadMessageDate,
                accessList = access_list,
                accessRoleList = access_role_list,
                protocol = protocol,
                mlsCipherSuite = mls_cipher_suite,
                mlsEpoch = mls_epoch,
                mlsGroupId = mls_group_id,
                mlsLastKeyingMaterialUpdate = mls_last_keying_material_update,
                mlsGroupState = mls_group_state,
                mlsProposalTimer = mls_proposal_timer,
                callStatus = callStatus,
                previewAssetId = previewAssetId,
                userAvailabilityStatus = userAvailabilityStatus,
                userType = userType,
                botService = botService,
                userDeleted = userDeleted,
                connectionStatus = connectionStatus,
                otherUserId = otherUserId,
                unreadMentionsCount = unreadMentionsCount,
                isMember = isMember,
                unreadContentCountEntity = unreadContentType
            )
        }
    }

    @Suppress("LongParameterList")
    private fun mapProtocolInfo(
        protocol: ConversationEntity.Protocol,
        mlsGroupId: String?,
        mlsGroupState: ConversationEntity.GroupState,
        mlsEpoch: Long,
        mlsLastKeyingMaterialUpdate: Long,
        mlsCipherSuite: ConversationEntity.CipherSuite,
    ): ConversationEntity.ProtocolInfo {
        return when (protocol) {
            ConversationEntity.Protocol.MLS -> ConversationEntity.ProtocolInfo.MLS(
                mlsGroupId ?: "",
                mlsGroupState,
                mlsEpoch.toULong(),
                Instant.fromEpochSeconds(mlsLastKeyingMaterialUpdate),
                mlsCipherSuite
            )

            ConversationEntity.Protocol.PROTEUS -> ConversationEntity.ProtocolInfo.Proteus
        }
    }
}

class MemberMapper {
    fun toModel(member: SQLDelightMember): Member {
        return Member(member.user, member.role)
    }
}

private const val MLS_DEFAULT_EPOCH = 0L
private const val MLS_DEFAULT_LAST_KEY_MATERIAL_UPDATE = 0L
private val MLS_DEFAULT_CIPHER_SUITE = ConversationEntity.CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519

// TODO: Refactor. We can split this into smaller DAOs.
//       For example, one for Members, one for Protocol/MLS-related things, etc.
//       Even if they operate on the same table underneath, these DAOs can represent/do different things.
@Suppress("TooManyFunctions")
class ConversationDAOImpl(
    private val conversationQueries: ConversationsQueries,
    private val userQueries: UsersQueries,
    private val memberQueries: MembersQueries
) : ConversationDAO {

    private val memberMapper = MemberMapper()
    private val conversationMapper = ConversationMapper()
    override suspend fun getSelfConversationId() = conversationQueries.selfConversationId().executeAsOneOrNull()
    override suspend fun insertConversation(conversationEntity: ConversationEntity) {
        nonSuspendingInsertConversation(conversationEntity)
    }

    override suspend fun insertConversations(conversationEntities: List<ConversationEntity>) {
        conversationQueries.transaction {

            for (conversationEntity: ConversationEntity in conversationEntities) {
                nonSuspendingInsertConversation(conversationEntity)
            }
        }
    }

    private fun nonSuspendingInsertConversation(conversationEntity: ConversationEntity) {
        with(conversationEntity) {
            conversationQueries.insertConversation(
                id,
                name,
                type,
                teamId,
                if (protocolInfo is ConversationEntity.ProtocolInfo.MLS) protocolInfo.groupId
                else null,
                if (protocolInfo is ConversationEntity.ProtocolInfo.MLS) protocolInfo.groupState
                else ConversationEntity.GroupState.ESTABLISHED,
                if (protocolInfo is ConversationEntity.ProtocolInfo.MLS) protocolInfo.epoch.toLong()
                else MLS_DEFAULT_EPOCH,
                if (protocolInfo is ConversationEntity.ProtocolInfo.MLS) ConversationEntity.Protocol.MLS
                else ConversationEntity.Protocol.PROTEUS,
                mutedStatus,
                mutedTime,
                creatorId,
                lastModifiedDate,
                lastNotificationDate,
                access,
                accessRole,
                lastReadDate,
                if (protocolInfo is ConversationEntity.ProtocolInfo.MLS) protocolInfo.keyingMaterialLastUpdate.epochSeconds
                else MLS_DEFAULT_LAST_KEY_MATERIAL_UPDATE,
                if (protocolInfo is ConversationEntity.ProtocolInfo.MLS) protocolInfo.cipherSuite
                else MLS_DEFAULT_CIPHER_SUITE
            )
        }
    }

    override suspend fun updateConversation(conversationEntity: ConversationEntity) {
        conversationQueries.updateConversation(
            conversationEntity.name,
            conversationEntity.type,
            conversationEntity.teamId,
            conversationEntity.id
        )
    }

    override suspend fun updateConversationGroupState(groupState: ConversationEntity.GroupState, groupId: String) {
        conversationQueries.updateConversationGroupState(groupState, groupId)
    }

    override suspend fun updateConversationModifiedDate(qualifiedID: QualifiedIDEntity, date: String) {
        conversationQueries.updateConversationModifiedDate(date, qualifiedID)
    }

    override suspend fun updateConversationNotificationDate(qualifiedID: QualifiedIDEntity, date: String) {
        conversationQueries.updateConversationNotificationsDate(date, qualifiedID)
    }

    override suspend fun updateAllConversationsNotificationDate(date: String) {
        conversationQueries.transaction {
            conversationQueries.selectConversationsWithUnnotifiedMessages()
                .executeAsList()
                .forEach { conversationQueries.updateConversationNotificationsDate(date, it.qualifiedId) }
        }
    }

    override suspend fun getAllConversations(): Flow<List<ConversationViewEntity>> {
        return conversationQueries.selectAllConversations()
            .asFlow()
            .mapToList()
            .map { it.map(conversationMapper::toModel) }
    }

    override suspend fun getAllConversationDetails(): Flow<List<ConversationViewEntity>> {
        return conversationQueries.selectAllConversationDetails()
            .asFlow()
            .mapToList()
            .map { list -> list.map { it.let { conversationMapper.toModel(it) } } }
    }

    override suspend fun observeGetConversationByQualifiedID(qualifiedID: QualifiedIDEntity): Flow<ConversationViewEntity?> {
        return conversationQueries.selectByQualifiedId(qualifiedID)
            .asFlow()
            .mapToOneOrNull()
            .map { it?.let { conversationMapper.toModel(it) } }
    }

    override suspend fun getConversationByQualifiedID(qualifiedID: QualifiedIDEntity): ConversationViewEntity {
        return conversationQueries.selectByQualifiedId(qualifiedID).executeAsOne().let {
            conversationMapper.toModel(it)
        }
    }

    override suspend fun observeConversationWithOtherUser(userId: UserIDEntity): Flow<ConversationViewEntity?> {
        return memberQueries.selectConversationByMember(userId)
            .asFlow()
            .mapToOneOrNull()
            .map { it?.let { conversationMapper.fromOneToOneToModel(it) } }
    }

    override suspend fun getConversationByGroupID(groupID: String): Flow<ConversationViewEntity?> {
        return conversationQueries.selectByGroupId(groupID)
            .asFlow()
            .mapToOneOrNull()
            .map { it?.let { conversationMapper.toModel(it) } }
    }

    override suspend fun getConversationIdByGroupID(groupID: String) =
        conversationQueries.getConversationIdByGroupId(groupID).executeAsOne()

    override suspend fun getConversationsByGroupState(groupState: ConversationEntity.GroupState): List<ConversationViewEntity> =
        conversationQueries.selectByGroupState(groupState, ConversationEntity.Protocol.MLS)
            .executeAsList()
            .map(conversationMapper::toModel)

    override suspend fun deleteConversationByQualifiedID(qualifiedID: QualifiedIDEntity) {
        conversationQueries.deleteConversation(qualifiedID)
    }

    override suspend fun insertMember(member: Member, conversationID: QualifiedIDEntity) {
        memberQueries.transaction {
            userQueries.insertOrIgnoreUserId(member.user)
            memberQueries.insertMember(member.user, conversationID, member.role)
        }
    }

    override suspend fun updateMember(member: Member, conversationID: QualifiedIDEntity) {
        memberQueries.updateMemberRole(member.role, member.user, conversationID)
    }

    override suspend fun insertMembersWithQualifiedId(memberList: List<Member>, conversationID: QualifiedIDEntity) {
        memberQueries.transaction {
            for (member: Member in memberList) {
                userQueries.insertOrIgnoreUserId(member.user)
                memberQueries.insertMember(member.user, conversationID, member.role)
            }
        }
    }

    override suspend fun insertMembers(memberList: List<Member>, groupId: String) {
        getConversationByGroupID(groupId).firstOrNull()?.let {
            insertMembersWithQualifiedId(memberList, it.id)
        }
    }

    override suspend fun updateOrInsertOneOnOneMemberWithConnectionStatus(
        member: Member,
        status: ConnectionEntity.State,
        conversationID: QualifiedIDEntity
    ) {
        memberQueries.transaction {
            userQueries.updateUserConnectionStatus(status, member.user)
            val recordDidNotExist = userQueries.selectChanges().executeAsOne() == 0L
            if (recordDidNotExist) {
                userQueries.insertOrIgnoreUserIdWithConnectionStatus(member.user, status)
            }
            conversationQueries.updateConversationType(ConversationEntity.Type.ONE_ON_ONE, conversationID)
            memberQueries.insertMember(member.user, conversationID, member.role)
        }
    }

    override suspend fun deleteMemberByQualifiedID(userID: QualifiedIDEntity, conversationID: QualifiedIDEntity) {
        memberQueries.deleteMember(conversationID, userID)
    }

    override suspend fun deleteMembersByQualifiedID(userIDList: List<QualifiedIDEntity>, conversationID: QualifiedIDEntity) {
        memberQueries.transaction {
            userIDList.forEach {
                memberQueries.deleteMember(conversationID, it)
            }
        }
    }

    override suspend fun deleteMembersByQualifiedID(userIDList: List<QualifiedIDEntity>, groupId: String) {
        getConversationByGroupID(groupId).firstOrNull()?.let {
            deleteMembersByQualifiedID(userIDList, it.id)
        }
    }

    override suspend fun getAllMembers(qualifiedID: QualifiedIDEntity): Flow<List<Member>> {
        return memberQueries.selectAllMembersByConversation(qualifiedID.value)
            .asFlow()
            .mapToList()
            .map { it.map(memberMapper::toModel) }
    }

    override suspend fun updateConversationMutedStatus(
        conversationId: QualifiedIDEntity,
        mutedStatus: ConversationEntity.MutedStatus,
        mutedStatusTimestamp: Long
    ) {
        conversationQueries.updateConversationMutingStatus(mutedStatus, mutedStatusTimestamp, conversationId)
    }

    override suspend fun getConversationsForNotifications(): Flow<List<ConversationViewEntity>> {
        return conversationQueries.selectConversationsWithUnnotifiedMessages()
            .asFlow()
            .mapToList()
            .map { it.map(conversationMapper::toModel) }
    }

    override suspend fun updateAccess(
        conversationID: QualifiedIDEntity,
        accessList: List<ConversationEntity.Access>,
        accessRoleList: List<ConversationEntity.AccessRole>
    ) {
        conversationQueries.updateAccess(accessList, accessRoleList, conversationID)
    }

    override suspend fun getUnreadConversationCount(): Long =
        conversationQueries.getUnreadConversationCount().executeAsOne()

    override suspend fun updateConversationReadDate(conversationID: QualifiedIDEntity, date: String) {
        conversationQueries.updateConversationReadDate(date, conversationID)
    }

    override suspend fun updateConversationMemberRole(conversationId: QualifiedIDEntity, userId: UserIDEntity, role: Member.Role) =
        memberQueries.updateMemberRole(role, userId, conversationId)

    override suspend fun updateKeyingMaterial(groupId: String, timestamp: Instant) {
        conversationQueries.updateKeyingMaterialDate(timestamp.epochSeconds, groupId)
    }

    override suspend fun getConversationsByKeyingMaterialUpdate(threshold: Duration): List<String> =
        conversationQueries.selectByKeyingMaterialUpdate(
            ConversationEntity.GroupState.ESTABLISHED,
            ConversationEntity.Protocol.MLS,
            Clock.System.now().epochSeconds.minus(threshold.inWholeSeconds)
        ).executeAsList()

    override suspend fun setProposalTimer(proposalTimer: ProposalTimerEntity) {
        conversationQueries.updateProposalTimer(proposalTimer.firingDate.toString(), proposalTimer.groupID)
    }

    override suspend fun clearProposalTimer(groupID: String) {
        conversationQueries.clearProposalTimer(groupID)
    }

    override suspend fun getProposalTimers(): Flow<List<ProposalTimerEntity>> {
        return conversationQueries.selectProposalTimers(ConversationEntity.Protocol.MLS)
            .asFlow()
            .mapToList()
            .map { list -> list.map { ProposalTimerEntity(it.mls_group_id, it.mls_proposal_timer.toInstant()) } }
    }

    override suspend fun observeIsUserMember(conversationId: QualifiedIDEntity, userId: UserIDEntity): Flow<Boolean> =
        conversationQueries.isUserMember(conversationId, userId).asFlow().mapToOneOrNull().map { it != null }

    override suspend fun whoDeletedMeInConversation(conversationId: QualifiedIDEntity, selfUserIdString: String): UserIDEntity? =
        conversationQueries.whoDeletedMeInConversation(conversationId, selfUserIdString).executeAsOneOrNull()

    override suspend fun updateConversationName(conversationId: QualifiedIDEntity, conversationName: String, timestamp: String) {
        conversationQueries.updateConversationName(conversationName, timestamp, conversationId)
    }

    override suspend fun updateConversationType(conversationID: QualifiedIDEntity, type: ConversationEntity.Type) {
        conversationQueries.updateConversationType(type, conversationID)
    }

    override suspend fun revokeOneOnOneConversationsWithDeletedUser(userId: UserIDEntity) {
        memberQueries.deleteUserFromGroupConversations(userId, userId)
    }

    override suspend fun getConversationIdsByUserId(userId: UserIDEntity): List<QualifiedIDEntity> {
        return memberQueries.selectConversationsByMember(userId).executeAsList().map { it.conversation }
    }

}
