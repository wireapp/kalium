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

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.error.normalizeFederatedBackendConflict
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.getOrNull
import com.wire.kalium.common.functional.map
import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.cryptography.CryptoTransactionContext
import com.wire.kalium.logic.data.client.CryptoTransactionProvider
import com.wire.kalium.logic.data.client.wrapInMLSContext
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.JoinExistingMLSConversationUseCase
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.conversation.mls.PendingActionsRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.sync.SyncStateObserver
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.exceptions.isAccessDenied
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
    private val mlsConversationRepository: MLSConversationRepository,
) : RecoverPendingMLSGroupJoinsUseCase {

    override suspend fun invoke() {
        syncStateObserver.waitUntilLiveOrFailure().onFailure {
            return
        }

        // One transaction per conversation, acknowledged right away, so a kill keeps the joins already done.
        pendingActionsRepository.getPendingMLSGroupJoins().forEach { conversationId ->
            val recovered = transactionProvider.transaction("recoverPendingMLSGroupJoin") { transactionContext ->
                recoverPendingConversation(transactionContext, conversationId)
            }
            if (recovered is Either.Right && recovered.value) {
                pendingActionsRepository.acknowledgePendingMLSGroupJoins(listOf(conversationId))
            }
        }
    }

    private suspend fun recoverPendingConversation(
        transactionContext: CryptoTransactionContext,
        conversationId: ConversationId
    ): Either<CoreFailure, Boolean> = markEstablishedIfGroupExists(transactionContext, conversationId).map { groupExists ->
        groupExists || joinPendingConversation(transactionContext, conversationId)
    }

    // CoreCrypto may already have the group while the conversation still waits; don't send an external commit for it.
    private suspend fun markEstablishedIfGroupExists(
        transactionContext: CryptoTransactionContext,
        conversationId: ConversationId
    ): Either<CoreFailure, Boolean> {
        val groupId = conversationRepository.getNonDeletedConversationById(conversationId).getOrNull()
            ?.let { (it.protocol as? Conversation.ProtocolInfo.MLSCapable)?.groupId }
            ?: return Either.Right(false)
        return transactionContext.wrapInMLSContext { mlsContext ->
            mlsConversationRepository.hasEstablishedMLSGroup(mlsContext, groupId).flatMap { groupExists ->
                if (!groupExists) return@flatMap Either.Right(false)
                mlsConversationRepository.getLocalGroupEpoch(mlsContext, groupId).flatMap { epoch ->
                    conversationRepository.updateMLSGroupIdAndState(
                        conversationId,
                        groupId,
                        epoch,
                        Conversation.ProtocolInfo.MLSCapable.GroupState.ESTABLISHED
                    )
                }.map { true }
            }
        }
    }

    private suspend fun joinPendingConversation(
        transactionContext: CryptoTransactionContext,
        conversationId: ConversationId
    ): Boolean {
        val recoveryResult = joinExistingMLSConversation(
            transactionContext = transactionContext,
            conversationId = conversationId,
            allowJoinByExternalCommit = true
        )
            .flatMap { conversationRepository.getConversationById(conversationId) }

        return when (recoveryResult) {
            is Either.Left -> {
                val transportFailure = recoveryResult.value
                val failure = transportFailure.normalizeFederatedBackendConflict()
                kaliumLogger.w("Failed to recover pending MLS group join for ${conversationId.toLogString()}: $failure")
                val requestError = (failure as? NetworkFailure.ServerMiscommunication)?.kaliumException
                    as? KaliumException.InvalidRequestError
                when {
                    // Gone or no longer ours (e.g. we left): retrying on every foreground can't succeed.
                    requestError != null && (requestError.isNotFound() || requestError.isAccessDenied()) -> true
                    failure is StorageFailure.DataNotFound -> true
                    failure is NetworkFailure.FederatedBackendFailure.ConflictingBackends -> {
                        conversationRepository.setConversationDeletedLocally(conversationId, true)
                            .onFailure { deletionFailure ->
                                kaliumLogger.w(
                                    "Failed to discard terminal pending MLS group " +
                                            "${conversationId.toLogString()}: $deletionFailure"
                                )
                            }
                        true
                    }
                    else -> false
                }
            }

            is Either.Right -> {
                val established = recoveryResult.value.isMLSEstablished()
                if (!established) {
                    kaliumLogger.w(
                        "Pending MLS group join for ${conversationId.toLogString()} completed without establishing the group"
                    )
                }
                established
            }
        }
    }

    private fun Conversation.isMLSEstablished(): Boolean {
        val mlsProtocol = protocol as? Conversation.ProtocolInfo.MLSCapable
        return mlsProtocol?.groupState == Conversation.ProtocolInfo.MLSCapable.GroupState.ESTABLISHED
    }
}
