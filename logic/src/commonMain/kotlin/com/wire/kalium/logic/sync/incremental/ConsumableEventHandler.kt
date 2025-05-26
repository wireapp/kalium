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
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Handles the scheduling info about consumable events.
 * It is used to manage the catching up job that processes events when the websocket is opened to mimic the behavior of
 * the old quick sync that was used to fetch pending events from the notification stream.
 */
internal class ConsumableEventHandler(private val processingScope: CoroutineScope) {
    private var websocketOpenedAt: Instant? = null
    private var lastEventReceivedAt: Instant? = null
    private var catchingUpJob: Job? = null
    private val mutex = Mutex()

    /**
     * Start the processing of the catching up job.
     *
     * @param interval The delay after just opening the websocket connection. Default is [CATCHING_UP_JOB_INITIAL_THRESHOLD].
     */
    suspend fun createNewCatchingUpJob(interval: Duration = CATCHING_UP_JOB_INITIAL_THRESHOLD, task: () -> Unit) = mutex.withLock {
        websocketOpenedAt = Clock.System.now()
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
     * Scheduled to run once with a delay of [CATCHING_UP_JOB_EVENT_INTERVAL].
     *
     * @param interval The delay between each event processing. Default is [CATCHING_UP_JOB_EVENT_INTERVAL].
     * @param onTimeReachedTask The task to be executed when the time reached. Used for marking the status of fetching events as LIVE.
     */
    suspend fun scheduleNewCatchingUpJob(interval: Duration = CATCHING_UP_JOB_EVENT_INTERVAL, onTimeReachedTask: () -> Unit) =
        mutex.withLock {
            lastEventReceivedAt = Clock.System.now()
            catchingUpJob?.cancel()
            catchingUpJob = processingScope.launch {
                while (isActive) {
                    delay(interval)
                    onTimeReachedTask()
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
        websocketOpenedAt = null
        lastEventReceivedAt = null
    }

    override fun toString(): String {
        return "ConsumableEventHandler(websocketOpenedAt=$websocketOpenedAt, " +
                "lastEventReceivedAt=$lastEventReceivedAt, catchingUpJob.isActive=${catchingUpJob?.isActive == true})"
    }

    companion object {
        /**
         * Setting this up to 5 secs, as a threshold to wait for the websocket to be opened
         */
        val CATCHING_UP_JOB_INITIAL_THRESHOLD = 5.seconds

        /**
         * Interval in between each event process threshold.
         */
        val CATCHING_UP_JOB_EVENT_INTERVAL = 1.seconds
    }
}
