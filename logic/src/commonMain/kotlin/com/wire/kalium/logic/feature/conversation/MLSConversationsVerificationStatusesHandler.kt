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
package com.wire.kalium.logic.feature.conversation

import com.benasher44.uuid.uuid4
import com.wire.kalium.cryptography.CryptoCertificateStatus
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.client.MLSClientProvider
import com.wire.kalium.logic.data.conversation.Conversation.VerificationStatus
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.conversation.toModel
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.conversation.mls.ConversationVerificationStatusChecker
import com.wire.kalium.logic.feature.conversation.mls.EpochChangesObserver
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.functional.onSuccess
import com.wire.kalium.logic.wrapMLSRequest
import com.wire.kalium.logic.wrapStorageRequest
import com.wire.kalium.persistence.dao.conversation.ConversationDAO
import com.wire.kalium.util.DateTimeUtil

/**
 * Observes all the MLS Conversations Verification status.
 * Notify user (by adding System message in conversation) if needed about changes.
 */
internal interface MLSConversationsVerificationStatusesHandler {
    suspend operator fun invoke()
}

data class VerificationStatusData(
    val conversationId: ConversationId,
    val currentStatusInDatabase: VerificationStatus,
    val newStatus: VerificationStatus
)

internal class MLSConversationsVerificationStatusesHandlerImpl(
    private val conversationRepository: ConversationRepository,
    private val persistMessage: PersistMessageUseCase,
    private val mlsClientProvider: MLSClientProvider,
    private val mlsConversationRepository: MLSConversationRepository,
    private val conversationDAO: ConversationDAO,
    private val epochChangesObserver: EpochChangesObserver,
    private val selfUserId: UserId,
    kaliumLogger: KaliumLogger
) : MLSConversationsVerificationStatusesHandler {

    private val logger = kaliumLogger.withTextTag("MLSConversationsVerificationStatusesHandler")

    override suspend fun invoke() {
        logger.d("Starting to monitor")
        epochChangesObserver.observe()
            .collect { groupId ->

                mlsClientProvider.getMLSClient()
                    .flatMap { mlsClient ->
                        wrapMLSRequest { mlsClient.isGroupVerified(groupId.value) }.map {
                            it.toModel()
                        }
                    }.flatMap { ccGroupStatus ->
                        if (ccGroupStatus == VerificationStatus.NOT_VERIFIED) {
                            verifyUsersStatus(groupId)
                        } else {
                            conversationRepository.getConversationDetailsByMLSGroupId(groupId).map {
                                VerificationStatusData(
                                    conversationId = it.conversation.id,
                                    currentStatusInDatabase = it.conversation.mlsVerificationStatus,
                                    newStatus =
                                    ccGroupStatus
                                )
                            }
                        }
                    }.onSuccess {
                        updateStatusAndNotifyUserIfNeeded(it)
                    }
            }
    }

    private suspend fun verifyUsersStatus(groupId: GroupID): Either<CoreFailure, VerificationStatusData> =
        wrapStorageRequest {
            conversationDAO.selectGroupStatusMembersNamesAndHandles(groupId.value)
        }.flatMap { (conversationId, status, membersEntity) ->
            mlsConversationRepository.getMembersIdentities(
                conversationId.toModel(),
                membersEntity.keys.map { it.toModel() })
                .map {
                    var newStatus: VerificationStatus = VerificationStatus.VERIFIED
                    // check that all identities are valid and name and handle are matching
                    for ((userId, wireIdentity) in it) {
                        val memberInfoInDatabase = membersEntity[userId.toDao()]
                        val isUserVerified = wireIdentity.firstOrNull {
                            it.status != CryptoCertificateStatus.VALID ||
                                    it.displayName != memberInfoInDatabase?.name ||
                                    it.handle != memberInfoInDatabase.handle
                        } != null
                        if (!isUserVerified) {
                            newStatus = VerificationStatus.NOT_VERIFIED
                            break
                        }
                    }
                    VerificationStatusData(
                        conversationId = conversationId.toModel(),
                        currentStatusInDatabase = status.toModel(),
                        newStatus = newStatus
                    )
                }
        }


    private suspend fun updateStatusAndNotifyUserIfNeeded(
        verificationStatusData: VerificationStatusData
    ) {
        logger.i("Updating verification status and notifying user if needed")
        val newStatus = getActualNewStatus(verificationStatusData.newStatus, verificationStatusData.currentStatusInDatabase)

        if (newStatus == verificationStatusData.currentStatusInDatabase) return

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
            date = DateTimeUtil.currentIsoDateTimeString(),
            senderUserId = selfUserId,
            status = Message.Status.Sent,
            visibility = Message.Visibility.VISIBLE,
            expirationData = null
        )

        persistMessage(conversationDegradedMessage)
        conversationRepository.setDegradedConversationNotifiedFlag(conversationId, updatedStatus == VerificationStatus.DEGRADED)
    }
}
