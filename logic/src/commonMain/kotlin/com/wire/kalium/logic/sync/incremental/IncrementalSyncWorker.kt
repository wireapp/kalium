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

import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logger.KaliumLogger.Companion.ApplicationFlow.SYNC
import com.wire.kalium.logic.clientPlatform
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
import kotlinx.datetime.Clock

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
                withContext(NonCancellable) {
                    val envelopes = streamData.eventList
                    kaliumLogger.d("$TAG Received ${envelopes.size} events to process")
                    val batchStart = Clock.System.now()
                    var processedEvents = 0
                    var slowEvents = 0
                    var totalEventMs = 0L
                    var maxEventMs = 0L
                    var maxEventType = "unknown"

                    transactionProvider.transaction("processEvents") { context ->
                        databaseBuilder.dbInvalidationController.runMuted {
                            val eventIds = mutableListOf<String>()
                            for (envelope in envelopes) {
                                val eventStart = Clock.System.now()
                                when (val eventEither = eventProcessor.processEvent(context, envelope)) {
                                    is Either.Left -> return@runMuted eventEither
                                    is Either.Right -> {
                                        val elapsedMs = (Clock.System.now() - eventStart).inWholeMilliseconds
                                        processedEvents += 1
                                        totalEventMs += elapsedMs
                                        if (elapsedMs > maxEventMs) {
                                            maxEventMs = elapsedMs
                                            maxEventType = envelope.event.toLogMap()["type"]?.toString() ?: "unknown"
                                        }
                                        if (elapsedMs >= SLOW_EVENT_THRESHOLD_MS) {
                                            val eventType = envelope.event.toLogMap()["type"]?.toString() ?: "unknown"
                                            slowEvents += 1
                                            logger.w(
                                                "[PerfDiag] runtime=${runtimeLabel()} scope=incremental-event " +
                                                    "eventId=${envelope.event.id} type=$eventType elapsedMs=$elapsedMs " +
                                                    "batchSize=${envelopes.size} processedIndex=$processedEvents"
                                            )
                                        }
                                        eventEither.value?.let(eventIds::add)
                                    }
                                }
                            }

                            when (val flushResult = eventProcessor.flushPendingSideEffects()) {
                                is Either.Left -> flushResult
                                is Either.Right -> Either.Right(eventIds)
                            }
                        }
                    }
                        .onSuccess { eventIds ->
                            val batchElapsedMs = (Clock.System.now() - batchStart).inWholeMilliseconds
                            val avgEventMs = if (processedEvents > 0) totalEventMs / processedEvents else 0L
                            logger.i(
                                "[PerfDiag] runtime=${runtimeLabel()} scope=incremental-batch " +
                                    "batchSize=${envelopes.size} processedEvents=$processedEvents markedProcessed=${eventIds.size} " +
                                    "elapsedMs=$batchElapsedMs avgEventMs=$avgEventMs slowEvents=$slowEvents " +
                                    "maxEventMs=$maxEventMs maxEventType=$maxEventType"
                            )
                            if (eventIds.isEmpty()) {
                                logger.i("No events to mark as processed")
                                return@onSuccess
                            }

                            eventRepository.setEventsAsProcessed(eventIds)
                                .onSuccess {
                                    logger.i("${eventIds.size} events set as processed")
                                }
                        }
                        .onFailure {
                            throw KaliumSyncException("Processing failed", it)
                        }
                }
            }
        logger.withFeatureId(SYNC).i("SYNC Finished gathering and processing events")
    }.distinctUntilChanged()

    companion object {
        const val TAG = "[IncrementalSyncWorker]"
        private const val SLOW_EVENT_THRESHOLD_MS = 250L
    }

    private fun runtimeLabel(): String = if (clientPlatform == "jvm") "jvm" else "native"
}
