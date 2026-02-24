/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
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

import com.wire.kalium.common.functional.foldToEitherWhileRight
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.map
import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logger.KaliumLogger.Companion.ApplicationFlow.SYNC
import com.wire.kalium.logic.data.client.CryptoTransactionProvider
import com.wire.kalium.logic.data.event.EventRepository
import com.wire.kalium.logic.sync.KaliumSyncException
import com.wire.kalium.persistence.db.UserDatabaseBuilder
import io.mockative.Mockable
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.onEach

/**
 * Gathers and processes IncrementalSync events.
 */
@Mockable
internal interface IncrementalSyncWorker {
    /**
     * Upon collection, will start collecting and processing events,
     * emitting the source of current events.
     */
    suspend fun processEventsFlow(): Flow<EventSource>
}

internal class IncrementalSyncWorkerImpl(
    private val eventGatherer: EventGatherer,
    private val eventProcessor: EventProcessor,
    private val transactionProvider: CryptoTransactionProvider,
    private val databaseBuilder: UserDatabaseBuilder,
    private val eventRepository: EventRepository,
    logger: KaliumLogger = kaliumLogger,
) : IncrementalSyncWorker {

    private val logger = logger.withFeatureId(SYNC)

    override suspend fun processEventsFlow(): Flow<EventSource> = channelFlow {
        // We start as PENDING
        send(EventSource.PENDING)

        kaliumLogger.d("$TAG gatherEvents starting...")
        eventGatherer.gatherEvents()
            // If we ever become Up-To-Date, move to LIVE
            .onEach { eventStreamData ->
                if (eventStreamData is EventStreamData.IsUpToDate) {
                    send(EventSource.LIVE) // We are LIVE!!!!!!
                }
            }
            .filterIsInstance<EventStreamData.NewEvents>()
            .collect { streamData ->
                val envelopes = streamData.eventList
                kaliumLogger.d("$TAG Received ${envelopes.size} events to process")
                transactionProvider.transaction("processEvents") { context ->
                    databaseBuilder.dbInvalidationController.runMuted {
                        envelopes.map { envelope -> eventProcessor.processEvent(context, envelope) }
                            .foldToEitherWhileRight(mutableListOf<String>()) { eventEither, acc ->
                                eventEither.map { eventId ->
                                    eventId?.let(acc::add)
                                    acc
                                }
                            }
                            .flatMap { eventIds ->
                                eventProcessor.flushPendingSideEffects().map { eventIds }
                            }
                    }
                }
                    .onSuccess { eventIds ->
                        if (eventIds.isEmpty()) {
                            logger.i("No events to mark as processed")
                            return@onSuccess
                        }

                        withContext(NonCancellable) {
                            eventRepository.setEventsAsProcessed(eventIds)
                        }
                            .onSuccess {
                                logger.i("${eventIds.size} events set as processed")
                            }
                    }
                    .onFailure {
                        throw KaliumSyncException("Processing failed", it)
                    }
            }
        logger.withFeatureId(SYNC).i("SYNC Finished gathering and processing events")
    }.distinctUntilChanged()

    companion object {
        const val TAG = "[IncrementalSyncWorker]"
    }
}
