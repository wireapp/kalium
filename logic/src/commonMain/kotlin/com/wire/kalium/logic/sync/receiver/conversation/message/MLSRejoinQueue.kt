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

import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.data.client.CryptoTransactionProvider
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.feature.message.StaleEpochVerifier
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlin.time.Instant

/** Rejoins stale or lost groups after the event batch. A failed rejoin is forgotten until the group's next message. */
internal interface MLSRejoinQueue {
    suspend fun request(conversationId: ConversationId, messageInstant: Instant)

    suspend fun rejoinQueued()
}

internal class MLSRejoinQueueImpl(
    private val transactionProvider: CryptoTransactionProvider,
    private val staleEpochVerifier: StaleEpochVerifier,
) : MLSRejoinQueue {
    private val logger by lazy { kaliumLogger.withFeatureId(KaliumLogger.Companion.ApplicationFlow.SYNC) }

    private val queued = mutableMapOf<ConversationId, Instant>()

    override suspend fun request(conversationId: ConversationId, messageInstant: Instant) {
        queued.getOrPut(conversationId) { messageInstant }
    }

    override suspend fun rejoinQueued() {
        val requests = queued.toMap()
        queued.clear()
        requests.forEach { (conversationId, messageInstant) ->
            withContext(NonCancellable) {
                transactionProvider.transaction("rejoinStaleMLSGroup") { context ->
                    staleEpochVerifier.verifyEpoch(context, conversationId, timestamp = messageInstant)
                }
            }.onFailure { logger.w("Rejoin of ${conversationId.toLogString()} failed: $it") }
        }
    }
}
