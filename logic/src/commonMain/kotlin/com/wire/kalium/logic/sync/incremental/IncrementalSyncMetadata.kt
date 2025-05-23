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

internal class IncrementalSyncMetadata(private val processingScope: CoroutineScope) {
    private var lastTimeWebsocketOpened: Instant? = null
    private var lastTimeWebsocketEventReceived: Instant? = null
    private var catchingUpJob: Job? = null
    private val mutex = Mutex()

    suspend fun createNewCatchingUpJob(interval: Long = CATCHING_UP_JOB_INTERVAL_IN_MS, task: () -> Unit) = mutex.withLock {
        lastTimeWebsocketOpened = Clock.System.now()
        catchingUpJob?.cancel()
        catchingUpJob = processingScope.launch {
            launch {
                while (isActive) {
                    delay(interval)
                    task()
                }
            }
        }
    }

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

    suspend fun clear() = mutex.withLock {
        catchingUpJob?.cancel()
        catchingUpJob = null
        lastTimeWebsocketOpened = null
        lastTimeWebsocketEventReceived = null
    }

    companion object {
        const val CATCHING_UP_JOB_INTERVAL_IN_MS = 5_000L
    }
}
