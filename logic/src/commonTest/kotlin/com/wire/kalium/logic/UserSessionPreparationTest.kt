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

package com.wire.kalium.logic

import kotlin.test.Test
import kotlin.test.assertSame

class UserSessionPreparationTest {
    @Test
    fun givenTypedFailures_whenCreated_thenEachRetainsOriginalException() {
        val expected = IllegalStateException("database preparation failed")
        val failures = listOf(
            UserSessionPreparationFailure.InsufficientStorage(expected),
            UserSessionPreparationFailure.TemporarilyUnavailable(expected),
            UserSessionPreparationFailure.ApplicationUpdateRequired(expected),
            UserSessionPreparationFailure.SupportRequired(expected),
        )

        failures.forEach { failure -> assertSame(expected, failure.exception) }
    }

    @Test
    fun givenFailure_whenExposedByResultAndState_thenTypedFailureIsRetained() {
        val failure = UserSessionPreparationFailure.SupportRequired(
            IllegalStateException("database preparation failed")
        )

        val result = PrepareUserSessionResult.Failure(failure)
        val state = UserSessionPreparationState.Failed(failure)

        assertSame(failure, result.failure)
        assertSame(failure, state.failure)
    }
}
