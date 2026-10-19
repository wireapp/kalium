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
package com.wire.kalium.logic.sync

import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logger.KaliumLogger.Companion.ApplicationFlow.SYNC
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.TimeSource

/** Stops sync across accounts, including scopes created while an application restart is pending. */
internal class SyncShutdownCoordinator {
    private val logger by lazy { kaliumLogger.withFeatureId(SYNC).withTextTag("SyncShutdownCoordinator") }
    private val mutex = Mutex()
    private var stopping = false
    private val jobs = mutableSetOf<Job>()

    suspend fun runUnlessStopping(block: suspend () -> Unit) = coroutineScope {
        val job = coroutineContext.job
        val canRun = mutex.withLock {
            if (stopping) false else {
                jobs.removeAll { it.isCompleted }
                jobs.add(job)
                true
            }
        }
        if (canRun) {
            block()
        } else {
            logger.i("Skipping sync start: application restart shutdown has been requested")
        }
        // Retain jobs until shutdown so a cancelled job's non-cancellable cleanup is also awaited.
    }

    suspend fun stopForRestart() {
        val startedAt = TimeSource.Monotonic.markNow()
        val runningJobs = mutex.withLock {
            stopping = true
            jobs.toList()
        }
        logger.i("Stopping sync for application restart: ${runningJobs.count { !it.isCompleted }} unfinished jobs")
        runningJobs.forEach { it.cancel() }
        try {
            runningJobs.joinAll()
        } catch (exception: CancellationException) {
            logger.w(
                "Sync shutdown wait cancelled after ${startedAt.elapsedNow()}; " +
                    "${runningJobs.count { !it.isCompleted }} jobs still unfinished"
            )
            throw exception
        }
        logger.i("Sync shutdown completed after ${startedAt.elapsedNow()}: all tracked jobs finished")
    }
}
