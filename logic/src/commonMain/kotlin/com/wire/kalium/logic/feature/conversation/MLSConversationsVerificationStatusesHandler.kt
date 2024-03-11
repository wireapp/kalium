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
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.conversation.Conversation.VerificationStatus
import com.wire.kalium.logic.data.conversation.ConversationDetails
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.conversation.mls.ConversationVerificationStatusChecker
import com.wire.kalium.logic.feature.conversation.mls.EpochChangesObserver
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.functional.onlyRight
import com.wire.kalium.util.DateTimeUtil
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach

/**
 * Observes all the MLS Conversations Verification status.
 * Notify user (by adding System message in conversation) if needed about changes.
 */
internal interface MLSConversationsVerificationStatusesHandler {
    suspend operator fun invoke()
}

internal class MLSConversationsVerificationStatusesHandlerImpl(
    private val conversationRepository: ConversationRepository,
    private val persistMessage: PersistMessageUseCase,
    private val conversationVerificationStatusChecker: ConversationVerificationStatusChecker,
    private val epochChangesObserver: EpochChangesObserver,
    private val selfUserId: UserId,
    kaliumLogger: KaliumLogger
) : MLSConversationsVerificationStatusesHandler {

    private val logger = kaliumLogger.withTextTag("MLSConversationsVerificationStatusesHandler")

    override suspend fun invoke() {
        logger.d("Starting to monitor")
        epochChangesObserver.observe()
            .mapLatest { groupId -> conversationVerificationStatusChecker.check(groupId).map { groupId to it } }
            .onEach {
                if (it is Either.Left) {
                    val failure = it.value
                    val throwable = if (failure is CoreFailure.Unknown) failure.rootCause else null
                    logger.w("Error while checking conversation verification status: ${it.value}", throwable)
                }
            }
            .onlyRight()
            .collect { (groupId, newStatus) ->
                conversationRepository.getConversationDetailsByMLSGroupId(groupId)
                    .map { conversation -> updateStatusAndNotifyUserIfNeeded(newStatus, conversation) }
            }
    }

    private suspend fun updateStatusAndNotifyUserIfNeeded(newStatusFromCC: VerificationStatus, conversation: ConversationDetails) {
        logger.i("Updating verification status and notifying user if needed")
        val currentStatus = conversation.conversation.mlsVerificationStatus
        val newStatus = getActualNewStatus(newStatusFromCC, currentStatus)

        if (newStatus == currentStatus) return

        conversationRepository.updateMlsVerificationStatus(newStatus, conversation.conversation.id)

        if (newStatus == VerificationStatus.DEGRADED || newStatus == VerificationStatus.VERIFIED) {
            notifyUserAboutStateChanges(conversation.conversation.id, newStatus)
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

        conversationRepository.setDegradedConversationNotifiedFlag(conversationId, updatedStatus != VerificationStatus.DEGRADED)
    }
}
