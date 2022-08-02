package com.wire.kalium.logic.sync.full

import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.data.sync.SlowSyncStatus
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.sync.SyncCriteriaProvider
import com.wire.kalium.logic.sync.SyncCriteriaResolution
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

internal class SlowSyncManager(
    private val syncCriteriaProvider: SyncCriteriaProvider,
    private val slowSyncRepository: SlowSyncRepository,
    private val slowSyncWorker: SlowSyncWorker,
    kaliumDispatcher: KaliumDispatcher = KaliumDispatcherImpl
) {

    private val scope = CoroutineScope(kaliumDispatcher.default.limitedParallelism(1))

    init {
        scope.launch {
            syncCriteriaProvider
                .syncCriteriaFlow()
                .distinctUntilChanged()
                // Collect latest will cancel whatever is running inside the collector when a new value is emitted
                .collectLatest { handleCriteriaResolution(it) }
        }
    }

    private suspend fun handleCriteriaResolution(it: SyncCriteriaResolution) {
        if (it is SyncCriteriaResolution.Ready) {
            // START SYNC
            kaliumLogger.i("Sync starting as all criteria are met")
            slowSyncWorker.performSlowSyncSteps().cancellable().collect { step ->
                slowSyncRepository.updateSlowSyncStatus(SlowSyncStatus.Ongoing(step))
            }
            slowSyncRepository.updateSlowSyncStatus(SlowSyncStatus.Complete)
        } else {
            // STOP SYNC
            kaliumLogger.i("Sync Stopped as criteria are not met: $it")
            slowSyncRepository.updateSlowSyncStatus(SlowSyncStatus.Pending)
        }
    }

}
