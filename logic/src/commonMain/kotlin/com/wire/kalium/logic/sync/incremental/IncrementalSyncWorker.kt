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

import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logger.KaliumLogger.Companion.ApplicationFlow.SYNC
import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.sync.KaliumSyncException
import io.mockative.Mockable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch

/**
 * Gathers and processes IncrementalSync events.
 */
@Mockable
interface IncrementalSyncWorker {
    /**
     * Upon collection, will start collecting and processing events,
     * emitting the source of current events.
     */
    suspend fun processEventsFlow(): Flow<EventSource>
}

internal class IncrementalSyncWorkerImpl(
    private val eventGatherer: EventGatherer,
    private val eventProcessor: EventProcessor,
    logger: KaliumLogger = kaliumLogger,
) : IncrementalSyncWorker {

    private val logger = logger.withFeatureId(SYNC)

    override suspend fun processEventsFlow() = channelFlow {
        val sourceJob = launch {
            eventGatherer.currentSource.collect { send(it) }
        }

        launch {
            kaliumLogger.d("$TAG gatherEvents starting...")
            eventGatherer.gatherEvents()
                .collect { envelopes ->
                    kaliumLogger.d("$TAG Received ${envelopes.size} events to process")
                    envelopes.forEach { envelope ->
                        eventProcessor.processEvent(envelope).onFailure {
                            throw KaliumSyncException("Processing failed", it)
                        }
                    }
                }
        }
    }

    companion object {
        const val TAG = "[IncrementalSyncWorker]"
    }
}
