package com.wire.kalium.logic.sync.incremental

import com.wire.kalium.logger.KaliumLogger.Companion.ApplicationFlow.SYNC
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.sync.IncrementalSyncRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncStatus
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.data.sync.SlowSyncStatus
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.sync.KaliumSyncException
import com.wire.kalium.logic.sync.full.SlowSyncManager
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Starts and Stops Incremental Sync once SlowSync is performed.
 *
 * Incremental Sync consists of receiving events, such as:
 * - Messages
 * - User Updates (like name, email, avatar)
 * - Conversation Updates (name, add/remove members)
 * - Team Updates (new member, name change)
 * - Feature Flags Updates
 * And many more.
 *
 * Events come from an [EventSource]. Because [EventSource.LIVE] requires
 * a constant Websocket connection, connectivity changes may drop the
 * [IncrementalSyncStatus] down to [IncrementalSyncStatus.Failed].
 *
 * If an [Event] is lost, _e.g._ when this client becomes offline for
 * too long, SlowSync will be invalidated and [SlowSyncManager] should
 * perform a fresh SlowSync.
 *
 * This Manager **will** be responsible for retries in case of network
 * failures or connectivity changes in general.
 *
 * @see Event
 * @see SlowSyncManager
 */
internal class IncrementalSyncManager(
    private val slowSyncRepository: SlowSyncRepository,
    private val incrementalSyncWorker: IncrementalSyncWorker,
    private val incrementalSyncRepository: IncrementalSyncRepository,
    kaliumDispatcher: KaliumDispatcher = KaliumDispatcherImpl
) {

    /**
     * A dispatcher with limited parallelism of 1.
     * This means using this dispatcher only a single coroutine will be processed at a time.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val eventProcessingDispatcher = kaliumDispatcher.default.limitedParallelism(1)

    private val coroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        when (throwable) {
            is CancellationException -> {
                kaliumLogger.withFeatureId(SYNC).i("Sync job was cancelled")
                incrementalSyncRepository.updateIncrementalSyncState(IncrementalSyncStatus.Pending)
            }

            is KaliumSyncException -> {
                kaliumLogger.withFeatureId(SYNC).i("SyncException during events processing", throwable)
                incrementalSyncRepository.updateIncrementalSyncState(IncrementalSyncStatus.Failed(throwable.coreFailureCause))
            }

            else -> {
                kaliumLogger.withFeatureId(SYNC).i("Sync job failed due to unknown reason", throwable)
                incrementalSyncRepository.updateIncrementalSyncState(IncrementalSyncStatus.Failed(CoreFailure.Unknown(throwable)))
            }
            // TODO: Trigger retry depending on failure
        }
    }

    private val syncScope = CoroutineScope(SupervisorJob() + eventProcessingDispatcher)

    init {
        syncScope.launch(coroutineExceptionHandler) {
            // TODO: Trigger re-sync when policy changes from DISCONNECT to KEEP_ALIVE
            slowSyncRepository.slowSyncStatus.collectLatest { status ->
                if (status is SlowSyncStatus.Complete) {
                    // START SYNC
                    kaliumLogger.i("Starting QuickSync, as SlowSync is completed")
                    doIncrementalSync()
                }
                incrementalSyncRepository.updateIncrementalSyncState(IncrementalSyncStatus.Pending)
            }
        }
    }

    private suspend fun doIncrementalSync() {
        incrementalSyncWorker
            .incrementalSyncFlow()
            .cancellable()
            .collect {
                val newState = when (it) {
                    EventSource.PENDING -> IncrementalSyncStatus.FetchingPendingEvents
                    EventSource.LIVE -> IncrementalSyncStatus.Live
                }
                incrementalSyncRepository.updateIncrementalSyncState(newState)
            }
    }
}
