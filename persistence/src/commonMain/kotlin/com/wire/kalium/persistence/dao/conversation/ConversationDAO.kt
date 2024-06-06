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

import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.UserIDEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant
import kotlin.time.Duration

data class ProposalTimerEntity(
    val groupID: String,
    val firingDate: Instant
)

interface ConversationDAO {
    suspend fun getSelfConversationId(protocol: ConversationEntity.Protocol): QualifiedIDEntity?
    suspend fun getE2EIConversationClientInfoByClientId(clientId: String): E2EIConversationClientInfoEntity?
    suspend fun insertConversation(conversationEntity: ConversationEntity)
    suspend fun insertConversations(conversationEntities: List<ConversationEntity>)
    suspend fun updateConversation(conversationEntity: ConversationEntity)
    suspend fun updateConversationGroupState(groupState: ConversationEntity.GroupState, groupId: String)
    suspend fun updateMlsGroupStateAndCipherSuite(
        groupState: ConversationEntity.GroupState,
        cipherSuite: ConversationEntity.CipherSuite,
        groupId: String
    )
    suspend fun updateConversationModifiedDate(qualifiedID: QualifiedIDEntity, date: Instant)
    suspend fun updateConversationNotificationDate(qualifiedID: QualifiedIDEntity)
    suspend fun updateConversationReadDate(conversationID: QualifiedIDEntity, date: Instant)
    suspend fun updateAllConversationsNotificationDate()
    suspend fun getAllConversations(): Flow<List<ConversationViewEntity>>
    suspend fun getAllConversationDetails(fromArchive: Boolean): Flow<List<ConversationViewEntity>>
    suspend fun getConversationIds(
        type: ConversationEntity.Type,
        protocol: ConversationEntity.Protocol,
        teamId: String? = null
    ): List<QualifiedIDEntity>

    suspend fun getTeamConversationIdsReadyToCompleteMigration(teamId: String): List<QualifiedIDEntity>
    suspend fun observeGetConversationByQualifiedID(qualifiedID: QualifiedIDEntity): Flow<ConversationViewEntity?>
    suspend fun observeGetConversationBaseInfoByQualifiedID(qualifiedID: QualifiedIDEntity): Flow<ConversationEntity?>
    suspend fun getConversationBaseInfoByQualifiedID(qualifiedID: QualifiedIDEntity): ConversationEntity?
    suspend fun getConversationByQualifiedID(qualifiedID: QualifiedIDEntity): ConversationViewEntity?
    suspend fun getOneOnOneConversationIdsWithOtherUser(
        userId: UserIDEntity,
        protocol: ConversationEntity.Protocol
    ): List<QualifiedIDEntity>

    suspend fun observeOneOnOneConversationWithOtherUser(userId: UserIDEntity): Flow<ConversationViewEntity?>
    suspend fun getConversationProtocolInfo(qualifiedID: QualifiedIDEntity): ConversationEntity.ProtocolInfo?
    suspend fun observeConversationByGroupID(groupID: String): Flow<ConversationViewEntity?>
    suspend fun getConversationIdByGroupID(groupID: String): QualifiedIDEntity?
    suspend fun getConversationsByGroupState(groupState: ConversationEntity.GroupState): List<ConversationViewEntity>
    suspend fun deleteConversationByQualifiedID(qualifiedID: QualifiedIDEntity)

    suspend fun updateConversationMutedStatus(
        conversationId: QualifiedIDEntity,
        mutedStatus: ConversationEntity.MutedStatus,
        mutedStatusTimestamp: Long
    )

    suspend fun updateConversationArchivedStatus(
        conversationId: QualifiedIDEntity,
        isArchived: Boolean,
        archivedStatusTimestamp: Long
    )

    suspend fun updateAccess(
        conversationID: QualifiedIDEntity,
        accessList: List<ConversationEntity.Access>,
        accessRoleList: List<ConversationEntity.AccessRole>
    )

