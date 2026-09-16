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
package com.wire.kalium.logic.feature.conversation.mls

import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.error.normalizeFederatedBackendConflict
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.cryptography.CryptoTransactionContext
import com.wire.kalium.logic.data.client.CryptoTransactionProvider
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.JoinExistingMLSConversationUseCase
import com.wire.kalium.logic.data.conversation.mls.PendingActionsRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.sync.SyncStateObserver
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.exceptions.isNotFound

internal interface RecoverPendingMLSGroupJoinsUseCase {
    suspend operator fun invoke()
}

internal class RecoverPendingMLSGroupJoinsUseCaseImpl(
    private val pendingActionsRepository: PendingActionsRepository,
    private val syncStateObserver: SyncStateObserver,
    private val transactionProvider: CryptoTransactionProvider,
    private val joinExistingMLSConversation: JoinExistingMLSConversationUseCase,
    private val conversationRepository: ConversationRepository,
) : RecoverPendingMLSGroupJoinsUseCase {

    override suspend fun invoke() {
        syncStateObserver.waitUntilLiveOrFailure().onFailure {
            return
        }

        val pendingConversationIds = pendingActionsRepository.getPendingMLSGroupJoins()
        if (pendingConversationIds.isEmpty()) return

        val successfulRecoveries = transactionProvider.transaction("recoverPendingMLSGroupJoins") { transactionContext ->
            Either.Right(recoverPendingConversations(transactionContext, pendingConversationIds))
        }

        when (successfulRecoveries) {
            is Either.Left -> Unit
            is Either.Right -> {
                if (successfulRecoveries.value.isNotEmpty()) {
                    pendingActionsRepository.acknowledgePendingMLSGroupJoins(successfulRecoveries.value)
                }
            }
        }
    }

    private suspend fun recoverPendingConversations(
        transactionContext: CryptoTransactionContext,
        pendingConversationIds: List<ConversationId>
    ): List<ConversationId> {
        val successfulConversationIds = mutableListOf<ConversationId>()
        pendingConversationIds.forEach { conversationId ->
            val recoveryResult = joinExistingMLSConversation(
                transactionContext = transactionContext,
                conversationId = conversationId,
                allowJoinByExternalCommit = true
            )
                .flatMap { conversationRepository.getConversationById(conversationId) }

            when (recoveryResult) {
                is Either.Left -> {
                    val transportFailure = recoveryResult.value
                    val failure = transportFailure.normalizeFederatedBackendConflict()
                    kaliumLogger.w("Failed to recover pending MLS group join for ${conversationId.toLogString()}: $failure")
                    if (failure is NetworkFailure.ServerMiscommunication &&
                        failure.kaliumException is KaliumException.InvalidRequestError &&
                        (failure.kaliumException as KaliumException.InvalidRequestError).isNotFound()
                    ) {
                        successfulConversationIds.add(conversationId)
                    } else if (failure is StorageFailure.DataNotFound) {
                        successfulConversationIds.add(conversationId)
                    } else if (failure is NetworkFailure.FederatedBackendFailure.ConflictingBackends) {
                        conversationRepository.setConversationDeletedLocally(conversationId, true)
                            .onFailure { deletionFailure ->
                                kaliumLogger.w(
                                    "Failed to discard terminal pending MLS group " +
                                            "${conversationId.toLogString()}: $deletionFailure"
                                )
                            }
                        successfulConversationIds.add(conversationId)
                    }
                }

                is Either.Right -> {
                    val conversation = recoveryResult.value
                    if (conversation.isMLSEstablished()) {
                        successfulConversationIds.add(conversationId)
                    } else {
                        kaliumLogger.w(
                            "Pending MLS group join for ${conversationId.toLogString()} completed without establishing the group"
                        )
                    }
                }
            }
        }
        return successfulConversationIds
    }

    private fun Conversation.isMLSEstablished(): Boolean {
        val mlsProtocol = protocol as? Conversation.ProtocolInfo.MLSCapable
        return mlsProtocol?.groupState == Conversation.ProtocolInfo.MLSCapable.GroupState.ESTABLISHED
    }
}
