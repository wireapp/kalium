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
import com.wire.kalium.network.api.model.GenericAPIErrorResponse
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.utils.NetworkResponse
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CellsBackgroundActionTest {

    @Test
    fun givenAnActionPerformedRightAway_whenAwaiting_thenSucceedWithoutPolling() = runTest {
        val statusRequests = mutableListOf<String>()

        val result = awaitBackgroundAction(
            start = { success(RestPerformActionResponse(status = RestActionStatus.Performed)) },
            getStatus = { statusRequests += it; failure(IllegalStateException("not expected")) },
        )

        assertIs<NetworkResponse.Success<Unit>>(result)
        assertEquals(emptyList(), statusRequests)
    }

    @Test
    fun givenABackgroundJob_whenItFinishes_thenSucceedOncePolledAsFinished() = runTest {
        val statuses = ArrayDeque(listOf(JobsTaskStatus.Queued, JobsTaskStatus.Running, JobsTaskStatus.Finished))
        val statusRequests = mutableListOf<String>()

        val result = awaitBackgroundAction(
            start = { success(backgroundResponse(JobsTaskStatus.Running)) },
            getStatus = { jobUuid -> statusRequests += jobUuid; success(job(statuses.removeFirst())) },
        )

        assertIs<NetworkResponse.Success<Unit>>(result)
        assertEquals(listOf(JOB_UUID, JOB_UUID, JOB_UUID), statusRequests)
    }

    @Test
    fun givenABackgroundJobAlreadyFinished_whenAwaiting_thenSucceedWithoutPolling() = runTest {
        val statusRequests = mutableListOf<String>()

        val result = awaitBackgroundAction(
            start = { success(backgroundResponse(JobsTaskStatus.Finished)) },
            getStatus = { statusRequests += it; failure(IllegalStateException("not expected")) },
        )

        assertIs<NetworkResponse.Success<Unit>>(result)
        assertEquals(emptyList(), statusRequests)
    }

    @Test
    fun givenABackgroundJobThatFails_whenAwaiting_thenFail() = runTest {
        val result = awaitBackgroundAction(
            start = { success(backgroundResponse(JobsTaskStatus.Running)) },
            getStatus = { success(job(JobsTaskStatus.Error, statusMessage = "Target exists")) },
        )

        val cause = assertIs<KaliumException.GenericError>(assertIs<NetworkResponse.Error>(result).kException).cause
        assertEquals("Target exists", assertIs<BackgroundActionException>(cause).message)
    }

    @Test
    fun givenTheActionIsRejected_whenAwaiting_thenFailWithoutPolling() = runTest {
        val rejected = failure(KaliumException.ServerError(GenericAPIErrorResponse(404, "", "")))
        val statusRequests = mutableListOf<String>()

        val result = awaitBackgroundAction(
            start = { rejected },
            getStatus = { statusRequests += it; success(job(JobsTaskStatus.Finished)) },
        )

        assertEquals(rejected, result)
        assertEquals(emptyList(), statusRequests)
    }

    @Test
    fun givenAFewFailedStatusRequests_whenTheJobFinishes_thenSucceed() = runTest {
        val statuses = ArrayDeque(
            listOf(statusError(), statusError(), success(job(JobsTaskStatus.Finished)))
        )

        val result = awaitBackgroundAction(
            start = { success(backgroundResponse(JobsTaskStatus.Running)) },
            getStatus = { statuses.removeFirst() },
        )

        assertIs<NetworkResponse.Success<Unit>>(result)
    }

    @Test
    fun givenStatusRequestsThatKeepFailing_whenAwaiting_thenFailWithTheirError() = runTest {
        val failure = statusError()

        val result = awaitBackgroundAction(
            start = { success(backgroundResponse(JobsTaskStatus.Running)) },
            getStatus = { failure },
        )

        assertEquals(failure, result)
    }

    @Test
    fun givenAJobThatNeverFinishes_whenAwaiting_thenFailOnceTimedOut() = runTest {
        val result = awaitBackgroundAction(
            start = { success(backgroundResponse(JobsTaskStatus.Running)) },
            getStatus = { success(job(JobsTaskStatus.Running)) },
        )

        val cause = assertIs<KaliumException.GenericError>(assertIs<NetworkResponse.Error>(result).kException).cause
        assertIs<BackgroundActionException>(cause)
    }

    private companion object {
        const val JOB_UUID = "jobUuid"

        fun <T : Any> success(value: T) = NetworkResponse.Success(value, emptyMap(), 200)
        fun failure(exception: Exception) = NetworkResponse.Error(
            exception as? KaliumException ?: KaliumException.GenericError(exception)
        )
        fun statusError() = failure(KaliumException.ServerError(GenericAPIErrorResponse(502, "", "")))

        fun job(status: JobsTaskStatus, statusMessage: String? = null) =
            RestBackgroundAction(jobUuid = JOB_UUID, name = "move", status = status, statusMessage = statusMessage)

        fun backgroundResponse(jobStatus: JobsTaskStatus) = RestPerformActionResponse(
            status = RestActionStatus.Background,
            backgroundActions = listOf(job(jobStatus)),
        )
    }
}
