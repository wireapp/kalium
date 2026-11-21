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
package com.wire.kalium.logic.sync.receiver.conversation.message

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.MLSFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.fold
import com.wire.kalium.common.functional.getOrElse
import com.wire.kalium.common.functional.map
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.cryptography.CryptoTransactionContext
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.data.client.CryptoTransactionProvider
import com.wire.kalium.logic.data.client.wrapInMLSContext
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.feature.message.StaleEpochVerifier
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.persistence.dao.pendingaction.PendingActionDAO
import com.wire.kalium.persistence.dao.pendingaction.PendingActionEntity
import com.wire.kalium.persistence.dao.pendingaction.PendingActionType
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Rejoins stale or lost groups outside the event batch. The sync start rejoins only groups CoreCrypto lacks:
 * rejoining a group that is merely behind the backlog would make that backlog undecryptable.
 */
internal interface MLSRejoinQueue {
    suspend fun request(conversationId: ConversationId)

    suspend fun rejoinQueued()

    suspend fun rejoinAtSyncStart()
}

internal class MLSRejoinQueueImpl(
    private val pendingActions: PendingActionDAO,
    private val transactionProvider: CryptoTransactionProvider,
    private val staleEpochVerifier: StaleEpochVerifier,
    private val conversationRepository: ConversationRepository,
    private val mlsConversationRepository: MLSConversationRepository,
) : MLSRejoinQueue {
    private val logger by lazy { kaliumLogger.withFeatureId(KaliumLogger.Companion.ApplicationFlow.SYNC) }

    private val deferred = mutableSetOf<ConversationId>()

    override suspend fun request(conversationId: ConversationId) {
        if (conversationId in deferred) return
        pendingActions.upsert(TYPE, conversationId.toDao(), Clock.System.now().toEpochMilliseconds())
    }

    override suspend fun rejoinQueued() {
        pendingActions.getByActionType(TYPE).filterNot { it.qualifiedId.toModel() in deferred }.forEach { rejoin(it) }
    }

    override suspend fun rejoinAtSyncStart() {
        deferred.clear()
        pendingActions.getByActionType(TYPE).forEach { rejoin(it, onlyMissingGroup = true) }
    }

    private suspend fun rejoin(action: PendingActionEntity, onlyMissingGroup: Boolean = false) {
        val conversationId = action.qualifiedId.toModel()
        val result = withContext(NonCancellable) {
            transactionProvider.transaction("rejoinStaleMLSGroup") { context ->
                if (onlyMissingGroup && hasLocalGroup(context, conversationId)) {
                    Either.Right(false)
                } else {
                    staleEpochVerifier.verifyEpoch(context, conversationId, timestamp = Instant.fromEpochMilliseconds(action.createdAt))
                        .map { true }
                }
            }
        }
        result.fold({ failure ->
            if (failure.canRejoinLater()) {
                logger.w("Deferring the rejoin of ${conversationId.toLogString()} to the next sync start: $failure")
                deferred += conversationId
            } else {
                logger.w("Giving up the rejoin of ${conversationId.toLogString()}, its stale messages are ignored: $failure")
                pendingActions.acknowledge(TYPE, action)
            }
        }, { rejoined ->
            if (!rejoined) logger.i("Dropping the rejoin of ${conversationId.toLogString()}: CoreCrypto has its group")
            pendingActions.acknowledge(TYPE, action)
        })
    }

    private suspend fun hasLocalGroup(context: CryptoTransactionContext, conversationId: ConversationId): Boolean =
        conversationRepository.getConversationProtocolInfo(conversationId).flatMap { protocolInfo ->
            if (protocolInfo is Conversation.ProtocolInfo.MLSCapable) {
                context.wrapInMLSContext { mlsConversationRepository.hasEstablishedMLSGroup(it, protocolInfo.groupId) }
            } else {
                Either.Right(false)
            }
        }.getOrElse(false)

    private companion object {
        val TYPE = PendingActionType.REJOIN_STALE_MLS_GROUP
    }
}

/** Conflicting federation never resolves. */
private fun CoreFailure.canRejoinLater(): Boolean = when (this) {
    is NetworkFailure.FederatedBackendFailure.ConflictingBackends -> false
    is NetworkFailure.NoNetworkConnection, MLSFailure.TransportUnavailable, is NetworkFailure.FederatedBackendFailure -> true
    is NetworkFailure.ServerMiscommunication -> kaliumException is KaliumException.ServerError
    else -> false
}
