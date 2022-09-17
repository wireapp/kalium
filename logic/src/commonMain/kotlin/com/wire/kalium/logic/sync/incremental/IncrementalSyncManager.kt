package com.wire.kalium.logic.sync.incremental

import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.sync.ConnectionPolicy.KEEP_ALIVE
import com.wire.kalium.logic.data.sync.IncrementalSyncRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncStatus
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.data.sync.SlowSyncStatus
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.sync.SyncExceptionHandler
import com.wire.kalium.logic.sync.full.SlowSyncManager
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

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
 * This Manager retries automatically in case of failures,
 * but still doesn't actively monitor connectivity changes in general,
 * like when a mobile phone changes from Wi-Fi to Mobile Data, etc.
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

    private val coroutineExceptionHandler = SyncExceptionHandler({
        kaliumLogger.i("Cancellation exception handled in SyncExceptionHandler for IncrementalSyncManager")
        incrementalSyncRepository.updateIncrementalSyncState(IncrementalSyncStatus.Pending)
    }, {
        kaliumLogger.i("$TAG ExceptionHandler error $it")
        incrementalSyncRepository.updateIncrementalSyncState(IncrementalSyncStatus.Failed(it))
        syncScope.launch {
            kaliumLogger.i("$TAG Triggering delay")
            delay(RETRY_DELAY)
            kaliumLogger.i("$TAG Delay finished")
        }
        startMonitoringForSync()
    })

    private val syncScope = CoroutineScope(SupervisorJob() + eventProcessingDispatcher)

    init {
        startMonitoringForSync()
    }

    private fun startMonitoringForSync() {
        syncScope.launch(coroutineExceptionHandler) {
            kaliumLogger.i("$TAG started monitoring for SlowSync")
            slowSyncRepository.slowSyncStatus.collectLatest { status ->
                if (status is SlowSyncStatus.Complete) {
                    // START SYNC. The ConnectionPolicy doesn't matter the first time
                    kaliumLogger.i("$TAG Starting IncrementalSync, as SlowSync is completed")
                    doIncrementalSyncWhilePolicyAllows()
                    incrementalSyncRepository.updateIncrementalSyncState(IncrementalSyncStatus.Pending)
                    kaliumLogger.i("$TAG IncrementalSync finished normally. Starting to observe ConnectionPolicy upgrade")
                    observeConnectionPolicyUpgrade()
                }
                incrementalSyncRepository.updateIncrementalSyncState(IncrementalSyncStatus.Pending)
            }
        }
    }

    private suspend fun observeConnectionPolicyUpgrade() {
        incrementalSyncRepository.connectionPolicyState
            .filter { it == KEEP_ALIVE }
            .cancellable()
            .collect {
                kaliumLogger.i("$TAG Re-starting IncrementalSync, as ConnectionPolicy was upgraded to KEEP_ALIVE")
                doIncrementalSyncWhilePolicyAllows()
            }
    }

    private suspend fun doIncrementalSyncWhilePolicyAllows() {
        incrementalSyncWorker
            .processEventsWhilePolicyAllowsFlow()
            .cancellable()
            .collect {
                val newState = when (it) {
                    EventSource.PENDING -> IncrementalSyncStatus.FetchingPendingEvents
                    EventSource.LIVE -> IncrementalSyncStatus.Live
                }
                incrementalSyncRepository.updateIncrementalSyncState(newState)
            }
        kaliumLogger.i("$TAG IncrementalSync stopped.")
    }

    private companion object {
        val RETRY_DELAY = 10.seconds
        private const val TAG = "IncrementalSyncManager"
    }
}
