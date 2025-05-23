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
package com.wire.kalium.logic.sync.incremental

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * Holds the metadata for the incremental sync process of the new async incremental sync.
 * Helping to keep track of the last time the websocket was opened and the last time an event was received and Job for stop catching up.
 */
internal class AsyncIncrementalSyncMetadata(private val processingScope: CoroutineScope) {
    private var lastTimeWebsocketOpened: Instant? = null
    private var lastTimeWebsocketEventReceived: Instant? = null
    private var catchingUpJob: Job? = null
    private val mutex = Mutex()

    /**
     * Start the processing of the catching up job.
     */
    suspend fun createNewCatchingUpJob(interval: Long = CATCHING_UP_JOB_INTERVAL_IN_MS, task: () -> Unit) = mutex.withLock {
        lastTimeWebsocketOpened = Clock.System.now()
        catchingUpJob?.cancel()
        catchingUpJob = processingScope.launch {
            while (isActive) {
                delay(interval)
                task()
            }
        }
    }

    /**
     * Schedule a new catching up job that will be cancelled if there was already one pending scheduled.
     * Scheduled to run once with a delay of [CATCHING_UP_JOB_INTERVAL_IN_MS] milliseconds.
     */
    suspend fun scheduleNewCatchingUpJob(interval: Long = CATCHING_UP_JOB_INTERVAL_IN_MS, task: () -> Unit) = mutex.withLock {
        lastTimeWebsocketEventReceived = Clock.System.now()
        catchingUpJob?.cancel()
        catchingUpJob = processingScope.launch {
            while (isActive) {
                delay(interval)
                task()
            }
        }
    }

    /**
     * Cancel the catching up job and reset the last time the websocket was opened and the last time
     * an event was received.To be called when the websocket is closed.
     */
    suspend fun clear() = mutex.withLock {
        catchingUpJob?.cancel()
        catchingUpJob = null
        lastTimeWebsocketOpened = null
        lastTimeWebsocketEventReceived = null
    }

    companion object {
        /**
         * Setting this up to 5 secs. 0.5 is too little, and this only runs once.
         */
        const val CATCHING_UP_JOB_INTERVAL_IN_MS = 5000L
    }
}
