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

package com.wire.kalium.logic.sync.incremental

import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.foldToEitherWhileRight
import com.wire.kalium.common.functional.map
import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logger.KaliumLogger.Companion.ApplicationFlow.SYNC
import com.wire.kalium.logic.data.client.CryptoTransactionProvider
import com.wire.kalium.logic.data.event.EventEnvelope
import com.wire.kalium.logic.data.event.EventRepository
import com.wire.kalium.logic.sync.KaliumSyncException
import com.wire.kalium.persistence.db.UserDatabaseBuilder
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Applies one gathered app event batch and completes its queue acknowledgement.
 *
 * The batch owns cancellation until native transactions and acknowledgement finish.
 * A failed batch keeps its native crypto changes and is retried without being acknowledged.
 * Queue acknowledgement starts only after both native transactions return successfully.
 */
internal interface EventBatchProcessor {
    suspend fun processBatch(envelopes: List<EventEnvelope>)

    companion object {
        fun standard(
            eventProcessor: EventProcessor,
            transactionProvider: CryptoTransactionProvider,
            databaseBuilder: UserDatabaseBuilder,
            eventRepository: EventRepository,
            logger: KaliumLogger = kaliumLogger,
        ): EventBatchProcessor = EventBatchProcessorImpl(eventProcessor, transactionProvider, databaseBuilder, eventRepository, logger)
    }
}

private class EventBatchProcessorImpl(
    private val eventProcessor: EventProcessor,
    private val transactionProvider: CryptoTransactionProvider,
    private val databaseBuilder: UserDatabaseBuilder,
    private val eventRepository: EventRepository,
    logger: KaliumLogger,
) : EventBatchProcessor {
    private val logger = logger.withFeatureId(SYNC)

    override suspend fun processBatch(envelopes: List<EventEnvelope>): Unit = withContext(NonCancellable) {
        transactionProvider.transaction("processEvents") { transactionContext ->
            databaseBuilder.dbInvalidationController.runMuted {
                // Process the entire batch before collecting failures.
                envelopes.map { eventProcessor.processEvent(transactionContext, it) }
                    .foldToEitherWhileRight(mutableListOf<String>()) { result, eventIds ->
                        result.map { eventId ->
                            eventId?.let(eventIds::add)
                            eventIds
                        }
                    }
                    // A failed batch still commits CoreCrypto, so its decrypted messages cannot be decrypted again on replay.
                    .onFailure { eventProcessor.flushPendingSideEffects() }
                    .flatMap { eventIds -> eventProcessor.flushPendingSideEffects().map { eventIds } }
            }
        }.onSuccess { eventIds ->
            if (eventIds.isEmpty()) logger.i("No events to mark as processed") else acknowledge(eventIds)
        }.onFailure { throw KaliumSyncException("Processing failed", it) }
        Unit
    }

    private suspend fun acknowledge(eventIds: List<String>) {
        // Throw to restart the sync: unacknowledged rows count as emitted, so receiving would otherwise stall on a full batch.
        eventRepository.setEventsAsProcessed(eventIds)
            .onSuccess { logger.i("${eventIds.size} events set as processed") }
            .onFailure { throw KaliumSyncException("Marking processed events failed", it) }
    }
}
