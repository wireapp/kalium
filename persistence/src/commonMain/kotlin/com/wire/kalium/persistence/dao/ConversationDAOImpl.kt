package com.wire.kalium.persistence.dao

import app.cash.sqldelight.coroutines.asFlow
import com.wire.kalium.persistence.ConversationsQueries
import com.wire.kalium.persistence.MembersQueries
import com.wire.kalium.persistence.SelectConversationByMember
import com.wire.kalium.persistence.UsersQueries
import com.wire.kalium.persistence.util.mapToList
import com.wire.kalium.persistence.util.mapToOneOrNull
import com.wire.kalium.util.DateTimeUtil
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.datetime.toInstant
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
import com.wire.kalium.persistence.ConversationDetails as SQLDelightConversationView
import com.wire.kalium.persistence.Member as SQLDelightMember

private class ConversationMapper {
    fun toModel(conversation: SQLDelightConversationView): ConversationViewEntity = with(conversation) {
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
                mls_last_keying_material_update_date,
                mls_cipher_suite
            ),
            isCreator = isCreator,
            mutedStatus = mutedStatus,
            mutedTime = muted_time,
            creatorId = creator_id,
            lastNotificationDate = lastNotifiedMessageDate,
            lastModifiedDate = last_modified_date,
            lastReadDate = lastReadDate,
            accessList = access_list,
            accessRoleList = access_role_list,
            protocol = protocol,
            mlsCipherSuite = mls_cipher_suite,
            mlsEpoch = mls_epoch,
            mlsGroupId = mls_group_id,
            mlsLastKeyingMaterialUpdateDate = mls_last_keying_material_update_date,
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
            selfRole = selfRole,
            receiptMode = receipt_mode
        )
    }

    @Suppress("LongParameterList")
    fun toModel(
        qualifiedId: QualifiedIDEntity,
        name: String?,
        type: ConversationEntity.Type,
        teamId: String?,
        mlsGroupId: String?,
        mlsGroupState: ConversationEntity.GroupState,
        mlsEpoch: Long,
        mlsProposalTimer: String?,
        protocol: ConversationEntity.Protocol,
        mutedStatus: ConversationEntity.MutedStatus,
        mutedTime: Long,
        creatorId: String,
        lastModifiedDate: Instant,
        lastNotifiedDate: Instant?,
        lastReadDate: Instant,
        accessList: List<ConversationEntity.Access>,
        accessRoleList: List<ConversationEntity.AccessRole>,
        mlsLastKeyingMaterialUpdateDate: Instant,
        mlsCipherSuite: ConversationEntity.CipherSuite,
        receiptMode: ConversationEntity.ReceiptMode
    ) = ConversationEntity(
        id = qualifiedId,
        name = name,
        type = type,
        teamId = teamId,
        protocolInfo = mapProtocolInfo(
            protocol,
            mlsGroupId,
            mlsGroupState,
            mlsEpoch,
            mlsLastKeyingMaterialUpdateDate,
            mlsCipherSuite
        ),
        mutedStatus = mutedStatus,
        mutedTime = mutedTime,
        creatorId = creatorId,
        lastNotificationDate = lastNotifiedDate,
        lastModifiedDate = lastModifiedDate,
        lastReadDate = lastReadDate,
        access = accessList,
        accessRole = accessRoleList,
        receiptMode = receiptMode
    )

    fun fromOneToOneToModel(conversation: SelectConversationByMember?): ConversationViewEntity? {
        return conversation?.run {
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
                    mls_last_keying_material_update_date,
                    mls_cipher_suite
                ),
                isCreator = isCreator,
                mutedStatus = mutedStatus,
                mutedTime = muted_time,
                creatorId = creator_id,
                lastNotificationDate = lastNotifiedMessageDate,
                lastModifiedDate = last_modified_date,
                lastReadDate = lastReadDate,
                accessList = access_list,
                accessRoleList = access_role_list,
                protocol = protocol,
                mlsCipherSuite = mls_cipher_suite,
                mlsEpoch = mls_epoch,
                mlsGroupId = mls_group_id,
                mlsLastKeyingMaterialUpdateDate = mls_last_keying_material_update_date,
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
                selfRole = selfRole,
                receiptMode = receipt_mode
            )
        }
    }

    @Suppress("LongParameterList")
    fun mapProtocolInfo(
        protocol: ConversationEntity.Protocol,
        mlsGroupId: String?,
        mlsGroupState: ConversationEntity.GroupState,
        mlsEpoch: Long,
        mlsLastKeyingMaterialUpdate: Instant,
        mlsCipherSuite: ConversationEntity.CipherSuite,
    ): ConversationEntity.ProtocolInfo {
        return when (protocol) {
            ConversationEntity.Protocol.MLS -> ConversationEntity.ProtocolInfo.MLS(
                mlsGroupId ?: "",
                mlsGroupState,
                mlsEpoch.toULong(),
                mlsLastKeyingMaterialUpdate,
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

internal const val MLS_DEFAULT_EPOCH = 0L
internal const val MLS_DEFAULT_LAST_KEY_MATERIAL_UPDATE_MILLI = 0L
internal val MLS_DEFAULT_CIPHER_SUITE = ConversationEntity.CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519

// TODO: Refactor. We can split this into smaller DAOs.
//       For example, one for Members, one for Protocol/MLS-related things, etc.
//       Even if they operate on the same table underneath, these DAOs can represent/do different things.
@Suppress("TooManyFunctions")
class ConversationDAOImpl(
    private val conversationQueries: ConversationsQueries,
    private val userQueries: UsersQueries,
    private val memberQueries: MembersQueries,
    private val coroutineContext: CoroutineContext
) : ConversationDAO {

    private val memberMapper = MemberMapper()
    private val conversationMapper = ConversationMapper()
    override suspend fun getSelfConversationId(protocol: ConversationEntity.Protocol) = withContext(coroutineContext) {
        conversationQueries.selfConversationId(protocol).executeAsOneOrNull()
    }

    override suspend fun insertConversation(conversationEntity: ConversationEntity) = withContext(coroutineContext) {
        nonSuspendingInsertConversation(conversationEntity)
    }

    override suspend fun insertConversations(conversationEntities: List<ConversationEntity>) = withContext(coroutineContext) {
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
                if (protocolInfo is ConversationEntity.ProtocolInfo.MLS) protocolInfo.keyingMaterialLastUpdate
                else Instant.fromEpochMilliseconds(MLS_DEFAULT_LAST_KEY_MATERIAL_UPDATE_MILLI),
                if (protocolInfo is ConversationEntity.ProtocolInfo.MLS) protocolInfo.cipherSuite
                else MLS_DEFAULT_CIPHER_SUITE,
                receiptMode
            )
        }
    }

    override suspend fun updateConversation(conversationEntity: ConversationEntity) = withContext(coroutineContext) {
        conversationQueries.updateConversation(
            conversationEntity.name,
            conversationEntity.type,
            conversationEntity.teamId,
            conversationEntity.id
        )
    }

    override suspend fun updateConversationGroupState(groupState: ConversationEntity.GroupState, groupId: String) =
        withContext(coroutineContext) {
            conversationQueries.updateConversationGroupState(groupState, groupId)
        }

    override suspend fun updateConversationModifiedDate(qualifiedID: QualifiedIDEntity, date: String) = withContext(coroutineContext) {
        conversationQueries.updateConversationModifiedDate(date.toInstant(), qualifiedID)
    }

    override suspend fun updateConversationNotificationDate(qualifiedID: QualifiedIDEntity, date: Instant) = withContext(coroutineContext) {
        conversationQueries.updateConversationNotificationsDate(date, qualifiedID)
    }

    override suspend fun updateAllConversationsNotificationDate(date: Instant) = withContext(coroutineContext) {
        conversationQueries.updateAllUnNotifiedConversationsNotificationsDate(date)
    }

    override suspend fun getAllConversations(): Flow<List<ConversationViewEntity>> {
        return conversationQueries.selectAllConversations()
            .asFlow()
            .mapToList()
            .flowOn(coroutineContext)
            .map { it.map(conversationMapper::toModel) }
    }

    override suspend fun getAllConversationDetails(): Flow<List<ConversationViewEntity>> {
        return conversationQueries.selectAllConversationDetails()
            .asFlow()
            .mapToList()
            .flowOn(coroutineContext)
            .map { list -> list.map { it.let { conversationMapper.toModel(it) } } }
    }

    override suspend fun observeGetConversationByQualifiedID(qualifiedID: QualifiedIDEntity): Flow<ConversationEntity?> {
        return conversationQueries.selectConversationByQualifiedId(qualifiedID, conversationMapper::toModel)
            .asFlow()
            .mapToOneOrNull()
            .flowOn(coroutineContext)
    }

    override suspend fun getConversationByQualifiedID(qualifiedID: QualifiedIDEntity): ConversationViewEntity =
        withContext(coroutineContext) {
            conversationQueries.selectByQualifiedId(qualifiedID).executeAsOne().let {
                conversationMapper.toModel(it)
            }
        }

    override suspend fun observeConversationWithOtherUser(userId: UserIDEntity): Flow<ConversationViewEntity?> {
        return memberQueries.selectConversationByMember(userId)
            .asFlow()
            .mapToOneOrNull()
            .flowOn(coroutineContext)
            .map { it?.let { conversationMapper.fromOneToOneToModel(it) } }
    }

    override suspend fun getConversationProtocolInfo(qualifiedID: QualifiedIDEntity): ConversationEntity.ProtocolInfo =
        conversationQueries.selectProtocolInfoByQualifiedId(qualifiedID, conversationMapper::mapProtocolInfo).executeAsOne()

    override suspend fun getConversationByGroupID(groupID: String): Flow<ConversationViewEntity?> {
        return conversationQueries.selectByGroupId(groupID)
            .asFlow()
            .flowOn(coroutineContext)
            .mapToOneOrNull()
            .map { it?.let { conversationMapper.toModel(it) } }
    }

    override suspend fun getConversationIdByGroupID(groupID: String) = withContext(coroutineContext) {
        conversationQueries.getConversationIdByGroupId(groupID).executeAsOne()
    }

    override suspend fun getConversationsByGroupState(groupState: ConversationEntity.GroupState): List<ConversationViewEntity> =
        withContext(coroutineContext) {
            conversationQueries.selectByGroupState(groupState, ConversationEntity.Protocol.MLS)
                .executeAsList()
                .map(conversationMapper::toModel)
        }

    override suspend fun deleteConversationByQualifiedID(qualifiedID: QualifiedIDEntity) = withContext(coroutineContext) {
        conversationQueries.deleteConversation(qualifiedID)
    }

    override suspend fun insertMember(member: Member, conversationID: QualifiedIDEntity) = withContext(coroutineContext) {
        memberQueries.transaction {
            userQueries.insertOrIgnoreUserId(member.user)
            memberQueries.insertMember(member.user, conversationID, member.role)
        }
    }

    override suspend fun updateMember(member: Member, conversationID: QualifiedIDEntity) = withContext(coroutineContext) {
        memberQueries.updateMemberRole(member.role, member.user, conversationID)
    }

    override suspend fun insertMembersWithQualifiedId(memberList: List<Member>, conversationID: QualifiedIDEntity) =
        withContext(coroutineContext) {
            nonSuspendInsertMembersWithQualifiedId(memberList, conversationID)
        }

    private fun nonSuspendInsertMembersWithQualifiedId(memberList: List<Member>, conversationID: QualifiedIDEntity) =
        memberQueries.transaction {
            for (member: Member in memberList) {
                userQueries.insertOrIgnoreUserId(member.user)
                memberQueries.insertMember(member.user, conversationID, member.role)
            }
        }

    override suspend fun insertMembers(memberList: List<Member>, groupId: String) {
        withContext(coroutineContext) {
            getConversationByGroupID(groupId).firstOrNull()?.let {
                nonSuspendInsertMembersWithQualifiedId(memberList, it.id)
            }
        }
    }

    override suspend fun updateOrInsertOneOnOneMemberWithConnectionStatus(
        member: Member,
        status: ConnectionEntity.State,
        conversationID: QualifiedIDEntity
    ) = withContext(coroutineContext) {
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

    override suspend fun deleteMemberByQualifiedID(userID: QualifiedIDEntity, conversationID: QualifiedIDEntity) =
        withContext(coroutineContext) {
            memberQueries.deleteMember(conversationID, userID)
        }

    override suspend fun deleteMembersByQualifiedID(userIDList: List<QualifiedIDEntity>, conversationID: QualifiedIDEntity) =
        withContext(coroutineContext) {
            nonSuspendDeleteMembersByQualifiedID(userIDList, conversationID)
        }

    private fun nonSuspendDeleteMembersByQualifiedID(userIDList: List<QualifiedIDEntity>, conversationID: QualifiedIDEntity) =
        memberQueries.transaction {
            userIDList.forEach {
                memberQueries.deleteMember(conversationID, it)
            }
        }

    override suspend fun deleteMembersByQualifiedID(userIDList: List<QualifiedIDEntity>, groupId: String) {
        withContext(coroutineContext) {
            getConversationByGroupID(groupId).firstOrNull()?.let {
                nonSuspendDeleteMembersByQualifiedID(userIDList, it.id)
            }
        }
    }

    override suspend fun getAllMembers(qualifiedID: QualifiedIDEntity): Flow<List<Member>> {
        return memberQueries.selectAllMembersByConversation(qualifiedID.value)
            .asFlow()
            .flowOn(coroutineContext)
            .mapToList()
            .map { it.map(memberMapper::toModel) }
    }

    override suspend fun updateConversationMutedStatus(
        conversationId: QualifiedIDEntity,
        mutedStatus: ConversationEntity.MutedStatus,
        mutedStatusTimestamp: Long
    ) = withContext(coroutineContext) {
        conversationQueries.updateConversationMutingStatus(mutedStatus, mutedStatusTimestamp, conversationId)
    }

    override suspend fun getConversationsForNotifications(): Flow<List<ConversationViewEntity>> {
        return conversationQueries.selectConversationsWithUnnotifiedMessages()
            .asFlow()
            .flowOn(coroutineContext)
            .mapToList()
            .map { it.map(conversationMapper::toModel) }
    }

    override suspend fun updateAccess(
        conversationID: QualifiedIDEntity,
        accessList: List<ConversationEntity.Access>,
        accessRoleList: List<ConversationEntity.AccessRole>
    ) = withContext(coroutineContext) {
        conversationQueries.updateAccess(accessList, accessRoleList, conversationID)
    }

    override suspend fun updateConversationReadDate(conversationID: QualifiedIDEntity, date: String) = withContext(coroutineContext) {
        conversationQueries.updateConversationReadDate(date.toInstant(), conversationID)
    }

    override suspend fun updateConversationMemberRole(conversationId: QualifiedIDEntity, userId: UserIDEntity, role: Member.Role) =
        withContext(coroutineContext) {
            memberQueries.updateMemberRole(role, userId, conversationId)
        }

    override suspend fun updateKeyingMaterial(groupId: String, timestamp: Instant) = withContext(coroutineContext) {
        conversationQueries.updateKeyingMaterialDate(timestamp, groupId)
    }

    override suspend fun getConversationsByKeyingMaterialUpdate(threshold: Duration): List<String> = withContext(coroutineContext) {
        conversationQueries.selectByKeyingMaterialUpdate(
            ConversationEntity.GroupState.ESTABLISHED,
            ConversationEntity.Protocol.MLS,
            DateTimeUtil.currentInstant().minus(threshold)
        ).executeAsList()
    }

    override suspend fun setProposalTimer(proposalTimer: ProposalTimerEntity) = withContext(coroutineContext) {
        conversationQueries.updateProposalTimer(proposalTimer.firingDate.toString(), proposalTimer.groupID)
    }

    override suspend fun clearProposalTimer(groupID: String) = withContext(coroutineContext) {
        conversationQueries.clearProposalTimer(groupID)
    }

    override suspend fun getProposalTimers(): Flow<List<ProposalTimerEntity>> {
        return conversationQueries.selectProposalTimers(ConversationEntity.Protocol.MLS)
            .asFlow()
            .flowOn(coroutineContext)
            .mapToList()
            .map { list -> list.map { ProposalTimerEntity(it.mls_group_id, it.mls_proposal_timer.toInstant()) } }
    }

    override suspend fun observeIsUserMember(conversationId: QualifiedIDEntity, userId: UserIDEntity): Flow<Boolean> =
        conversationQueries.isUserMember(conversationId, userId)
            .asFlow()
            .flowOn(coroutineContext)
            .mapToOneOrNull()
            .map { it != null }

    override suspend fun whoDeletedMeInConversation(conversationId: QualifiedIDEntity, selfUserIdString: String): UserIDEntity? =
        withContext(coroutineContext) {
            conversationQueries.whoDeletedMeInConversation(conversationId, selfUserIdString).executeAsOneOrNull()
        }

    override suspend fun updateConversationName(conversationId: QualifiedIDEntity, conversationName: String, timestamp: String) =
        withContext(coroutineContext) {
            conversationQueries.updateConversationName(conversationName, timestamp.toInstant(), conversationId)
        }

    override suspend fun updateConversationType(conversationID: QualifiedIDEntity, type: ConversationEntity.Type) =
        withContext(coroutineContext) {
            conversationQueries.updateConversationType(type, conversationID)
        }

    override suspend fun revokeOneOnOneConversationsWithDeletedUser(userId: UserIDEntity) = withContext(coroutineContext) {
        memberQueries.deleteUserFromGroupConversations(userId, userId)
    }

    override suspend fun getConversationIdsByUserId(userId: UserIDEntity): List<QualifiedIDEntity> = withContext(coroutineContext) {
        memberQueries.selectConversationsByMember(userId).executeAsList().map { it.conversation }
    }

    override suspend fun updateConversationReceiptMode(conversationID: QualifiedIDEntity, receiptMode: ConversationEntity.ReceiptMode) =
        withContext(coroutineContext) {
            conversationQueries.updateConversationReceiptMode(receiptMode, conversationID)
        }

}
