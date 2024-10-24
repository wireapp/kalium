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

package com.wire.kalium.logic.sync.slow

import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logger.KaliumLogger.Companion.ApplicationFlow.SYNC
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.data.sync.SlowSyncStatus
import com.wire.kalium.logic.functional.combine
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.sync.SyncExceptionHandler
import com.wire.kalium.logic.sync.incremental.IncrementalSyncManager
import com.wire.kalium.logic.sync.slow.migration.SyncMigrationStepsProvider
import com.wire.kalium.logic.sync.slow.migration.steps.SyncMigrationStep
import com.wire.kalium.logic.util.ExponentialDurationHelper
import com.wire.kalium.logic.util.ExponentialDurationHelperImpl
import com.wire.kalium.network.NetworkStateObserver
import com.wire.kalium.util.DateTimeUtil
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
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
@Suppress("LongParameterList")
internal class SlowSyncManager(
    private val slowSyncCriteriaProvider: SlowSyncCriteriaProvider,
    private val slowSyncRepository: SlowSyncRepository,
    private val slowSyncWorker: SlowSyncWorker,
    private val slowSyncRecoveryHandler: SlowSyncRecoveryHandler,
    private val networkStateObserver: NetworkStateObserver,
    private val syncMigrationStepsProvider: () -> SyncMigrationStepsProvider,
    logger: KaliumLogger = kaliumLogger,
    kaliumDispatcher: KaliumDispatcher = KaliumDispatcherImpl,
    private val exponentialDurationHelper: ExponentialDurationHelper = ExponentialDurationHelperImpl(
        MIN_RETRY_DELAY,
        MAX_RETRY_DELAY
    )
) {

    @OptIn(ExperimentalCoroutinesApi::class)
    private val scope =
        CoroutineScope(SupervisorJob() + kaliumDispatcher.default.limitedParallelism(1))
    private val logger = logger.withFeatureId(SYNC)

    private val coroutineExceptionHandler = SyncExceptionHandler(
        onCancellation = {
            slowSyncRepository.updateSlowSyncStatus(SlowSyncStatus.Pending)
        },
        onFailure = { failure ->
            logger.i("SlowSync ExceptionHandler error $failure")
            scope.launch {
                val delay = exponentialDurationHelper.next()
                slowSyncRepository.updateSlowSyncStatus(SlowSyncStatus.Failed(failure, delay))
                slowSyncRecoveryHandler.recover(failure) {
                    logger.i("SlowSync Triggering delay($delay) and waiting for reconnection")
                    networkStateObserver.delayUntilConnectedWithInternetAgain(delay)
                    logger.i("SlowSync Delay and waiting for connection finished - retrying")
                    logger.i("SlowSync Connected - retrying")
                    startMonitoring()
                }
            }
        }
    )

    init {
        startMonitoring()
    }

    private suspend fun isSlowSyncNeededFlow(): Flow<SlowSyncParam> =
        slowSyncRepository.observeLastSlowSyncCompletionInstant()
            .map { latestSlowSync ->
                logger.i("Last SlowSync was performed on '$latestSlowSync'")
                val lastVersion = slowSyncRepository.getSlowSyncVersion()
                when {
                    (lastVersion != null) && (CURRENT_VERSION > lastVersion) -> {
                        logger.i("Last saved SlowSync version is $lastVersion, current is $CURRENT_VERSION")
                        SlowSyncParam.MigrationNeeded(
                            oldVersion = lastVersion,
                            newVersion = CURRENT_VERSION
                        )
                    }

                    latestSlowSync == null -> {
                        SlowSyncParam.NotPerformedBefore
                    }

                    DateTimeUtil.currentInstant() > (latestSlowSync + MIN_TIME_BETWEEN_SLOW_SYNCS) -> {
                        logger.i("Slow sync too old - last slow sync was performed on '$latestSlowSync'")
                        SlowSyncParam.LastSlowSyncTooOld
                    }

                    else -> {
                        SlowSyncParam.Success
                    }
                }
            }

    private fun startMonitoring() {
        scope.launch(coroutineExceptionHandler) {
            slowSyncCriteriaProvider
                .syncCriteriaFlow()
                .combine(isSlowSyncNeededFlow())
                .distinctUntilChanged()
                // Collect latest will cancel whatever is running inside the collector when a new value is emitted
                .collectLatest { (syncCriteriaResolution, isSlowSyncNeeded) ->
                    handleCriteriaResolution(syncCriteriaResolution, isSlowSyncNeeded)
                }
        }
    }

    private suspend fun handleCriteriaResolution(
        syncCriteriaResolution: SyncCriteriaResolution,
        isSlowSyncNeeded: SlowSyncParam
    ) {
        if (syncCriteriaResolution is SyncCriteriaResolution.Ready) {
            // START SYNC IF NEEDED
            logger.i("SlowSync criteria ready, checking if SlowSync is needed or already performed")

            when (isSlowSyncNeeded) {
                SlowSyncParam.LastSlowSyncTooOld,
                SlowSyncParam.NotPerformedBefore -> {
                    performSlowSync(emptyList())
                }

                is SlowSyncParam.MigrationNeeded -> {
                    val migrationSteps = syncMigrationStepsProvider()
                        .getMigrationSteps(
                            isSlowSyncNeeded.oldVersion,
                            isSlowSyncNeeded.newVersion
                        )
                    performSlowSync(
                        migrationSteps = migrationSteps
                    )
                }

                SlowSyncParam.Success -> {
                    logger.i("No need to perform SlowSync. Marking as Complete")
                }
            }

            exponentialDurationHelper.reset()
            slowSyncRepository.updateSlowSyncStatus(SlowSyncStatus.Complete)
        } else {
            // STOP SYNC
            logger.i("SlowSync Stopped as criteria are not met: $syncCriteriaResolution")
            slowSyncRepository.updateSlowSyncStatus(SlowSyncStatus.Pending)
        }
    }

    private suspend fun performSlowSync(migrationSteps: List<SyncMigrationStep>) {
        logger.i("Starting SlowSync as all criteria are met and it wasn't performed recently")
        slowSyncWorker.slowSyncStepsFlow(migrationSteps).cancellable().collect { step ->
            logger.i("Performing SlowSyncStep $step")
            slowSyncRepository.updateSlowSyncStatus(SlowSyncStatus.Ongoing(step))
        }
        logger.i("SlowSync completed. Updating last completion instant")
        slowSyncRepository.setSlowSyncVersion(CURRENT_VERSION)
        slowSyncRepository.setLastSlowSyncCompletionInstant(DateTimeUtil.currentInstant())
    }

    private companion object {
        /**
         * The current version of Slow Sync.
         *
         * By bumping this version, we can force all clients to perform a new Slow Sync.
         * Useful when a new step is added to Slow Sync, or when we fix some bug in Slow Sync,
         * and we'd like to get all users to take advantage of the fix.
         */
        const val CURRENT_VERSION = 7

        val MIN_RETRY_DELAY = 1.seconds
        val MAX_RETRY_DELAY = 10.minutes
        val MIN_TIME_BETWEEN_SLOW_SYNCS = 7.days
    }
}
