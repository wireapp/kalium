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

package com.wire.kalium.persistence.dao.conversation

import app.cash.sqldelight.coroutines.asFlow
import com.wire.kalium.persistence.ConversationsQueries
import com.wire.kalium.persistence.MembersQueries
import com.wire.kalium.persistence.UnreadEventsQueries
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.util.mapToList
import com.wire.kalium.persistence.util.mapToOneOrNull
import com.wire.kalium.util.DateTimeUtil
import kotlinx.coroutines.flow.Flow
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
@Suppress("TooManyFunctions")
internal class ConversationDAOImpl internal constructor(
    private val conversationQueries: ConversationsQueries,
    private val memberQueries: MembersQueries,
    private val unreadEventsQueries: UnreadEventsQueries,
    private val coroutineContext: CoroutineContext
) : ConversationDAO {

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
                hasIncompleteMetadata
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

    override suspend fun updateConversationModifiedDate(qualifiedID: QualifiedIDEntity, date: Instant) = withContext(coroutineContext) {
        conversationQueries.updateConversationModifiedDate(date, qualifiedID)
    }

    override suspend fun updateConversationNotificationDate(qualifiedID: QualifiedIDEntity) = withContext(coroutineContext) {
        conversationQueries.updateConversationNotificationsDateWithTheLastMessage(qualifiedID)
    }

    override suspend fun updateAllConversationsNotificationDate() = withContext(coroutineContext) {
        conversationQueries.updateAllNotifiedConversationsNotificationsDate()
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

    override suspend fun getAllProteusTeamConversations(teamId: String): List<QualifiedIDEntity> {
        return withContext(coroutineContext) {
            conversationQueries.selectAllTeamProteusConversations(teamId)
                .executeAsList()
        }
    }

    override suspend fun getAllProteusTeamConversationsReadyToBeFinalised(teamId: String): List<QualifiedIDEntity> {
        return withContext(coroutineContext) {
            conversationQueries.selectAllTeamProteusConversationsReadyForMigration(teamId)
                .executeAsList()
                .map { it.qualified_id }
        }
    }

    override suspend fun observeGetConversationByQualifiedID(qualifiedID: QualifiedIDEntity): Flow<ConversationViewEntity?> {
        return conversationQueries.selectByQualifiedId(qualifiedID)
            .asFlow()
            .mapToOneOrNull()
            .flowOn(coroutineContext)
            .map { it?.let { conversationMapper.toModel(it) } }
    }

    override suspend fun observeGetConversationBaseInfoByQualifiedID(qualifiedID: QualifiedIDEntity): Flow<ConversationEntity?> {
        return conversationQueries.selectConversationByQualifiedId(qualifiedID, conversationMapper::toModel)
            .asFlow()
            .mapToOneOrNull()
            .flowOn(coroutineContext)
    }

    // todo: find a better naming for views vs tables queries
    override suspend fun getConversationBaseInfoByQualifiedID(qualifiedID: QualifiedIDEntity): ConversationEntity? =
        withContext(coroutineContext) {
            conversationQueries.selectConversationByQualifiedId(qualifiedID, conversationMapper::toModel).executeAsOneOrNull()
        }

    override suspend fun getConversationByQualifiedID(qualifiedID: QualifiedIDEntity): ConversationViewEntity? =
        withContext(coroutineContext) {
            conversationQueries.selectByQualifiedId(qualifiedID).executeAsOneOrNull()?.let {
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

    override suspend fun getConversationProtocolInfo(qualifiedID: QualifiedIDEntity): ConversationEntity.ProtocolInfo? =
        withContext(coroutineContext) {
            conversationQueries.selectProtocolInfoByQualifiedId(qualifiedID, conversationMapper::mapProtocolInfo).executeAsOneOrNull()
        }

    override suspend fun getConversationByGroupID(groupID: String): Flow<ConversationViewEntity?> {
        return conversationQueries.selectByGroupId(groupID)
            .asFlow()
            .flowOn(coroutineContext)
            .mapToOneOrNull()
            .map { it?.let { conversationMapper.toModel(it) } }
    }

    override suspend fun getConversationIdByGroupID(groupID: String) = withContext(coroutineContext) {
        conversationQueries.getConversationIdByGroupId(groupID).executeAsOneOrNull()
    }

    override suspend fun getConversationsByGroupState(groupState: ConversationEntity.GroupState): List<ConversationViewEntity> =
        withContext(coroutineContext) {
            conversationQueries.selectByGroupState(groupState)
                .executeAsList()
                .map(conversationMapper::toModel)
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

    override suspend fun updateConversationName(conversationId: QualifiedIDEntity, conversationName: String, timestamp: String) =
        withContext(coroutineContext) {
            conversationQueries.updateConversationName(conversationName, timestamp.toInstant(), conversationId)
        }

    override suspend fun updateConversationType(conversationID: QualifiedIDEntity, type: ConversationEntity.Type) =
        withContext(coroutineContext) {
            conversationQueries.updateConversationType(type, conversationID)
        }

    override suspend fun updateConversationProtocol(conversationId: QualifiedIDEntity, protocol: ConversationEntity.Protocol) {
        withContext(coroutineContext) {
            conversationQueries.updateConversationProtocol(protocol, conversationId)
        }
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

    override suspend fun updateGuestRoomLink(conversationId: QualifiedIDEntity, link: String?) = withContext(coroutineContext) {
        conversationQueries.updateGuestRoomLink(link, conversationId)
    }

    override suspend fun observeGuestRoomLinkByConversationId(conversationId: QualifiedIDEntity): Flow<String?> =
        conversationQueries.getGuestRoomLinkByConversationId(conversationId).asFlow().map {
            it.executeAsOne().guest_room_link
        }.flowOn(coroutineContext)

    override suspend fun updateMessageTimer(conversationId: QualifiedIDEntity, messageTimer: Long?) = withContext(coroutineContext) {
        val previousTimer = conversationQueries.getMessageTimer(conversationId).executeAsOneOrNull()?.message_timer
        val updated = previousTimer != messageTimer
        if (updated) {
            conversationQueries.updateMessageTimer(messageTimer, conversationId)
        }
        updated
    }

    override suspend fun updateUserMessageTimer(conversationId: QualifiedIDEntity, messageTimer: Long?) = withContext(coroutineContext) {
        conversationQueries.updateUserMessageTimer(messageTimer, conversationId)
    }

    override suspend fun getConversationsWithoutMetadata(): List<QualifiedIDEntity> = withContext(coroutineContext) {
        conversationQueries.selectConversationIdsWithoutMetadata().executeAsList()
    }

    override suspend fun clearContent(conversationId: QualifiedIDEntity) = withContext(coroutineContext) {
        conversationQueries.clearContent(conversationId)
    }
}
