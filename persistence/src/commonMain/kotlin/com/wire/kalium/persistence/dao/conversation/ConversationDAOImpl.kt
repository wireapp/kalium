/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
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

package com.wire.kalium.persistence.dao.conversation

import app.cash.sqldelight.coroutines.asFlow
import com.wire.kalium.persistence.ConversationsQueries
import com.wire.kalium.persistence.ConversationDetailsQueries
import com.wire.kalium.persistence.ConversationDetailsWithEventsQueries
import com.wire.kalium.persistence.MembersQueries
import com.wire.kalium.persistence.UnreadEventsQueries
import com.wire.kalium.persistence.cache.FlowCache
import com.wire.kalium.persistence.dao.ConversationIDEntity
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.util.mapToList
import com.wire.kalium.persistence.util.mapToOne
import com.wire.kalium.persistence.util.mapToOneOrDefault
import com.wire.kalium.persistence.util.mapToOneOrNull
import com.wire.kalium.util.DateTimeUtil
import com.wire.kalium.util.DateTimeUtil.toIsoDateTimeString
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.datetime.toInstant
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration

internal const val MLS_DEFAULT_EPOCH = 0L
internal const val MLS_DEFAULT_LAST_KEY_MATERIAL_UPDATE_MILLI = 0L
internal val MLS_DEFAULT_CIPHER_SUITE = ConversationEntity.CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519

