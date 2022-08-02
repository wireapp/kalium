package com.wire.kalium.logic.sync.incremental

import com.wire.kalium.logger.KaliumLogger.Companion.ApplicationFlow.SYNC
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.sync.IncrementalSyncRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncStatus
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.data.sync.SlowSyncStatus
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.sync.KaliumSyncException
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

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
        }
        syncScope.launch {
            delay(2000)
            startObservingForSync()
        }
    }

    private val syncScope = CoroutineScope(SupervisorJob() + eventProcessingDispatcher)

    init {
        startObservingForSync()
    }

    private fun startObservingForSync() {
        syncScope.launch(coroutineExceptionHandler) {
            incrementalSyncRepository.connectionPolicyState
                .combine(slowSyncRepository.slowSyncStatus) { policy, status ->
                    status to policy
                }.collectLatest { (status, _) ->
                    if (status is SlowSyncStatus.Complete) {
                        // START SYNC
                        kaliumLogger.i("Starting QuickSync, as SlowSync is completed")
                        incrementalSyncWorker.performIncrementalSync()
                    }
                }
        }
    }
}
