/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
package com.wire.kalium.logic.sync.receiver.conversation

import com.wire.kalium.common.functional.getOrElse
import com.wire.kalium.cryptography.CryptoTransactionContext
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import io.mockative.Mockable

@Mockable
interface MLSResetConversationEventHandler {
    suspend fun handle(transaction: CryptoTransactionContext, event: Event.Conversation.MLSReset)
}

internal class MLSResetConversationEventHandlerImpl(
    private val mlsConversationRepository: MLSConversationRepository,
) : MLSResetConversationEventHandler {
    override suspend fun handle(transaction: CryptoTransactionContext, event: Event.Conversation.MLSReset) {

        // If the event is from same user do reset only if local group id does not match new group id.
        transaction.mls?.let { mlsContext ->
            mlsConversationRepository.leaveGroup(mlsContext, event.groupID)

            val hasEstablishedMLSGroup = mlsConversationRepository.hasEstablishedMLSGroup(
                mlsContext,
                event.newGroupID
            ).getOrElse { false }

            val newEpoch = if (hasEstablishedMLSGroup) {
                mlsContext.conversationEpoch(event.newGroupID.value).toLong()
            } else {
                0L
            }

            val newState = if (hasEstablishedMLSGroup) {
                // already have the group, no need to join
                // can mean that the welcome event arrived before the reset
                ConversationEntity.GroupState.ESTABLISHED
            } else {
                // update local db with the new group id and set the conversation as not established
                ConversationEntity.GroupState.PENDING_AFTER_RESET
            }

            mlsConversationRepository.updateGroupIdAndState(
                event.conversationId,
                event.newGroupID,
                groupState = newState,
                newEpoch = newEpoch,
            )
        }
    }
}