// TODO: Refactor. We can split this into smaller DAOs.
//       For example, one for Members, one for Protocol/MLS-related things, etc.
//       Even if they operate on the same table underneath, these DAOs can represent/do different things.
@Suppress("TooManyFunctions", "LongParameterList")
internal class ConversationDAOImpl internal constructor(
    private val conversationDetailsCache: FlowCache<ConversationIDEntity, ConversationViewEntity?>,
    private val conversationCache: FlowCache<ConversationIDEntity, ConversationEntity?>,
    private val conversationQueries: ConversationsQueries,
    private val conversationDetailsQueries: ConversationDetailsQueries,
    private val conversationDetailsWithEventsQueries: ConversationDetailsWithEventsQueries,
    private val memberQueries: MembersQueries,
    private val unreadEventsQueries: UnreadEventsQueries,
    private val coroutineContext: CoroutineContext,
) : ConversationDAO {
    private val conversationMapper = ConversationMapper
    private val conversationDetailsWithEventsMapper = ConversationDetailsWithEventsMapper
    override val platformExtensions: ConversationExtensions =
        ConversationExtensionsImpl(conversationDetailsWithEventsQueries, conversationDetailsWithEventsMapper, coroutineContext)

    // region Get/Observe by ID

    override suspend fun observeConversationById(
        qualifiedID: QualifiedIDEntity
    ): Flow<ConversationEntity?> {
        return conversationCache.get(qualifiedID) {
            conversationQueries.selectConversationByQualifiedId(qualifiedID, conversationMapper::fromViewToModel)
                .asFlow()
                .mapToOneOrNull()
        }
    }

    override suspend fun getConversationById(
        qualifiedID: QualifiedIDEntity
    ): ConversationEntity? = observeConversationById(qualifiedID).first()

    override suspend fun observeConversationDetailsById(
        conversationId: QualifiedIDEntity
    ): Flow<ConversationViewEntity?> = conversationDetailsCache.get(conversationId) {
        conversationDetailsQueries.selectConversationDetailsByQualifiedId(conversationId, conversationMapper::fromViewToModel)
            .asFlow()
            .mapToOneOrNull()
    }

    override suspend fun getConversationDetailsById(
        qualifiedID: QualifiedIDEntity
    ): ConversationViewEntity? =
        observeConversationDetailsById(qualifiedID).first()

    // endregion

    override suspend fun getSelfConversationId(protocol: ConversationEntity.Protocol) = withContext(coroutineContext) {
        conversationQueries.selfConversationId(protocol).executeAsOneOrNull()
    }

    override suspend fun getE2EIConversationClientInfoByClientId(clientId: String): E2EIConversationClientInfoEntity? =
        withContext(coroutineContext) {
            conversationQueries.getMLSGroupIdAndUserIdByClientId(clientId, conversationMapper::toE2EIConversationClient)
                .executeAsOneOrNull()
        }

    override suspend fun getMLSGroupIdByUserId(userId: UserIDEntity): String? =
        withContext(coroutineContext) {
            conversationQueries.getMLSGroupIdByUserId(userId)
                .executeAsOneOrNull()
        }

    override suspend fun getMLSGroupIdByConversationId(conversationId: QualifiedIDEntity): String? =
        withContext(coroutineContext) {
            conversationQueries.getMLSGroupIdByConversationId(conversationId)
                .executeAsOneOrNull()
                ?.mls_group_id
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
                if (protocolInfo is ConversationEntity.ProtocolInfo.MLSCapable) protocolInfo.groupId
                else null,
                if (protocolInfo is ConversationEntity.ProtocolInfo.MLSCapable) protocolInfo.groupState
                else ConversationEntity.GroupState.ESTABLISHED,
                if (protocolInfo is ConversationEntity.ProtocolInfo.MLSCapable) protocolInfo.epoch.toLong()
                else MLS_DEFAULT_EPOCH,
                when (protocolInfo) {
                    is ConversationEntity.ProtocolInfo.MLS -> ConversationEntity.Protocol.MLS
                    is ConversationEntity.ProtocolInfo.Mixed -> ConversationEntity.Protocol.MIXED
                    is ConversationEntity.ProtocolInfo.Proteus -> ConversationEntity.Protocol.PROTEUS
                },
                mutedStatus,
                mutedTime,
                creatorId,
                lastModifiedDate,
                lastNotificationDate,
                access,
                accessRole,
                lastReadDate,
                if (protocolInfo is ConversationEntity.ProtocolInfo.MLSCapable) protocolInfo.keyingMaterialLastUpdate
                else Instant.fromEpochMilliseconds(MLS_DEFAULT_LAST_KEY_MATERIAL_UPDATE_MILLI),
                if (protocolInfo is ConversationEntity.ProtocolInfo.MLSCapable) protocolInfo.cipherSuite
                else MLS_DEFAULT_CIPHER_SUITE,
                receiptMode,
                messageTimer,
                userMessageTimer,
                hasIncompleteMetadata,
                archived,
                archivedInstant,
                isChannel
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

    override suspend fun updateMlsGroupStateAndCipherSuite(
        groupState: ConversationEntity.GroupState,
        cipherSuite: ConversationEntity.CipherSuite,
        groupId: String
    ) = withContext(coroutineContext) {
        conversationQueries.updateMlsGroupStateAndCipherSuite(groupState, cipherSuite, groupId)
    }

    override suspend fun updateConversationModifiedDate(qualifiedID: QualifiedIDEntity, date: Instant) = withContext(coroutineContext) {
        conversationQueries.updateConversationModifiedDate(date, qualifiedID)
    }

    override suspend fun updateConversationNotificationDate(qualifiedID: QualifiedIDEntity) = withContext(coroutineContext) {
        conversationQueries.updateConversationNotificationsDateWithTheLastMessage(qualifiedID)
    }

    override suspend fun updateAllConversationsNotificationDate() = withContext(coroutineContext) {
        conversationQueries.updateAllNotifiedConversationsNotificationsDate()
    }

    override suspend fun getAllConversations(): Flow<List<ConversationEntity>> {
        return conversationQueries.selectAllConversations(conversationMapper::fromViewToModel)
            .asFlow()
            .mapToList()
            .flowOn(coroutineContext)
    }

    override suspend fun getAllConversationDetails(
        fromArchive: Boolean,
        filter: ConversationFilterEntity
    ): Flow<List<ConversationViewEntity>> {
        return conversationDetailsQueries.selectAllConversationDetails(fromArchive, filter.toString(), conversationMapper::fromViewToModel)
            .asFlow()
            .mapToList()
            .flowOn(coroutineContext)
    }

    override suspend fun getAllConversationDetailsWithEvents(
        fromArchive: Boolean,
        onlyInteractionEnabled: Boolean,
        newActivitiesOnTop: Boolean,
    ): Flow<List<ConversationDetailsWithEventsEntity>> {
        return conversationDetailsWithEventsQueries.selectAllConversationDetailsWithEvents(
            fromArchive = fromArchive,
            onlyInteractionsEnabled = onlyInteractionEnabled,
            newActivitiesOnTop = newActivitiesOnTop,
            mapper = conversationDetailsWithEventsMapper::fromViewToModel
        ).asFlow()
            .mapToList()
            .flowOn(coroutineContext)
    }

    override suspend fun setWireCell(conversationId: QualifiedIDEntity, wireCell: String?) {
        conversationQueries.updateWireCell(wireCell, conversationId)
    }

    override suspend fun getCellName(conversationId: QualifiedIDEntity): String? =
        conversationQueries.getCellName(conversationId).executeAsOneOrNull()?.wire_cell

    override suspend fun getConversationIds(
        type: ConversationEntity.Type,
        protocol: ConversationEntity.Protocol,
        teamId: String?
    ): List<QualifiedIDEntity> {
        return withContext(coroutineContext) {
            conversationQueries.selectConversationIds(protocol, type, teamId).executeAsList()
        }
    }

    override suspend fun getConversationTypeById(conversationId: QualifiedIDEntity): ConversationEntity.Type? =
        withContext(coroutineContext) {
            conversationQueries.getConversationTypeById(conversationId).executeAsOneOrNull()
        }

    override suspend fun getTeamConversationIdsReadyToCompleteMigration(teamId: String): List<QualifiedIDEntity> {
        return withContext(coroutineContext) {
            conversationQueries.selectAllTeamProteusConversationsReadyForMigration(teamId)
                .executeAsList()
                .map { it.qualified_id }
        }
    }

    override suspend fun getOneOnOneConversationIdsWithOtherUser(
        userId: UserIDEntity,
        protocol: ConversationEntity.Protocol
    ): List<QualifiedIDEntity> =
        withContext(coroutineContext) {
            conversationQueries.selectOneOnOneConversationIdsByProtocol(protocol, userId).executeAsList()
        }

    override suspend fun observeOneOnOneConversationWithOtherUser(userId: UserIDEntity): Flow<ConversationEntity?> {
        return conversationQueries.selectActiveOneOnOneConversation(userId, conversationMapper::fromViewToModel)
            .asFlow()
            .mapToOneOrNull()
            .flowOn(coroutineContext)
    }

    override suspend fun observeOneOnOneConversationDetailsWithOtherUser(userId: UserIDEntity): Flow<ConversationViewEntity?> {
        return conversationDetailsQueries.selectActiveOneOnOneConversationDetails(userId, conversationMapper::fromViewToModel)
            .asFlow()
            .mapToOneOrNull()
            .flowOn(coroutineContext)
    }

    override suspend fun getConversationProtocolInfo(qualifiedID: QualifiedIDEntity): ConversationEntity.ProtocolInfo? =
        withContext(coroutineContext) {
            conversationQueries.selectProtocolInfoByQualifiedId(qualifiedID, conversationMapper::mapProtocolInfo).executeAsOneOrNull()
        }

    override suspend fun observeConversationDetailsByGroupID(groupID: String): Flow<ConversationViewEntity?> {
        return conversationDetailsQueries.selectConversationDetailsByGroupId(groupID, conversationMapper::fromViewToModel)
            .asFlow()
            .flowOn(coroutineContext)
            .mapToOneOrNull()
    }

    override suspend fun getConversationDetailsByGroupID(groupID: String): ConversationViewEntity? {
        return conversationDetailsQueries.selectConversationDetailsByGroupId(groupID, conversationMapper::fromViewToModel)
            .executeAsOneOrNull()
    }

    override suspend fun getConversationIdByGroupID(groupID: String) = withContext(coroutineContext) {
        conversationQueries.getConversationIdByGroupId(groupID).executeAsOneOrNull()
    }

    override suspend fun getConversationsByGroupState(groupState: ConversationEntity.GroupState): List<ConversationEntity> =
        withContext(coroutineContext) {
            conversationQueries.selectByGroupState(groupState, conversationMapper::fromViewToModel)
                .executeAsList()
        }

    override suspend fun deleteConversationByQualifiedID(qualifiedID: QualifiedIDEntity) = withContext(coroutineContext) {
        conversationQueries.deleteConversation(qualifiedID)
    }

    override suspend fun updateConversationMutedStatus(
        conversationId: QualifiedIDEntity,
        mutedStatus: ConversationEntity.MutedStatus,
        mutedStatusTimestamp: Long
    ) = withContext(coroutineContext) {
        conversationQueries.updateConversationMutingStatus(mutedStatus, mutedStatusTimestamp, conversationId)
    }

    override suspend fun updateConversationArchivedStatus(
        conversationId: QualifiedIDEntity,
        isArchived: Boolean,
        archivedStatusTimestamp: Long
    ) = withContext(coroutineContext) {
        conversationQueries.updateConversationArchivingStatus(
            isArchived,
            archivedStatusTimestamp.toIsoDateTimeString().toInstant(),
            conversationId
        )
    }

    override suspend fun updateAccess(
        conversationID: QualifiedIDEntity,
        accessList: List<ConversationEntity.Access>,
        accessRoleList: List<ConversationEntity.AccessRole>
    ) = withContext(coroutineContext) {
        conversationQueries.updateAccess(accessList, accessRoleList, conversationID)
    }

    override suspend fun updateConversationReadDate(conversationID: QualifiedIDEntity, date: Instant) = withContext(coroutineContext) {
        unreadEventsQueries.deleteUnreadEvents(date, conversationID)
        conversationQueries.updateConversationReadDate(date, conversationID)
    }

    override suspend fun updateKeyingMaterial(groupId: String, timestamp: Instant) = withContext(coroutineContext) {
        conversationQueries.updateKeyingMaterialDate(timestamp, groupId)
    }

    override suspend fun getConversationsByKeyingMaterialUpdate(threshold: Duration): List<String> = withContext(coroutineContext) {
        conversationQueries.selectByKeyingMaterialUpdate(
            ConversationEntity.GroupState.ESTABLISHED,
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
        return conversationQueries.selectProposalTimers()
            .asFlow()
            .flowOn(coroutineContext)
            .mapToList()
            .map { list -> list.map { ProposalTimerEntity(it.mls_group_id, it.mls_proposal_timer.toInstant()) } }
    }

    override suspend fun whoDeletedMeInConversation(conversationId: QualifiedIDEntity, selfUserIdString: String): UserIDEntity? =
        withContext(coroutineContext) {
            conversationQueries.whoDeletedMeInConversation(conversationId, selfUserIdString).executeAsOneOrNull()
        }

    override suspend fun updateConversationName(conversationId: QualifiedIDEntity, conversationName: String, dateTime: Instant) =
        withContext(coroutineContext) {
            conversationQueries.updateConversationName(conversationName, dateTime, conversationId)
        }

    override suspend fun updateConversationType(conversationID: QualifiedIDEntity, type: ConversationEntity.Type) =
        withContext(coroutineContext) {
            conversationQueries.updateConversationType(type, conversationID)
        }

    override suspend fun updateConversationProtocolAndCipherSuite(
        conversationId: QualifiedIDEntity,
        groupID: String?,
        protocol: ConversationEntity.Protocol,
        cipherSuite: ConversationEntity.CipherSuite
    ): Boolean {
        return withContext(coroutineContext) {
            conversationQueries.updateConversationGroupIdAndProtocolInfo(
                groupID,
                protocol,
                cipherSuite,
                conversationId
            ).executeAsOne() > 0
        }
    }

    override suspend fun getConversationsByUserId(userId: UserIDEntity): List<ConversationEntity> = withContext(coroutineContext) {
        memberQueries.selectConversationsByMember(userId, conversationMapper::fromViewToModel).executeAsList()
    }

    override suspend fun updateConversationReceiptMode(conversationID: QualifiedIDEntity, receiptMode: ConversationEntity.ReceiptMode) =
        withContext(coroutineContext) {
            conversationQueries.updateConversationReceiptMode(receiptMode, conversationID)
        }

    override suspend fun updateGuestRoomLink(
        conversationId: QualifiedIDEntity,
        link: String,
        isPasswordProtected: Boolean
    ) = withContext(coroutineContext) {
        conversationQueries.updateGuestRoomLink(link, isPasswordProtected, conversationId)
    }

    override suspend fun deleteGuestRoomLink(conversationId: QualifiedIDEntity) = withContext(coroutineContext) {
        conversationQueries.updateGuestRoomLink(null, false, conversationId)
    }

    override suspend fun observeGuestRoomLinkByConversationId(conversationId: QualifiedIDEntity): Flow<ConversationGuestLinkEntity?> =
        conversationQueries.getGuestRoomLinkByConversationId(conversationId).asFlow().mapToOneOrNull().map {
            it?.guest_room_link?.let { link -> ConversationGuestLinkEntity(link, it.is_guest_password_protected) }
        }.flowOn(coroutineContext)

    override suspend fun updateMessageTimer(conversationId: QualifiedIDEntity, messageTimer: Long?) = withContext(coroutineContext) {
        conversationQueries.updateMessageTimer(messageTimer, conversationId)
    }

    override suspend fun updateUserMessageTimer(conversationId: QualifiedIDEntity, messageTimer: Long?) = withContext(coroutineContext) {
        conversationQueries.updateUserMessageTimer(messageTimer, conversationId)
    }

    override suspend fun getConversationsWithoutMetadata(): List<QualifiedIDEntity> = withContext(coroutineContext) {
        conversationQueries.selectConversationIdsWithoutMetadata().executeAsList()
    }

    override suspend fun updateDegradedConversationNotifiedFlag(conversationId: QualifiedIDEntity, updateFlag: Boolean) =
        withContext(coroutineContext) {
            conversationQueries.updateDegradedConversationNotifiedFlag(updateFlag, conversationId)
        }

    override suspend fun observeDegradedConversationNotified(conversationId: QualifiedIDEntity): Flow<Boolean> =
        conversationQueries.selectDegradedConversationNotified(conversationId)
            .asFlow()
            .mapToOneOrDefault(true)
            .flowOn(coroutineContext)

    override suspend fun clearContent(conversationId: QualifiedIDEntity) = withContext(coroutineContext) {
        conversationQueries.clearContent(conversationId)
    }

    override suspend fun updateMlsVerificationStatus(
        verificationStatus: ConversationEntity.VerificationStatus,
        conversationId: QualifiedIDEntity
    ) = withContext(coroutineContext) {
        conversationQueries.updateMlsVerificationStatus(verificationStatus, conversationId)
    }

    override suspend fun observeUnreadArchivedConversationsCount(): Flow<Long> =
        unreadEventsQueries.getUnreadArchivedConversationsCount().asFlow().mapToOne()

    override suspend fun updateLegalHoldStatus(
        conversationId: QualifiedIDEntity,
        legalHoldStatus: ConversationEntity.LegalHoldStatus
    ) = withContext(coroutineContext) {
        conversationQueries.transactionWithResult {
            conversationQueries.updateLegalHoldStatus(legalHoldStatus, conversationId)
            conversationQueries.selectChanges().executeAsOne() > 0
        }

    }

    override suspend fun updateLegalHoldStatusChangeNotified(conversationId: QualifiedIDEntity, notified: Boolean) =
        withContext(coroutineContext) {
            conversationQueries.transactionWithResult {
                conversationQueries.upsertLegalHoldStatusChangeNotified(conversationId, notified)
                conversationQueries.selectChanges().executeAsOne() > 0
            }
        }

    override suspend fun observeLegalHoldStatus(conversationId: QualifiedIDEntity) =
        conversationQueries.selectLegalHoldStatus(conversationId)
            .asFlow()
            .mapToOneOrDefault(ConversationEntity.LegalHoldStatus.DISABLED)
            .flowOn(coroutineContext)

    override suspend fun observeLegalHoldStatusChangeNotified(conversationId: QualifiedIDEntity) =
        conversationQueries.selectLegalHoldStatusChangeNotified(conversationId)
            .asFlow()
            .mapToOneOrDefault(true)
            .flowOn(coroutineContext)

    override suspend fun getEstablishedSelfMLSGroupId(): String? =
        withContext(coroutineContext) {
            conversationQueries
                .getEstablishedSelfMLSGroupId()
                .executeAsOneOrNull()
                ?.mls_group_id
        }

    override suspend fun selectGroupStatusMembersNamesAndHandles(groupID: String): EpochChangesDataEntity? = withContext(coroutineContext) {
        conversationQueries.transactionWithResult {
            val (conversationId, mlsVerificationStatus) = conversationQueries.conversationIDByGroupId(groupID).executeAsOneOrNull()
                ?: return@transactionWithResult null
            memberQueries.selectMembersNamesAndHandle(conversationId).executeAsList()
                .let { members ->
                    val membersMap = members.associate { it.user to NameAndHandleEntity(it.name, it.handle) }
                    EpochChangesDataEntity(
                        conversationId,
                        mlsVerificationStatus,
                        membersMap
                    )
                }
        }
    }

    override suspend fun isAChannel(conversationId: QualifiedIDEntity): Boolean = withContext(coroutineContext) {
        conversationQueries.selectIsChannel(conversationId).executeAsOneOrNull() ?: false
    }
}
