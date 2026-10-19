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

import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logger.KaliumLogger.Companion.ApplicationFlow.SYNC
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.onEach

/** Gathers app event batches and publishes incremental sync progress. */
internal interface IncrementalSyncWorker {
    /** Collection starts receiving and processing, emitting the source of current events. */
    suspend fun processEventsFlow(): Flow<EventSource>
}

internal class IncrementalSyncWorkerImpl(
    private val eventGatherer: EventGatherer,
    private val batchProcessor: EventBatchProcessor,
    logger: KaliumLogger = kaliumLogger,
    private val consumePendingAccountInvalidation: suspend () -> Boolean = { false },
    private val consumePendingLegalHoldTransitions: suspend () -> Unit = {},
    private val consumePendingConversationCleanup: suspend () -> Unit = {},
) : IncrementalSyncWorker {
    private val logger = logger.withFeatureId(SYNC)

    override suspend fun processEventsFlow(): Flow<EventSource> = channelFlow {
        send(EventSource.PENDING)
        // Removed credentials may never connect; the existing logout owner runs first.
        if (consumePendingAccountInvalidation()) awaitCancellation()
        consumePendingConversationCleanup()
        consumePendingLegalHoldTransitions()

        logger.d("$TAG gatherEvents starting...")
        eventGatherer.gatherEvents()
            .onEach { if (it is EventStreamData.IsUpToDate) send(EventSource.LIVE) }
            .filterIsInstance<EventStreamData.NewEvents>()
            .collect { streamData ->
                logger.d("$TAG Received ${streamData.eventList.size} events to process")
                batchProcessor.processBatch(streamData.eventList)
            }
        logger.i("SYNC Finished gathering and processing events")
    }.distinctUntilChanged()

    companion object {
        const val TAG = "[IncrementalSyncWorker]"
    }
}
