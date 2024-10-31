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

import com.benasher44.uuid.uuid4
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logger.KaliumLogger.Companion.ApplicationFlow.SYNC
import com.wire.kalium.logic.data.sync.ConnectionPolicy
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.sync.KaliumSyncException
import com.wire.kalium.logic.sync.SyncType
import com.wire.kalium.logic.sync.provideNewSyncManagerStartedLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch

/**
 * Gathers and processes IncrementalSync events.
 */
interface IncrementalSyncWorker {
    /**
     * Upon collection, will start collecting and processing events,
     * emitting the source of current events.
     *
     * Flow will finish only if the [ConnectionPolicy]
     * is [ConnectionPolicy.DISCONNECT_AFTER_PENDING_EVENTS].
     * Otherwise, it will keep collecting and processing events
     * indeterminately until a failure or cancellation.
     */
    suspend fun processEventsWhilePolicyAllowsFlow(): Flow<EventSource>
}

internal class IncrementalSyncWorkerImpl(
    private val eventGatherer: EventGatherer,
    private val eventProcessor: EventProcessor,
    logger: KaliumLogger = kaliumLogger,
) : IncrementalSyncWorker {

    private val logger = logger.withFeatureId(SYNC)

    override suspend fun processEventsWhilePolicyAllowsFlow() = channelFlow {
        val sourceJob = launch {
            eventGatherer.currentSource.collect { send(it) }
        }
        launch {
            // start
            val syncId = uuid4().toString()
            val syncLogger = kaliumLogger.provideNewSyncManagerStartedLogger(SyncType.INCREMENTAL, syncId)
            eventGatherer.gatherEvents().cancellable().collect {
                // TODO make sure that event process is not cancel in a midway
                eventProcessor.processEvent(it).onFailure { failure ->
                    throw KaliumSyncException("Failed to process event. Aborting Sync for a retry", failure)
                }
            }
            // When events are all consumed, cancel the source job to complete the channelFlow
            syncLogger.logSyncCompleted()
            sourceJob.cancel()
            // finish
            logger.withFeatureId(SYNC).i("SYNC Finished gathering and processing events")
        }
    }
}
