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

import com.benasher44.uuid.uuid4
import com.wire.kalium.cryptography.CredentialType
import com.wire.kalium.cryptography.CryptoCertificateStatus
import com.wire.kalium.cryptography.WireIdentity
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.client.MLSClientProvider
import com.wire.kalium.logic.data.conversation.Conversation.VerificationStatus
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.conversation.mls.EpochChangesData
import com.wire.kalium.logic.data.conversation.toModel
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.getOrElse
import com.wire.kalium.logic.functional.left
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.functional.onSuccess
import com.wire.kalium.logic.functional.right
import com.wire.kalium.logic.wrapMLSRequest
import io.mockative.Mockable
import kotlinx.datetime.Clock

typealias UserToWireIdentity = Map<UserId, List<WireIdentity>>

/**
 * Check and update MLS Conversations Verification status.
 * Notify user (by adding System message in conversation) if needed about changes.
 */
@Mockable
internal interface FetchMLSVerificationStatusUseCase {
    suspend operator fun invoke(groupId: GroupID)
}

data class VerificationStatusData(
    val conversationId: ConversationId,
    val currentPersistedStatus: VerificationStatus,
    val newStatus: VerificationStatus
)

@Suppress("LongParameterList")
internal class FetchMLSVerificationStatusUseCaseImpl(
    private val conversationRepository: ConversationRepository,
    private val persistMessage: PersistMessageUseCase,
    private val mlsClientProvider: MLSClientProvider,
    private val mlsConversationRepository: MLSConversationRepository,
    private val selfUserId: UserId,
    private val userRepository: UserRepository,
    kaliumLogger: KaliumLogger
) : FetchMLSVerificationStatusUseCase {

    private val logger = kaliumLogger.withTextTag("FetchMLSVerificationStatusUseCaseImpl")

    override suspend fun invoke(groupId: GroupID) {
        mlsClientProvider.getMLSClient()
            .flatMap { mlsClient ->
                wrapMLSRequest { mlsClient.isGroupVerified(groupId.value) }.map {
                    it.toModel()
                }
            }.flatMap { ccGroupStatus ->
                if (ccGroupStatus == VerificationStatus.VERIFIED) {
                    verifyUsersStatus(groupId)
                } else {
                    conversationRepository.getConversationDetailsByMLSGroupId(groupId).map {
                        VerificationStatusData(
                            conversationId = it.conversation.id,
                            currentPersistedStatus = it.conversation.mlsVerificationStatus,
                            newStatus =
                            ccGroupStatus
                        )
                    }
                }
            }.onSuccess {
                updateStatusAndNotifyUserIfNeeded(it)
            }
    }

    private suspend fun verifyUsersStatus(groupId: GroupID): Either<CoreFailure, VerificationStatusData> =
        conversationRepository.getGroupStatusMembersNamesAndHandles(groupId)
            .flatMap { epochChangesData ->
                mlsConversationRepository.getMembersIdentities(
                    epochChangesData.conversationId,
                    epochChangesData.members.keys.toList()
                )
                    .flatMap { ccIdentities ->
                        updateKnownUsersIfNeeded(epochChangesData, ccIdentities, groupId)
                    }
            }.map { (dbData, ccIdentity) ->
                var newStatus: VerificationStatus = VerificationStatus.VERIFIED
                // check that all identities are valid and name and handle are matching
                for ((userId, wireIdentity) in ccIdentity) {
                    val persistedMemberInfo = dbData.members[userId]
                    val isUserVerified = wireIdentity.none {
                        it.status != CryptoCertificateStatus.VALID ||
                                it.credentialType != CredentialType.X509 ||
                                it.x509Identity == null ||
                                it.x509Identity?.displayName != persistedMemberInfo?.name ||
                                it.x509Identity?.handle?.handle != persistedMemberInfo?.handle
                    }
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
        val newStatus = getActualNewStatus(
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
     * Current CoreCrypto implementation returns only a boolean flag "if conversation is verified or not".
     * So we need to calculate if status was degraded on our side by comparing it to the previous status.
     * @param newStatusFromCC - [VerificationStatus] that CoreCrypto returns.
     * @param currentStatus - [VerificationStatus] that conversation currently has.
     * @return [VerificationStatus] that should be saved to DB.
     */
    private fun getActualNewStatus(newStatusFromCC: VerificationStatus, currentStatus: VerificationStatus): VerificationStatus =
        if (newStatusFromCC == VerificationStatus.NOT_VERIFIED && currentStatus == VerificationStatus.VERIFIED)
            VerificationStatus.DEGRADED
        else if (newStatusFromCC == VerificationStatus.NOT_VERIFIED && currentStatus == VerificationStatus.DEGRADED)
            VerificationStatus.DEGRADED
        else
            newStatusFromCC

    /**
     * Add a SystemMessage into a conversation, to inform user when the conversation verification status becomes DEGRADED.
     */
    private suspend fun notifyUserAboutStateChanges(
        conversationId: ConversationId,
        updatedStatus: VerificationStatus
    ) {
        logger.i("Notifying user about state changes")
        val content = if (updatedStatus == VerificationStatus.VERIFIED) MessageContent.ConversationVerifiedMLS
        else MessageContent.ConversationDegradedMLS

        val conversationDegradedMessage = Message.System(
            id = uuid4().toString(),
            content = content,
            conversationId = conversationId,
            date = Clock.System.now(),
            senderUserId = selfUserId,
            status = Message.Status.Sent,
            visibility = Message.Visibility.VISIBLE,
            expirationData = null
        )

        persistMessage(conversationDegradedMessage)
        conversationRepository.setDegradedConversationNotifiedFlag(conversationId, updatedStatus != VerificationStatus.DEGRADED)
    }
}
