package com.wire.kalium.logic.sync.slow

import com.wire.kalium.logger.KaliumLogger.Companion.ApplicationFlow.SYNC
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.data.sync.SlowSyncStatus
import com.wire.kalium.logic.functional.combine
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
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.days
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

    init {
        startMonitoring()
    }

    private fun startMonitoring() {
        scope.launch(coroutineExceptionHandler) {
            slowSyncCriteriaProvider
                .syncCriteriaFlow()
                .distinctUntilChanged()
                .combine(slowSyncRepository.observeLastSlowSyncCompletionInstant())
                // Collect latest will cancel whatever is running inside the collector when a new value is emitted
                .collectLatest { (syncCriteriaResolution, lastTimeSlowSyncWasPerformed) ->
                    handleCriteriaResolution(syncCriteriaResolution, lastTimeSlowSyncWasPerformed)
                }
        }
    }

    private suspend fun handleCriteriaResolution(syncCriteriaResolution: SyncCriteriaResolution, lastTimeSlowSyncWasPerformed: Instant?) {
        if (syncCriteriaResolution is SyncCriteriaResolution.Ready) {
            // START SYNC IF NEEDED
            logger.i("SlowSync criteria ready, checking if SlowSync is needed or already performed")
            logger.i("Last SlowSync was performed on '$lastTimeSlowSyncWasPerformed'")
            if (isSlowSyncNeeded(lastTimeSlowSyncWasPerformed)) {
                logger.i("Starting SlowSync as all criteria are met and it wasn't performed recently")
                performSlowSync()
            } else {
                logger.i("No need to perform SlowSync. Marking as Complete")
            }
            slowSyncRepository.updateSlowSyncStatus(SlowSyncStatus.Complete)
            slowSyncRepository.setLastSlowSyncCompletionInstant(Clock.System.now())
        } else {
            // STOP SYNC
            logger.i("SlowSync Stopped as criteria are not met: $syncCriteriaResolution")
            slowSyncRepository.updateSlowSyncStatus(SlowSyncStatus.Pending)
        }
    }

    private fun isSlowSyncNeeded(lastTimeSlowSyncWasPerformed: Instant?): Boolean {
        return lastTimeSlowSyncWasPerformed?.let {
            val currentTime = Clock.System.now()
            val nextSlowSyncDateTime = lastTimeSlowSyncWasPerformed + MIN_TIME_BETWEEN_SLOW_SYNCS
            logger.i("Next SlowSync should be performed on '$nextSlowSyncDateTime'")
            currentTime > nextSlowSyncDateTime
        } ?: true
    }

    private suspend fun performSlowSync() {
        slowSyncWorker.performSlowSyncSteps().cancellable().collect { step ->
            logger.i("Performing SlowSyncStep $step")
            slowSyncRepository.updateSlowSyncStatus(SlowSyncStatus.Ongoing(step))
        }
    }

    private companion object {
        val RETRY_DELAY = 10.seconds
        val MIN_TIME_BETWEEN_SLOW_SYNCS = 7.days
    }
}
