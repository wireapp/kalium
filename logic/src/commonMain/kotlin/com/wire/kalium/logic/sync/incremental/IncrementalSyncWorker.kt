package com.wire.kalium.logic.sync.incremental

import com.wire.kalium.logger.KaliumLogger.Companion.ApplicationFlow.SYNC
import com.wire.kalium.logic.data.sync.ConnectionPolicy
import com.wire.kalium.logic.kaliumLogger
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
    private val eventProcessor: EventProcessor
) : IncrementalSyncWorker {

    override suspend fun processEventsWhilePolicyAllowsFlow() = channelFlow {
        val sourceJob = launch {
            eventGatherer.currentSource.collect { send(it) }
        }
        launch {
            eventGatherer.gatherEvents().cancellable().collect {
                // TODO make sure that event process is not cancel in a midway
                eventProcessor.processEvent(it)
            }
            // When events are all consumed, cancel the source job to complete the channelFlow
            sourceJob.cancel()
            kaliumLogger.withFeatureId(SYNC).i("SYNC Finished gathering and processing events")
        }
    }
}
