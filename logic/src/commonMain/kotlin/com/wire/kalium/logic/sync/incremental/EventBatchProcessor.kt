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

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.isRight
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.cryptography.CryptoTransactionContext
import com.wire.kalium.eventprocessing.DefaultEventBatchProcessor
import com.wire.kalium.eventprocessing.EventBatchProcessingObserver
import com.wire.kalium.eventprocessing.EventBatchProcessingRuntime
import com.wire.kalium.eventprocessing.EventBatchProcessor as SharedEventBatchProcessor
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logger.KaliumLogger.Companion.ApplicationFlow.SYNC
import com.wire.kalium.logic.data.client.CryptoTransactionProvider
import com.wire.kalium.logic.data.event.EventEnvelope
import com.wire.kalium.logic.data.event.EventRepository
import com.wire.kalium.logic.sync.KaliumSyncException
import com.wire.kalium.persistence.db.UserDatabaseBuilder

internal typealias EventBatchProcessor = SharedEventBatchProcessor<EventEnvelope>

/** Logic-owned wiring for the reusable event-processing engine. */
internal class EventBatchProcessorImpl(
    eventProcessor: EventProcessor,
    transactionProvider: CryptoTransactionProvider,
    databaseBuilder: UserDatabaseBuilder,
    eventRepository: EventRepository,
    logger: KaliumLogger = kaliumLogger,
) : EventBatchProcessor {

    private val delegate = DefaultEventBatchProcessor(
        runtime = KaliumEventBatchProcessingRuntime(
            eventProcessor = eventProcessor,
            transactionProvider = transactionProvider,
            databaseBuilder = databaseBuilder,
            eventRepository = eventRepository,
        ),
        observer = KaliumEventBatchProcessingObserver(logger),
    )

    override suspend fun processEvents(events: List<EventEnvelope>) {
        delegate.processEvents(events)
    }
}

private class KaliumEventBatchProcessingRuntime(
    private val eventProcessor: EventProcessor,
    private val transactionProvider: CryptoTransactionProvider,
    private val databaseBuilder: UserDatabaseBuilder,
    private val eventRepository: EventRepository,
) : EventBatchProcessingRuntime<EventEnvelope, CryptoTransactionContext> {

    override suspend fun transaction(
        block: suspend (CryptoTransactionContext) -> Either<CoreFailure, List<String>>
    ): Either<CoreFailure, List<String>> = transactionProvider.transaction("processEvents", block)

    override suspend fun runMuted(
        block: suspend () -> Either<CoreFailure, List<String>>
    ): Either<CoreFailure, List<String>> = databaseBuilder.dbInvalidationController.runMuted(block)

    override suspend fun processEvent(
        transactionContext: CryptoTransactionContext,
        event: EventEnvelope,
    ): Either<CoreFailure, String?> = eventProcessor.processEvent(transactionContext, event)

    override suspend fun flushPendingSideEffects(): Either<CoreFailure, Unit> =
        eventProcessor.flushPendingSideEffects()

    override suspend fun markEventsAsProcessed(eventIds: List<String>): Boolean =
        eventRepository.setEventsAsProcessed(eventIds).isRight()

    override fun processingException(failure: CoreFailure): Throwable =
        KaliumSyncException("Processing failed", failure)
}

private class KaliumEventBatchProcessingObserver(
    logger: KaliumLogger,
) : EventBatchProcessingObserver {

    private val logger = logger.withFeatureId(SYNC)

    override fun onBatchReceived(eventCount: Int) {
        kaliumLogger.d("${IncrementalSyncWorkerImpl.TAG} Received $eventCount events to process")
    }

    override fun onNoEventsToMarkAsProcessed() {
        logger.i("No events to mark as processed")
    }

    override fun onEventsMarkedAsProcessed(eventCount: Int) {
        logger.i("$eventCount events set as processed")
    }
}
