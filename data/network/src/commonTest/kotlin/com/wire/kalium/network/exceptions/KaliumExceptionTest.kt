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

package com.wire.kalium.network.exceptions

import com.wire.kalium.network.api.model.GenericAPIErrorResponse
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KaliumExceptionTest {

    @Test
    fun given429InvalidRequest_whenCheckingTooManyRequests_thenReturnsTrue() {
        val subject = KaliumException.InvalidRequestError(
            GenericAPIErrorResponse(HttpStatusCode.TooManyRequests.value, "too-many-requests", "Please try again later.")
        )

        assertTrue(subject.isTooManyRequests())
    }

    @Test
    fun given420InvalidRequest_whenCheckingTooManyRequests_thenReturnsTrue() {
        val subject = KaliumException.InvalidRequestError(
            GenericAPIErrorResponse(420, "unknown status code", "nginx throttled request")
        )

        assertTrue(subject.isTooManyRequests())
    }

    @Test
    fun givenNonThrottleInvalidRequest_whenCheckingTooManyRequests_thenReturnsFalse() {
        val subject = KaliumException.InvalidRequestError(
            GenericAPIErrorResponse(HttpStatusCode.BadRequest.value, "bad-request", "Bad request")
        )

        assertFalse(subject.isTooManyRequests())
    }

    @Test
    fun givenMeetingPasswordError_whenCheckingWrongPassword_thenAcceptForbiddenAndKnownBadRequest() {
        val errors = listOf(
            GenericAPIErrorResponse(
                code = HttpStatusCode.Forbidden.value,
                message = "Invalid meeting password",
                label = NetworkErrorLabel.WRONG_MEETING_PASSWORD,
            ),
            GenericAPIErrorResponse(
                code = HttpStatusCode.BadRequest.value,
                message = "Error parsing query parameter password failed: Invalid password",
                label = NetworkErrorLabel.BAD_REQUEST,
            ),
        )

        for (error in errors) {
            assertTrue(KaliumException.InvalidRequestError(error).isWrongMeetingPassword())
        }
    }

    @Test
    fun givenUnrelatedError_whenCheckingWrongMeetingPassword_thenReturnFalse() {
        val errors = listOf(
            GenericAPIErrorResponse(
                code = HttpStatusCode.BadRequest.value,
                message = "Error parsing query parameter code failed: Invalid code",
                label = NetworkErrorLabel.BAD_REQUEST,
            ),
            GenericAPIErrorResponse(
                code = HttpStatusCode.BadRequest.value,
                message = "Password query parameter is not allowed",
                label = NetworkErrorLabel.BAD_REQUEST,
            ),
            GenericAPIErrorResponse(
                code = HttpStatusCode.BadRequest.value,
                message = "Invalid meeting password",
                label = NetworkErrorLabel.WRONG_MEETING_PASSWORD,
            ),
            GenericAPIErrorResponse(
                code = HttpStatusCode.Forbidden.value,
                message = "Error parsing query parameter password failed: Invalid password",
                label = NetworkErrorLabel.ACCESS_DENIED,
            ),
        )

        for (error in errors) {
            assertFalse(KaliumException.InvalidRequestError(error).isWrongMeetingPassword())
        }
    }
}
