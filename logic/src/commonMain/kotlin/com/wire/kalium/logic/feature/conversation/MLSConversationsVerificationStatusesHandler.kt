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
package com.wire.kalium.logic.feature.conversation

import com.benasher44.uuid.uuid4
import com.wire.kalium.logic.data.conversation.Conversation.VerificationStatus
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.functional.onlyRight
import com.wire.kalium.util.DateTimeUtil
import kotlinx.coroutines.flow.mapLatest

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
    private val mlsConversationRepository: MLSConversationRepository,
    private val selfUserId: UserId,
) : MLSConversationsVerificationStatusesHandler {

    override suspend fun invoke() =
        mlsConversationRepository.observeEpochChanges()
            .mapLatest { groupId -> mlsConversationRepository.getConversationVerificationStatus(groupId).map { groupId to it } }
            .onlyRight()
            .collect { (groupId, newStatus) ->
                conversationRepository.getConversationDetailsByMLSGroupId(groupId)
                    .map { conversation ->
                        val currentStatus = conversation.conversation.verificationStatus

                        // Current CoreCrypto implementation returns only a boolean flag "if conversation is verified or not".
                        // So we need to calculate if status was degraded on our side by comparing it to the previous status.

                        if ((newStatus == VerificationStatus.NOT_VERIFIED && currentStatus == VerificationStatus.DEGRADED) ||
                            newStatus == currentStatus
                        ) {
                            return@collect
                        }

                        if (newStatus == VerificationStatus.NOT_VERIFIED && currentStatus == VerificationStatus.VERIFIED) {
                            conversationRepository.updateVerificationStatus(VerificationStatus.DEGRADED, conversation.conversation.id)
                            notifyUserAboutStateChanges(conversation.conversation.id, VerificationStatus.DEGRADED)
                        } else {
                            conversationRepository.updateVerificationStatus(newStatus, conversation.conversation.id)
                        }
                    }
            }

    /**
     * Add a SystemMessage into a conversation, to inform user when the conversation verification status becomes DEGRADED.
     */
    private suspend fun notifyUserAboutStateChanges(
        conversationId: ConversationId,
        updatedStatus: VerificationStatus
    ) {
        // TODO notify about verified too
        val content = MessageContent.ConversationDegradedMLS
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
    }
}
