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

import com.wire.kalium.common.logger.kaliumLogger as defaultKaliumLogger
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.message.sync.DebouncedMessageSyncScheduler
import com.wire.kalium.logic.feature.message.sync.SyncMessagesResult
import com.wire.kalium.logic.feature.message.sync.SyncMessagesUseCase
import com.wire.kalium.logic.sync.UserSessionWorkScheduler
import com.wire.kalium.network.AppVisibilityObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Coordinates message sync operations based on app visibility state.
 *
 * Responsibilities:
 * - Triggers immediate sync on app foreground/background transitions
 * - Starts debounced sync scheduler when app enters foreground
 * - Stops debounced sync scheduler when app enters background
 * - Handles sync failures by scheduling WorkManager retry
 */
interface AppVisibilityAwareSyncCoordinator {
    /**
     * Starts observing app visibility and coordinating sync operations.
     * Should be called once during session initialization.
     */
    fun start()

    /**
     * Stops observing and cancels any ongoing coordination.
     * Should be called during session cleanup (handled automatically via coroutine cancellation).
     */
    fun stop()
}

internal class AppVisibilityAwareSyncCoordinatorImpl(
    private val appVisibilityObserver: AppVisibilityObserver,
    private val syncMessagesUseCase: SyncMessagesUseCase,
    private val debouncedMessageSyncScheduler: DebouncedMessageSyncScheduler,
    private val userSessionWorkScheduler: UserSessionWorkScheduler,
    private val userId: UserId,
    private val scope: CoroutineScope,
    kaliumLogger: KaliumLogger = defaultKaliumLogger
) : AppVisibilityAwareSyncCoordinator {

    private val logger = kaliumLogger.withTextTag("AppVisibilitySyncCoordinator")
    private var observerJob: Job? = null
    private var previousVisibility: Boolean? = null

    override fun start() {
        if (observerJob?.isActive == true) {
            logger.d("Coordinator already running")
            return
        }

        logger.i("Starting app visibility-aware message sync coordinator")

        observerJob = scope.launch {
            appVisibilityObserver.observeAppVisibility()
                .collectLatest { isVisible ->
                    handleVisibilityChange(isVisible)
                }
        }
    }

    override fun stop() {
        logger.i("Stopping app visibility-aware message sync coordinator")
        observerJob?.cancel()
        observerJob = null
        previousVisibility = null
    }

    private suspend fun handleVisibilityChange(isVisible: Boolean) {
        val previous = previousVisibility

        // Only trigger sync on transitions, not the initial state
        if (previous != null && previous != isVisible) {
            val transition = if (isVisible) "background → foreground" else "foreground → background"
            logger.i("App transition detected ($transition), triggering message sync")

            val result = syncMessagesUseCase()

            when (result) {
                is SyncMessagesResult.Success -> {
                    logger.i("Message sync completed successfully")
                }
                is SyncMessagesResult.NothingToSync -> {
                    logger.i("No messages to sync")
                }
                is SyncMessagesResult.Disabled -> {
                    logger.i("Message sync feature disabled")
                }
                is SyncMessagesResult.ApiFailure -> {
                    logger.w("Message sync API failure (${result.statusCode}), scheduling retry")
                    userSessionWorkScheduler.scheduleMessageSyncRetry(userId)
                }
                is SyncMessagesResult.Failure -> {
                    logger.e("Message sync failed, scheduling retry", result.exception)
                    userSessionWorkScheduler.scheduleMessageSyncRetry(userId)
                }
            }
        }

        // Start/stop the debounced scheduler based on app visibility
        if (isVisible) {
            logger.i("App entered foreground, starting debounced message sync scheduler")
            debouncedMessageSyncScheduler.start()
        } else {
            logger.i("App entered background, stopping debounced message sync scheduler")
            debouncedMessageSyncScheduler.stop()
        }

        // Update previous state
        previousVisibility = isVisible
    }
}
