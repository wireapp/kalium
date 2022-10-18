package com.wire.kalium.logic.sync.slow

import com.wire.kalium.logger.KaliumLogger.Companion.ApplicationFlow.SYNC
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.data.sync.SlowSyncStatus
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.sync.SyncExceptionHandler
import com.wire.kalium.logic.sync.incremental.IncrementalSyncManager
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

/**
 * Starts and stops SlowSync based on a set of criteria,
 * defined in [SlowSyncCriteriaProvider].
 * Once the criteria are met, this Manager will
 * take care of running SlowSync.
 *
 * Ideally, SlowSync should run only **once** after the
 * initial log-in / client registration. But [IncrementalSyncManager]
 * might invalidate this and request a new
 * SlowSync in case some [Event] is lost.
 * @see IncrementalSyncManager
 */
internal class SlowSyncManager(
    private val slowSyncCriteriaProvider: SlowSyncCriteriaProvider,
    private val slowSyncRepository: SlowSyncRepository,
    private val slowSyncWorker: SlowSyncWorker,
    kaliumDispatcher: KaliumDispatcher = KaliumDispatcherImpl
) {

    private val scope = CoroutineScope(SupervisorJob() + kaliumDispatcher.default.limitedParallelism(1))
    private val logger = kaliumLogger.withFeatureId(SYNC)

    private val coroutineExceptionHandler = SyncExceptionHandler({
        slowSyncRepository.updateSlowSyncStatus(SlowSyncStatus.Pending)
    }, {
        slowSyncRepository.updateSlowSyncStatus(SlowSyncStatus.Failed(it))
        scope.launch {
            delay(RETRY_DELAY)
            startMonitoring()
        }
    })

    init { startMonitoring() }

    private fun startMonitoring() {
        scope.launch(coroutineExceptionHandler) {
            slowSyncCriteriaProvider
                .syncCriteriaFlow()
                .distinctUntilChanged()
                // Collect latest will cancel whatever is running inside the collector when a new value is emitted
                .collectLatest { handleCriteriaResolution(it) }
        }
    }

    private suspend fun handleCriteriaResolution(it: SyncCriteriaResolution) {
        if (it is SyncCriteriaResolution.Ready) {
            // START SYNC
            logger.i("Starting SlowSync as all criteria are met")
            slowSyncWorker.performSlowSyncSteps().cancellable().collect { step ->
                logger.i("Performing SlowSyncStep $step")
                slowSyncRepository.updateSlowSyncStatus(SlowSyncStatus.Ongoing(step))
            }
            slowSyncRepository.updateSlowSyncStatus(SlowSyncStatus.Complete)
        } else {
            // STOP SYNC
            logger.i("SlowSync Stopped as criteria are not met: $it")
            slowSyncRepository.updateSlowSyncStatus(SlowSyncStatus.Pending)
        }
    }

    private companion object {
        val RETRY_DELAY = 10.seconds
    }
}
