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
import com.wire.kalium.persistence.dao.pendingaction.PendingActionEntity
import com.wire.kalium.persistence.dao.pendingaction.PendingActionType
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Rejoins MLS groups that received messages found stale or lost, each in its own CoreCrypto transaction
 * instead of the event batch's. The receive path queues a rejoin and fails the batch; the sync worker
 * rejoins and replays it. Only the app's standard receive path uses this queue.
 *
 * Only a missing network fails sync. A rejoin that fails in a way that can succeed later stays queued for the
 * next sync start and its group's messages are ignored until then: a later rejoin enters at the remote epoch,
 * so holding the batch back would decrypt none of them and would stop receiving in every conversation.
 */
internal interface MLSRejoinQueue {
    /** Queues a rejoin. False when one ran for the batch being replayed or waits for the next sync start: ignore the message. */
    suspend fun request(conversationId: ConversationId): Boolean

    /** Rejoins the queued groups not waiting for the next sync start. True when any was queued. Throws without network. */
    suspend fun rejoinQueued(): Boolean

    /** Rejoins every queued group, including those waiting for this sync start. Throws without network. */
    suspend fun rejoinAtSyncStart()

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

    // Still queued after a failure that can succeed later; tried again at the next sync start only.
    private val deferred = mutableSetOf<ConversationId>()

    override suspend fun request(conversationId: ConversationId): Boolean {
        if (conversationId in rejoined || conversationId in deferred) return false
        pendingActions.upsert(TYPE, conversationId.toDao(), Clock.System.now().toEpochMilliseconds())
        return true
    }

    override suspend fun rejoinQueued(): Boolean {
        val queued = pendingActions.getByActionType(TYPE).filterNot { it.qualifiedId.toModel() in deferred }
        queued.forEach { rejoin(it) }
        return queued.isNotEmpty()
    }

    override suspend fun rejoinAtSyncStart() {
        deferred.clear()
        pendingActions.getByActionType(TYPE).forEach { rejoin(it) }
    }

    private suspend fun rejoin(action: PendingActionEntity) {
        val conversationId = action.qualifiedId.toModel()
        val result = withContext(NonCancellable) {
            transactionProvider.transaction("rejoinStaleMLSGroup") {
                // The verifier rejoins only when CoreCrypto is behind the remote epoch or has no group.
                staleEpochVerifier.verifyEpoch(it, conversationId, timestamp = Instant.fromEpochMilliseconds(action.createdAt))
            }
        }
        val failure = (result as? Either.Left)?.value
        when {
            failure == null -> {
                pendingActions.acknowledge(TYPE, action)
                rejoined += conversationId
            }
            // Sync cannot make progress offline anyway; the batch is replayed once the network is back.
            failure is NetworkFailure.NoNetworkConnection -> throw KaliumSyncException("Rejoining a stale MLS group failed", failure)
            failure.canRejoinLater() -> {
                logger.w("Deferring the rejoin of ${conversationId.toLogString()} to the next sync start: $failure")
                deferred += conversationId
            }
            else -> {
                logger.w("Giving up the rejoin of ${conversationId.toLogString()}, its stale messages are ignored: $failure")
                pendingActions.acknowledge(TYPE, action)
                rejoined += conversationId
            }
        }
    }

    // Deferred groups keep their messages ignored until the next sync start.
    override fun onBatchAcknowledged() = rejoined.clear()

    private companion object {
        val TYPE = PendingActionType.REJOIN_STALE_MLS_GROUP
    }
}

/** Server and federation failures a later rejoin can get past; conflicting federated backends never resolve. */
private fun CoreFailure.canRejoinLater(): Boolean = when (this) {
    is NetworkFailure.FederatedBackendFailure.ConflictingBackends -> false
    MLSFailure.TransportUnavailable, is NetworkFailure.FederatedBackendFailure -> true
    is NetworkFailure.ServerMiscommunication -> kaliumException is KaliumException.ServerError
    else -> false
}
