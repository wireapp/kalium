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

package com.wire.kalium.logic.feature.message.sync

import com.wire.kalium.common.logger.kaliumLogger as defaultKaliumLogger
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.data.sync.MessageSyncRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Scheduler that automatically uploads pending messages after a debounce period
 * when new messages are added to the sync queue.
 */
interface DebouncedMessageSyncScheduler {
    /**
     * Starts observing the message sync table and scheduling uploads.
     * Should be called when the app enters foreground.
     */
    fun start()

    /**
     * Stops observing and cancels any pending uploads.
     * Should be called when the app enters background.
     */
    fun stop()
}

internal class DebouncedMessageSyncSchedulerImpl(
    private val messageSyncRepository: MessageSyncRepository,
    private val syncMessagesUseCase: SyncMessagesUseCase,
    private val scope: CoroutineScope,
    private val debounceTime: Duration = 3.seconds,
    private val isFeatureEnabled: Boolean,
    kaliumLogger: KaliumLogger = defaultKaliumLogger
) : DebouncedMessageSyncScheduler {

    private val logger = kaliumLogger.withTextTag("DebouncedMessageSync")
    private var observerJob: Job? = null

    override fun start() {
        if (!isFeatureEnabled) {
            logger.i("Feature disabled, not starting scheduler")
            return
        }

        if (observerJob?.isActive == true) {
            logger.d("Scheduler already running")
            return
        }

        logger.i("Starting debounced message sync scheduler with ${debounceTime.inWholeSeconds}s debounce")

        observerJob = scope.launch {
            messageSyncRepository.observePendingMessagesCount()
                .distinctUntilChanged() // Only emit when count actually changes
                .filter { count -> count > 0 } // Only proceed if there are messages to sync
                .debounce(debounceTime) // Wait for debounce period of inactivity
                .collectLatest { count ->
                    logger.i("Debounce timer expired with $count pending messages, triggering sync")
                    triggerSync()
                }
        }
    }

    override fun stop() {
        logger.i("Stopping debounced message sync scheduler")
        observerJob?.cancel()
        observerJob = null
    }

    private suspend fun triggerSync() {
        try {
            val result = syncMessagesUseCase()
            when (result) {
                is SyncMessagesResult.Success -> {
                    logger.i("Sync completed successfully")
                }
                is SyncMessagesResult.NothingToSync -> {
                    logger.d("No messages to sync")
                }
                is SyncMessagesResult.Disabled -> {
                    logger.i("Sync disabled")
                }
                is SyncMessagesResult.ApiFailure -> {
                    logger.w("Sync API failure: ${result.statusCode} - ${result.message}")
                }
                is SyncMessagesResult.Failure -> {
                    logger.e("Sync failed with exception: ${result.exception.message}")
                }
            }
        } catch (e: Exception) {
            logger.e("Unexpected error during sync: ${e.message}", e)
        }
    }
}
