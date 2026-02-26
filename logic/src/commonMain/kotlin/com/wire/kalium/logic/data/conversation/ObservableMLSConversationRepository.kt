/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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
@file:Suppress("TooManyFunctions")

package com.wire.kalium.logic.data.conversation

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.E2EIFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.cryptography.E2EIClient
import com.wire.kalium.cryptography.MLSClient
import com.wire.kalium.cryptography.MlsCoreCryptoContext
import com.wire.kalium.cryptography.WireIdentity
import com.wire.kalium.logic.data.conversation.mls.MLSAdditionResult
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.id.QualifiedClientID
import com.wire.kalium.logic.data.mls.CipherSuite
import com.wire.kalium.logic.data.mls.MLSPublicKeys
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.messaging.hooks.CryptoStateChangeHookNotifier
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration

internal class ObservableMLSConversationRepository(
    private val delegate: MLSConversationRepository,
    private val userId: UserId,
    private val hookNotifier: CryptoStateChangeHookNotifier
) : MLSConversationRepository {

    // todo(ym). Probably this should also trigger, but maybe too expensive? or we don't care :)
    override suspend fun decryptMessage(
        mlsContext: MlsCoreCryptoContext,
        message: ByteArray,
        groupID: GroupID
    ): Either<CoreFailure, List<DecryptedMessageBundle>> =
        delegate.decryptMessage(mlsContext, message, groupID)

    override suspend fun establishMLSGroup(
        mlsContext: MlsCoreCryptoContext,
        groupID: GroupID,
        members: List<UserId>,
        publicKeys: MLSPublicKeys?,
        allowSkippingUsersWithoutKeyPackages: Boolean
    ): Either<CoreFailure, MLSAdditionResult> =
        delegate
            .establishMLSGroup(mlsContext, groupID, members, publicKeys, allowSkippingUsersWithoutKeyPackages)
            .onSuccess { hookNotifier.onCryptoStateChanged(userId) }

    override suspend fun establishMLSSubConversationGroup(
        mlsContext: MlsCoreCryptoContext,
        groupID: GroupID,
        parentId: ConversationId
    ): Either<CoreFailure, Unit> = delegate
        .establishMLSSubConversationGroup(mlsContext, groupID, parentId)
        .onSuccess { hookNotifier.onCryptoStateChanged(userId) }

    override suspend fun hasEstablishedMLSGroup(
        mlsContext: MlsCoreCryptoContext,
        groupID: GroupID
    ): Either<CoreFailure, Boolean> = delegate.hasEstablishedMLSGroup(mlsContext, groupID)

    override suspend fun removeMembersFromMLSGroup(
        mlsContext: MlsCoreCryptoContext,
        groupID: GroupID,
        userIdList: List<UserId>
    ): Either<CoreFailure, Unit> = delegate
        .removeMembersFromMLSGroup(mlsContext, groupID, userIdList)
        .onSuccess { hookNotifier.onCryptoStateChanged(userId) }

    override suspend fun removeClientsFromMLSGroup(
        mlsContext: MlsCoreCryptoContext,
        groupID: GroupID,
        clientIdList: List<QualifiedClientID>
    ): Either<CoreFailure, Unit> = delegate
        .removeClientsFromMLSGroup(mlsContext, groupID, clientIdList)
        .onSuccess { hookNotifier.onCryptoStateChanged(userId) }

    override suspend fun leaveGroup(
        mlsContext: MlsCoreCryptoContext,
        groupID: GroupID
    ): Either<CoreFailure, Unit> = delegate
        .leaveGroup(mlsContext, groupID)
        .onSuccess { hookNotifier.onCryptoStateChanged(userId) }

    override suspend fun joinGroupByExternalCommit(
        mlsContext: MlsCoreCryptoContext,
        groupID: GroupID,
        groupInfo: ByteArray
    ): Either<CoreFailure, Unit> = delegate
        .joinGroupByExternalCommit(mlsContext, groupID, groupInfo)
        .onSuccess { hookNotifier.onCryptoStateChanged(userId) }

    override suspend fun isLocalGroupEpochStale(
        mlsContext: MlsCoreCryptoContext,
        groupID: GroupID,
        currentRemoteEpoch: ULong
    ): Either<CoreFailure, Boolean> = delegate.isLocalGroupEpochStale(mlsContext, groupID, currentRemoteEpoch)

    override suspend fun getMLSGroupsRequiringKeyingMaterialUpdate(threshold: Duration): Either<CoreFailure, List<GroupID>> =
        delegate.getMLSGroupsRequiringKeyingMaterialUpdate(threshold)

    override suspend fun updateKeyingMaterial(
        mlsContext: MlsCoreCryptoContext,
        groupID: GroupID
    ): Either<CoreFailure, Unit> = delegate
        .updateKeyingMaterial(mlsContext, groupID)
        .onSuccess { hookNotifier.onCryptoStateChanged(userId) }

    override suspend fun commitPendingProposals(
        mlsContext: MlsCoreCryptoContext,
        groupID: GroupID
    ): Either<CoreFailure, Unit> = delegate
        .commitPendingProposals(mlsContext, groupID)
        .onSuccess { hookNotifier.onCryptoStateChanged(userId) }

    override suspend fun setProposalTimer(timer: ProposalTimer, inMemory: Boolean) =
        delegate.setProposalTimer(timer, inMemory)

    override suspend fun clearProposalTimer(groupID: GroupID) =
        delegate.clearProposalTimer(groupID)

    override suspend fun observeProposalTimers(): Flow<ProposalTimer> = delegate.observeProposalTimers()

    override suspend fun addMemberToMLSGroup(
        mlsContext: MlsCoreCryptoContext,
        groupID: GroupID,
        userIdList: List<UserId>,
        cipherSuite: CipherSuite
    ): Either<CoreFailure, Unit> = delegate
        .addMemberToMLSGroup(mlsContext, groupID, userIdList, cipherSuite)
        .onSuccess { hookNotifier.onCryptoStateChanged(userId) }

    override suspend fun rotateKeysAndMigrateConversations(
        mlsContext: MlsCoreCryptoContext,
        clientId: ClientId,
        e2eiClient: E2EIClient,
        certificateChain: String,
        groupIdList: List<GroupID>,
        isNewClient: Boolean
    ): Either<E2EIFailure, Unit> = delegate
        .rotateKeysAndMigrateConversations(mlsContext, clientId, e2eiClient, certificateChain, groupIdList, isNewClient)
        .onSuccess { hookNotifier.onCryptoStateChanged(userId) }

    override suspend fun getClientIdentity(
        mlsContext: MlsCoreCryptoContext,
        clientId: ClientId
    ): Either<CoreFailure, WireIdentity?> = delegate.getClientIdentity(mlsContext, clientId)

    override suspend fun getUserIdentity(
        mlsContext: MlsCoreCryptoContext,
        userId: UserId
    ): Either<CoreFailure, List<WireIdentity>> = delegate.getUserIdentity(mlsContext, userId)

    override suspend fun getMembersIdentities(
        mlsClient: MLSClient,
        conversationId: ConversationId,
        userIds: List<UserId>
    ): Either<CoreFailure, Map<UserId, List<WireIdentity>>> =
        delegate.getMembersIdentities(mlsClient, conversationId, userIds)

    override suspend fun updateGroupIdAndState(
        conversationId: ConversationId,
        newGroupId: GroupID,
        newEpoch: Long,
        groupState: ConversationEntity.GroupState
    ): Either<CoreFailure, Unit> = delegate.updateGroupIdAndState(conversationId, newGroupId, newEpoch, groupState)
}
