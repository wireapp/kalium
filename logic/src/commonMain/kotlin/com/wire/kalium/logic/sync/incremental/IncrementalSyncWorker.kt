package com.wire.kalium.logic.sync.incremental

import com.wire.kalium.logger.KaliumLogger.Companion.ApplicationFlow.SYNC
import com.wire.kalium.logic.kaliumLogger
import kotlinx.coroutines.flow.cancellable

interface IncrementalSyncWorker {
    /**
     * Starts collecting and processing events.
     * Starting with Pending events, and then Live events
     */
    suspend fun performIncrementalSync()
}

internal class IncrementalSyncWorkerImpl(
    private val eventGatherer: EventGatherer,
    private val eventProcessor: EventProcessor
) : IncrementalSyncWorker {

    override suspend fun performIncrementalSync() {
        eventGatherer.gatherEvents().cancellable().collect {
            eventProcessor.processEvent(it)
        }
        kaliumLogger.withFeatureId(SYNC).i("SYNC Finished gathering and processing events")
    }
}
