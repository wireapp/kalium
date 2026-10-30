/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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
package com.wire.kalium.cells.data

import com.wire.kalium.cells.sdk.kmp.model.JobsTaskStatus
import com.wire.kalium.cells.sdk.kmp.model.RestActionStatus
import com.wire.kalium.cells.sdk.kmp.model.RestBackgroundAction
import com.wire.kalium.cells.sdk.kmp.model.RestPerformActionResponse
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.utils.NetworkResponse
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

private val POLL_INTERVAL = 500.milliseconds
private val TIMEOUT = 2.minutes
private const val MAX_CONSECUTIVE_STATUS_FAILURES = 3

/**
 * Starts a node action and waits for the background jobs it runs, if any, by polling their status.
 *
 * Asking the server to await the jobs instead doesn't work for long ones, e.g. moving many nodes: past the await
 * timeout, the server answers 408 while the jobs go on, and the HTTP client silently resends the request, which then
 * fails because the nodes have already moved.
 *
 * @param start sends the action, without asking the server to await it.
 * @param getStatus gets the status of one of the jobs the action runs.
 * @return success once every job finished, the failure of the action, of a job, or of getting its status, or a failure
 * if the jobs didn't finish within [timeout]: they may still finish later.
 */
@Suppress("ReturnCount")
internal suspend fun awaitBackgroundAction(
    start: suspend () -> NetworkResponse<RestPerformActionResponse>,
    getStatus: suspend (jobUuid: String) -> NetworkResponse<RestBackgroundAction>,
    pollInterval: Duration = POLL_INTERVAL,
    timeout: Duration = TIMEOUT,
): NetworkResponse<Unit> {
    val response = when (val started = start()) {
        is NetworkResponse.Error -> return started
        is NetworkResponse.Success -> started.value
    }
    val pendingJobUuids = response.backgroundActions.orEmpty()
        .filter { it.status != JobsTaskStatus.Finished }
        .map { it.jobUuid }
    // Performed right away, or nothing left to wait for.
    if (response.status != RestActionStatus.Background || pendingJobUuids.isEmpty()) return success()

    return withTimeoutOrNull(timeout) {
        pendingJobUuids.firstNotNullOfOrNull { jobUuid -> awaitJob(jobUuid, getStatus, pollInterval) } ?: success()
    } ?: NetworkResponse.Error(KaliumException.GenericError(BackgroundActionException("Still running after $timeout")))
}

/** @return null once the job finished, the failure otherwise. */
@Suppress("ReturnCount")
private suspend fun awaitJob(
    jobUuid: String,
    getStatus: suspend (jobUuid: String) -> NetworkResponse<RestBackgroundAction>,
    pollInterval: Duration,
): NetworkResponse.Error? {
    var consecutiveFailures = 0
    while (true) {
        delay(pollInterval)
        val job = when (val status = getStatus(jobUuid)) {
            is NetworkResponse.Success -> status.value.also { consecutiveFailures = 0 }
            // A failed status request says nothing about the job: give it a few more tries.
            is NetworkResponse.Error -> {
                consecutiveFailures++
                if (consecutiveFailures < MAX_CONSECUTIVE_STATUS_FAILURES) continue else return status
            }
        }
        when (job.status) {
            JobsTaskStatus.Finished -> return null
            JobsTaskStatus.Error, JobsTaskStatus.Interrupted -> return NetworkResponse.Error(
                KaliumException.GenericError(BackgroundActionException(job.statusMessage ?: "Job ${job.status}"))
            )

            else -> Unit // Still queued or running.
        }
    }
}

private fun success() = NetworkResponse.Success(Unit, emptyMap(), HttpStatusCode.OK.value)

internal class BackgroundActionException(message: String) : Exception(message)
