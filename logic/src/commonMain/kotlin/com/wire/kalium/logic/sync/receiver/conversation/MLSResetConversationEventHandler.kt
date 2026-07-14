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

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.fold
import com.wire.kalium.cryptography.CryptoTransactionContext
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.persistence.dao.conversation.ConversationEntity

internal interface MLSResetConversationEventHandler {
    suspend fun handle(
        transaction: CryptoTransactionContext,
        event: Event.Conversation.MLSReset
    ): Either<CoreFailure, Unit>
}

internal class MLSResetConversationEventHandlerImpl(
    private val mlsConversationRepository: MLSConversationRepository,
) : MLSResetConversationEventHandler {
    override suspend fun handle(
        transaction: CryptoTransactionContext,
        event: Event.Conversation.MLSReset
    ): Either<CoreFailure, Unit> {
        val mlsContext = transaction.mls ?: return Either.Right(Unit)

        // Leaving the old group is best-effort: it may already be absent when a reset is replayed.
        mlsConversationRepository.leaveGroup(mlsContext, event.groupID)

        return mlsConversationRepository.hasEstablishedMLSGroup(mlsContext, event.newGroupID)
            .fold(
                { Either.Left(it) },
                { hasEstablishedMLSGroup ->
                    if (hasEstablishedMLSGroup) {
                        mlsConversationRepository.getLocalGroupEpoch(mlsContext, event.newGroupID)
                            .flatMap { epoch ->
                                updateGroupState(event, ConversationEntity.GroupState.ESTABLISHED, epoch.toLong())
                            }
                    } else {
                        updateGroupState(event, ConversationEntity.GroupState.PENDING_AFTER_RESET, 0L)
                    }
                }
            )
    }

    private suspend fun updateGroupState(
        event: Event.Conversation.MLSReset,
        state: ConversationEntity.GroupState,
        epoch: Long
    ): Either<CoreFailure, Unit> = mlsConversationRepository.updateGroupIdAndState(
        event.conversationId,
        event.newGroupID,
        groupState = state,
        newEpoch = epoch,
    )
}
