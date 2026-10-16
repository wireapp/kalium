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
package com.wire.kalium.logic.data.conversation

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.wrapMLSRequest
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.fold
import com.wire.kalium.common.functional.map
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.cryptography.CryptoTransactionContext
import com.wire.kalium.logic.data.conversation.Conversation.ProtocolInfo.MLSCapable.GroupState
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.id.toCrypto
import com.wire.kalium.network.api.authenticated.conversation.ConversationResponse
import com.wire.kalium.util.ConversationPersistenceApi

/**
 * Use case responsible for updating the protocol of a conversation, either locally or remotely.
 *
 * If `localOnly` is true, the protocol is updated only in the local database.
 * Otherwise, the change is performed through the backend, and the updated conversation
 * is persisted locally using [PersistConversationsUseCase] if needed.
 *
 * When the protocol changes to an MLS-capable one, the MLS group state is derived from CoreCrypto:
 * [GroupState.ESTABLISHED] if this client already has the group, a pending state otherwise.
 *
 * @param conversationId ID of the conversation to update.
 * @param protocol The new protocol to apply.
 * @param localOnly If true, applies the protocol update locally without backend interaction.
 * @return [Either.Right] with `true` if the update was successful, or [Either.Left] if an error occurred.
 */
internal interface UpdateConversationProtocolUseCase {
    suspend operator fun invoke(
        transactionContext: CryptoTransactionContext,
        conversationId: ConversationId,
        protocol: Conversation.Protocol,
        localOnly: Boolean
    ): Either<CoreFailure, Boolean>
}

@OptIn(ConversationPersistenceApi::class)
internal class UpdateConversationProtocolUseCaseImpl(
    private val conversationRepository: ConversationRepository,
    private val persistConversations: PersistConversationsUseCase
) : UpdateConversationProtocolUseCase {

    override suspend fun invoke(
        transactionContext: CryptoTransactionContext,
        conversationId: ConversationId,
        protocol: Conversation.Protocol,
        localOnly: Boolean
    ): Either<CoreFailure, Boolean> {
        return if (localOnly) {
            conversationRepository.updateProtocolLocally(conversationId, protocol)
        } else {
            conversationRepository.updateProtocolRemotely(
                conversationId = conversationId,
                protocol = protocol
            )
        }
            .flatMap { status ->
                if (status.hasUpdated) {
                    return@flatMap updateMLSGroupState(transactionContext, conversationId, protocol, status.response)
                        .map { true }
                }
                persistConversations(transactionContext, listOf(status.response), invalidateMembers = true)
                    .map { true }
            }
    }

    /**
     * Updating the protocol in place leaves the MLS columns with the Proteus defaults (ESTABLISHED, epoch 0),
     * so a client that missed the migration would never be picked up by pending-join recovery.
     */
    private suspend fun updateMLSGroupState(
        transactionContext: CryptoTransactionContext,
        conversationId: ConversationId,
        protocol: Conversation.Protocol,
        response: ConversationResponse
    ): Either<CoreFailure, Unit> {
        val groupId = response.groupId?.let(::GroupID)
        if (protocol == Conversation.Protocol.PROTEUS || groupId == null) return Either.Right(Unit)
        return conversationRepository.updateMLSGroupIdAndState(
            conversationId = conversationId,
            groupID = groupId,
            epoch = response.epoch ?: 0UL,
            groupState = mlsGroupState(transactionContext, groupId)
        )
    }

    private suspend fun mlsGroupState(transactionContext: CryptoTransactionContext, groupId: GroupID): GroupState {
        // Without an MLS client there is no Welcome to wait for: the group has to be joined once the client registers.
        val mlsContext = transactionContext.mls ?: return GroupState.PENDING_JOIN
        return wrapMLSRequest { mlsContext.conversationExists(groupId.toCrypto()) }
            .fold({ failure ->
                kaliumLogger.w("Error checking MLS group state after protocol update, setting to PENDING_JOIN: $failure")
                GroupState.PENDING_JOIN
            }, { exists ->
                if (exists) GroupState.ESTABLISHED else GroupState.PENDING_WELCOME_MESSAGE
            })
    }
}