    suspend fun updateKeyingMaterial(groupId: String, timestamp: Instant)
    suspend fun getConversationsByKeyingMaterialUpdate(threshold: Duration): List<String>
    suspend fun setProposalTimer(proposalTimer: ProposalTimerEntity)
    suspend fun clearProposalTimer(groupID: String)
    suspend fun getProposalTimers(): Flow<List<ProposalTimerEntity>>
    suspend fun whoDeletedMeInConversation(conversationId: QualifiedIDEntity, selfUserIdString: String): UserIDEntity?
    suspend fun updateConversationName(conversationId: QualifiedIDEntity, conversationName: String, timestamp: String)
    suspend fun updateConversationType(conversationID: QualifiedIDEntity, type: ConversationEntity.Type)
    suspend fun updateConversationProtocolAndCipherSuite(
        conversationId: QualifiedIDEntity,
        groupID: String?,
        protocol: ConversationEntity.Protocol,
        cipherSuite: ConversationEntity.CipherSuite
    ): Boolean

    suspend fun getConversationsByUserId(userId: UserIDEntity): List<ConversationEntity>
    suspend fun updateConversationReceiptMode(conversationID: QualifiedIDEntity, receiptMode: ConversationEntity.ReceiptMode)
    suspend fun updateGuestRoomLink(
        conversationId: QualifiedIDEntity,
        link: String,
        isPasswordProtected: Boolean
    )

    suspend fun deleteGuestRoomLink(conversationId: QualifiedIDEntity)

    suspend fun observeGuestRoomLinkByConversationId(conversationId: QualifiedIDEntity): Flow<ConversationGuestLinkEntity?>
    suspend fun updateMessageTimer(conversationId: QualifiedIDEntity, messageTimer: Long?)
    suspend fun updateUserMessageTimer(conversationId: QualifiedIDEntity, messageTimer: Long?)
    suspend fun getConversationsWithoutMetadata(): List<QualifiedIDEntity>
    suspend fun clearContent(conversationId: QualifiedIDEntity)
    suspend fun updateMlsVerificationStatus(verificationStatus: ConversationEntity.VerificationStatus, conversationId: QualifiedIDEntity)
    suspend fun getConversationByGroupID(groupID: String): ConversationViewEntity?
    suspend fun observeUnreadArchivedConversationsCount(): Flow<Long>
    suspend fun observeDegradedConversationNotified(conversationId: QualifiedIDEntity): Flow<Boolean>
    suspend fun updateDegradedConversationNotifiedFlag(conversationId: QualifiedIDEntity, updateFlag: Boolean)
    suspend fun updateLegalHoldStatus(conversationId: QualifiedIDEntity, legalHoldStatus: ConversationEntity.LegalHoldStatus): Boolean
    suspend fun updateLegalHoldStatusChangeNotified(conversationId: QualifiedIDEntity, notified: Boolean): Boolean
    suspend fun observeLegalHoldStatus(conversationId: QualifiedIDEntity): Flow<ConversationEntity.LegalHoldStatus>
    suspend fun observeLegalHoldStatusChangeNotified(conversationId: QualifiedIDEntity): Flow<Boolean>
    suspend fun getMLSGroupIdByUserId(userId: UserIDEntity): String?
    suspend fun getMLSGroupIdByConversationId(conversationId: QualifiedIDEntity): String?
    suspend fun getEstablishedSelfMLSGroupId(): String?

    suspend fun selectGroupStatusMembersNamesAndHandles(groupID: String): EpochChangesDataEntity?
}

data class NameAndHandleEntity(
    val name: String?,
    val handle: String?
)

data class EpochChangesDataEntity(
    val conversationId: QualifiedIDEntity,
    val mlsVerificationStatus: ConversationEntity.VerificationStatus,
    val members: Map<UserIDEntity, NameAndHandleEntity>
)
