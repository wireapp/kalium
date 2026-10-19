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
package com.wire.kalium.logic.feature.e2ei.usecase

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.wrapMLSRequest
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.getOrElse
import com.wire.kalium.common.functional.left
import com.wire.kalium.common.functional.map
import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.common.functional.right
import com.wire.kalium.cryptography.MLSClient
import com.wire.kalium.cryptography.WireIdentity
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.data.client.MLSClientProvider
import com.wire.kalium.logic.data.conversation.Conversation.VerificationStatus
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.conversation.actualMLSVerificationStatus
import com.wire.kalium.logic.data.conversation.areMLSIdentitiesVerified
import com.wire.kalium.logic.data.conversation.mls.EpochChangesData
import com.wire.kalium.logic.data.conversation.mlsVerificationMessage
import com.wire.kalium.logic.data.conversation.toModel
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import kotlin.uuid.Uuid
import kotlin.time.Clock

internal typealias UserToWireIdentity = Map<UserId, List<WireIdentity>>

/**
 * Check and update MLS Conversations Verification status.
 * Notify user (by adding System message in conversation) if needed about changes.
 */
internal interface FetchMLSVerificationStatusUseCase {
    suspend operator fun invoke(groupId: GroupID)
}

internal data class VerificationStatusData(
    val conversationId: ConversationId,
    val currentPersistedStatus: VerificationStatus,
    val newStatus: VerificationStatus
)

@Suppress("LongParameterList")
internal class FetchMLSVerificationStatusUseCaseImpl(
    private val mlsClientProvider: MLSClientProvider,
    private val conversationRepository: ConversationRepository,
    private val persistMessage: PersistMessageUseCase,
    private val mlsConversationRepository: MLSConversationRepository,
    private val selfUserId: UserId,
    private val userRepository: UserRepository,
    kaliumLogger: KaliumLogger
) : FetchMLSVerificationStatusUseCase {

    private val logger = kaliumLogger.withTextTag("FetchMLSVerificationStatusUseCaseImpl")

    override suspend fun invoke(groupId: GroupID) {
        mlsClientProvider.getMLSClient()
            .onFailure { logger.w("Could not fetch MLS client for verification refresh: $it") }
            .onSuccess { mlsClient -> refreshVerificationStatus(mlsClient, groupId) }
    }

    private suspend fun refreshVerificationStatus(mlsClient: MLSClient, groupId: GroupID) {
        wrapMLSRequest { mlsClient.getGroupState(groupId.value) }
            .map { it.toModel() }
            .flatMap { ccGroupStatus ->
                if (ccGroupStatus == VerificationStatus.VERIFIED) {
                    verifyUsersStatus(mlsClient, groupId)
                } else {
                    conversationRepository.getConversationByMLSGroupId(groupId).map {
                        VerificationStatusData(
                            conversationId = it.id,
                            currentPersistedStatus = it.mlsVerificationStatus,
                            newStatus = ccGroupStatus
                        )
                    }
                }
            }.onSuccess {
                updateStatusAndNotifyUserIfNeeded(it)
            }
    }

    private suspend fun verifyUsersStatus(mlsClient: MLSClient, groupId: GroupID): Either<CoreFailure, VerificationStatusData> =
        conversationRepository.getGroupStatusMembersNamesAndHandles(groupId)
            .flatMap { epochChangesData ->
                mlsConversationRepository.getMembersIdentities(
                    mlsClient = mlsClient,
                    conversationId = epochChangesData.conversationId,
                    userIds = epochChangesData.members.keys.toList()
                )
                    .flatMap { ccIdentities ->
                        updateKnownUsersIfNeeded(epochChangesData, ccIdentities, groupId)
                    }
            }.map { (dbData, ccIdentity) ->
                var newStatus: VerificationStatus = VerificationStatus.VERIFIED
                // check that all identities are valid and name and handle are matching
                for ((userId, wireIdentity) in ccIdentity) {
                    val persistedMemberInfo = dbData.members[userId]
                    val isUserVerified = areMLSIdentitiesVerified(wireIdentity, persistedMemberInfo?.name, persistedMemberInfo?.handle)
                    if (!isUserVerified) {
                        newStatus = VerificationStatus.NOT_VERIFIED
                        break
                    }
                }
                VerificationStatusData(
                    conversationId = dbData.conversationId,
                    currentPersistedStatus = dbData.mlsVerificationStatus,
                    newStatus = newStatus
                )
            }

    private suspend fun updateKnownUsersIfNeeded(
        epochChangesData: EpochChangesData,
        ccIdentities: UserToWireIdentity,
        groupId: GroupID
    ): Either<CoreFailure, Pair<EpochChangesData, UserToWireIdentity>> {
        var dbData = epochChangesData

        val missingUsers = missingUsers(
            usersFromDB = epochChangesData.members.keys,
            usersFromCC = ccIdentities.keys
        )

        if (missingUsers.isNotEmpty()) {
            logger.i("Fetching missing users during verification process")
            conversationRepository.getGroupStatusMembersNamesAndHandles(groupId)
                .onSuccess {
                    dbData = it
                }.getOrElse { error -> return error.left() }
        }

        return (dbData to ccIdentities).right()
    }

    private suspend fun missingUsers(usersFromDB: Set<UserId>, usersFromCC: Set<UserId>): Set<UserId> {
        (usersFromCC - usersFromDB).let {
            if (it.isNotEmpty()) userRepository.fetchUsersByIds(it)
            return it
        }
    }

    private suspend fun updateStatusAndNotifyUserIfNeeded(
        verificationStatusData: VerificationStatusData
    ) {
        logger.i("Updating verification status and notifying user if needed")
        val newStatus = actualMLSVerificationStatus(
            newStatusFromCC = verificationStatusData.newStatus,
            currentStatus = verificationStatusData.currentPersistedStatus
        )

        if (newStatus == verificationStatusData.currentPersistedStatus) return

        conversationRepository.updateMlsVerificationStatus(newStatus, verificationStatusData.conversationId)

        if (newStatus == VerificationStatus.DEGRADED || newStatus == VerificationStatus.VERIFIED) {
            notifyUserAboutStateChanges(verificationStatusData.conversationId, newStatus)
        }
    }

    /**
     * Add a SystemMessage into a conversation, to inform user when the conversation verification status becomes DEGRADED.
     */
    private suspend fun notifyUserAboutStateChanges(
        conversationId: ConversationId,
        updatedStatus: VerificationStatus
    ) {
        logger.i("Notifying user about state changes")
        persistMessage(mlsVerificationMessage(Uuid.random().toString(), conversationId, selfUserId, Clock.System.now(), updatedStatus))
        conversationRepository.setDegradedConversationNotifiedFlag(conversationId, updatedStatus != VerificationStatus.DEGRADED)
    }
}
