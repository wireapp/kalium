/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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

package com.wire.kalium.logic.sync.remoteBackup

import com.wire.kalium.common.functional.fold
import com.wire.kalium.common.logger.kaliumLogger as defaultKaliumLogger
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.sync.incremental.EventProcessingCallback
import com.wire.kalium.network.AppVisibilityObserver
import io.mockative.Mockable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Coordinates cryptographic state backup based on app visibility changes and server event processing.
 *
 * Triggers backup in two scenarios:
 * 1. Immediately when app transitions to/from background
 * 2. After processing server events (debounced by cryptoStateBackupInterval from KaliumConfigs)
 */
@Mockable
interface BackupStateVisibilityCoordinator : EventProcessingCallback {
    /**
     * Starts observing app visibility and coordinating backup operations.
     * Should be called once during session initialization.
     */
    fun start()

    /**
     * Stops observing and cancels any ongoing coordination.
     * Should be called during session cleanup (handled automatically via coroutine cancellation).
     */
    fun stop()

    /**
     * Notifies the coordinator that a server event was processed.
     * Schedules a debounced backup (based on cryptoStateBackupInterval config), cancelling any existing debounce timer.
     */
    override fun onEventProcessed()
}

internal class BackupStateVisibilityCoordinatorImpl(
    private val appVisibilityObserver: AppVisibilityObserver,
    private val backupCryptoStateUseCase: BackupCryptoStateUseCase,
    private val kaliumConfigs: KaliumConfigs,
    private val scope: CoroutineScope,
    kaliumLogger: KaliumLogger = defaultKaliumLogger
) : BackupStateVisibilityCoordinator {

    private val logger = kaliumLogger.withTextTag("BackupStateVisibilityCoordinator")
    private var observerJob: Job? = null
    private var previousVisibility: Boolean? = null
    private var lastUploadedHash: String? = null
    private var debouncedBackupJob: Job? = null

    override fun start() {
        if (!kaliumConfigs.cryptoStateBackupEnabled) {
            return
        }

        if (observerJob?.isActive == true) {
            logger.d("Coordinator already running")
            return
        }

        logger.i("Starting backup state visibility coordinator")

        observerJob = scope.launch {
            appVisibilityObserver.observeAppVisibility()
                .collectLatest { isVisible ->
                    handleVisibilityChange(isVisible)
                }
        }
    }

    override fun stop() {
        observerJob?.cancel()
        observerJob = null
        debouncedBackupJob?.cancel()
        debouncedBackupJob = null
        previousVisibility = null
        lastUploadedHash = null
    }

    override fun onEventProcessed() {
        if (!kaliumConfigs.cryptoStateBackupEnabled) {
            return
        }

        // Schedule debounced backup after processing any event
        scheduleDebouncedBackup()
    }

    private fun scheduleDebouncedBackup() {
        // Cancel any existing debounced backup
        debouncedBackupJob?.cancel()

        logger.d("Scheduling debounced backup in ${kaliumConfigs.cryptoStateBackupInterval.inWholeMinutes} minutes")

        debouncedBackupJob = scope.launch {
            delay(kaliumConfigs.cryptoStateBackupInterval)
            logger.i("Debounced backup timer expired, triggering crypto state backup")
            performBackup("debounced timer")
        }
    }

    private fun cancelDebouncedBackup() {
        if (debouncedBackupJob?.isActive == true) {
            logger.d("Cancelling debounced backup timer")
            debouncedBackupJob?.cancel()
            debouncedBackupJob = null
        }
    }

    private suspend fun handleVisibilityChange(isVisible: Boolean) {
        val previous = previousVisibility
        previousVisibility = isVisible

        // Only trigger on actual transitions, not initial state
        if (previous == null || previous == isVisible) {
            return
        }

        // Trigger backup on any transition
        if (previous != isVisible) {
            val transition = if (isVisible) "background → foreground" else "foreground → background"
            logger.i("App transition detected ($transition), triggering crypto state backup")

            // Cancel any pending debounced backup since we're doing it now
            cancelDebouncedBackup()

            performBackup(transition)
        }
    }

    private suspend fun performBackup(reason: String) {
        backupCryptoStateUseCase(lastUploadedHash).fold(
            { failure ->
                logger.e("Failed to backup crypto state ($reason): $failure")
            },
            { hash ->
                lastUploadedHash = hash
                logger.i("Crypto state backup completed successfully ($reason, hash: ${hash.take(8)}...)")
            }
        )
    }
}
