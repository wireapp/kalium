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
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.data.client.CryptoTransactionProvider
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.feature.message.StaleEpochVerifier
import com.wire.kalium.logic.sync.KaliumSyncException
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.persistence.dao.pendingaction.PendingActionDAO
import com.wire.kalium.persistence.dao.pendingaction.PendingActionType
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Rejoins MLS groups that received messages found stale or lost, each in its own CoreCrypto transaction
 * instead of the event batch's. The receive path queues a rejoin and fails the batch; the sync worker
 * rejoins and replays it. Only the app's standard receive path uses this queue.
 */
internal interface MLSRejoinQueue {
    /** Queues a rejoin. False when one already ran for the batch being replayed: ignore the message then. */
    suspend fun request(conversationId: ConversationId): Boolean

    /** Rejoins every queued group. True when any was queued. Throws, keeping the rest queued, when one can succeed later. */
    suspend fun rejoinQueued(): Boolean

    /** A batch was acknowledged, so later stale messages may queue a rejoin again. */
    fun onBatchAcknowledged()
}

internal class MLSRejoinQueueImpl(
    private val pendingActions: PendingActionDAO,
    private val transactionProvider: CryptoTransactionProvider,
    private val staleEpochVerifier: StaleEpochVerifier,
) : MLSRejoinQueue {
    private val logger by lazy { kaliumLogger.withFeatureId(KaliumLogger.Companion.ApplicationFlow.SYNC) }

    // Rejoined or given up since the last acknowledged batch. The replay must not queue these again:
    // a message that still fails would otherwise fail the batch forever.
    private val rejoined = mutableSetOf<ConversationId>()

    override suspend fun request(conversationId: ConversationId): Boolean {
        if (conversationId in rejoined) return false
        pendingActions.upsert(TYPE, conversationId.toDao(), Clock.System.now().toEpochMilliseconds())
        return true
    }

    override suspend fun rejoinQueued(): Boolean {
        val queued = pendingActions.getByActionType(TYPE)
        queued.forEach { action ->
            val conversationId = action.qualifiedId.toModel()
            val result = withContext(NonCancellable) {
                transactionProvider.transaction("rejoinStaleMLSGroup") {
                    // The verifier rejoins only when CoreCrypto is behind the remote epoch or has no group.
                    staleEpochVerifier.verifyEpoch(it, conversationId, timestamp = Instant.fromEpochMilliseconds(action.createdAt))
                }
            }
            if (result is Either.Left) {
                if (result.value.canRejoinLater()) throw KaliumSyncException("Rejoining a stale MLS group failed", result.value)
                logger.w("Giving up the rejoin of ${conversationId.toLogString()}, its stale messages are ignored: ${result.value}")
            }
            pendingActions.acknowledge(TYPE, action)
            rejoined += conversationId
        }
        return queued.isNotEmpty()
    }

    override fun onBatchAcknowledged() = rejoined.clear()

    private companion object {
        val TYPE = PendingActionType.REJOIN_STALE_MLS_GROUP
    }
}

/** The NSE's network rule, except conflicting federated backends, which no retry resolves. */
private fun CoreFailure.canRejoinLater(): Boolean = when (this) {
    is NetworkFailure.FederatedBackendFailure.ConflictingBackends -> false
    is NetworkFailure.NoNetworkConnection, MLSFailure.TransportUnavailable, is NetworkFailure.FederatedBackendFailure -> true
    is NetworkFailure.ServerMiscommunication -> kaliumException is KaliumException.ServerError
    else -> false
}
