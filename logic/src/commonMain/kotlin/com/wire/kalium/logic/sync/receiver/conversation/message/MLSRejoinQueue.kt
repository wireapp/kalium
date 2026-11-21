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
 * Rejoins MLS groups that received messages found stale or lost, each in its own CoreCrypto transaction
 * instead of the event batch's. The receive path queues a rejoin and ignores the message: a rejoin enters at
 * the remote epoch, so no message already received for the group can be decrypted after it. The sync worker
 * rejoins after the batch is acknowledged. The notification extension does not use this queue.
 *
 * A rejoin never fails sync. One that can succeed later stays queued and is tried again at the next sync
 * start only. The sync start rejoins only groups CoreCrypto lacks: one it has may have been repaired meanwhile
 * and only be behind the backlog, which a rejoin would make undecryptable. If it is still stale, its next
 * message queues a rejoin again.
 */
internal interface MLSRejoinQueue {
    /** Queues a rejoin of the conversation's group for after the current batch. */
    suspend fun request(conversationId: ConversationId)

    /** Rejoins the queued groups not waiting for the next sync start. */
    suspend fun rejoinQueued()

    /** Rejoins every queued group CoreCrypto lacks and drops the others for the backlog to catch up. */
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

    // Still queued after a failure that can succeed later; tried again at the next sync start only.
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
                    // The verifier rejoins only when CoreCrypto is behind the remote epoch or has no group.
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

    // A conversation or group that can't be resolved locally is rejoined as before.
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

/**
 * Network, server and federation failures a later rejoin can get past. Federation that is refused or conflicting
 * never resolves, so retrying it would only keep the group's messages ignored.
 */
private fun CoreFailure.canRejoinLater(): Boolean = when (this) {
    is NetworkFailure.FederatedBackendFailure.ConflictingBackends,
    is NetworkFailure.FederatedBackendFailure.FederationDenied,
    is NetworkFailure.FederatedBackendFailure.FederationNotEnabled,
    is NetworkFailure.FederatedBackendFailure.FederationNotImplemented -> false
    is NetworkFailure.NoNetworkConnection, MLSFailure.TransportUnavailable, is NetworkFailure.FederatedBackendFailure -> true
    is NetworkFailure.ServerMiscommunication -> kaliumException is KaliumException.ServerError
    else -> false
}
