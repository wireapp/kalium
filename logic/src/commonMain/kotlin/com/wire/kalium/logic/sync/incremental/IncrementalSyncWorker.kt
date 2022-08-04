package com.wire.kalium.logic.sync.incremental

import com.wire.kalium.logger.KaliumLogger.Companion.ApplicationFlow.SYNC
import com.wire.kalium.logic.kaliumLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

interface IncrementalSyncWorker {
    /**
     * Upon collection, will start collecting and processing events,
     * emitting the source of current events.
     */
    suspend fun incrementalSyncFlow(): Flow<EventSource>
}

internal class IncrementalSyncWorkerImpl(
    private val eventGatherer: EventGatherer,
    private val eventProcessor: EventProcessor
) : IncrementalSyncWorker {

    override suspend fun incrementalSyncFlow() = channelFlow {
        val sourceJob = launch {
            eventGatherer.currentSource.collect { send(it) }
        }
        launch {
            eventGatherer.gatherEvents().cancellable().collect {
                eventProcessor.processEvent(it)
            }
            // When events are all consumed, cancel the source job to complete the channelFlow
            sourceJob.cancel()
        }
        kaliumLogger.withFeatureId(SYNC).i("SYNC Finished gathering and processing events")
    }
}
