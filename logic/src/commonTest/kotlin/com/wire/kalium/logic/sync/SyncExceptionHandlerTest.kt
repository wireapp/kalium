/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
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

import com.wire.kalium.logic.CoreFailure
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.test.runTest
import okio.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class SyncExceptionHandlerTest {

    @Test
    fun givenCancellationException_whenHandlingException_thenShouldCallOnCancellation() = runTest {
        val exception = CancellationException()
        val (arrangement, syncExceptionHandler) = Arrangement().arrange()

        syncExceptionHandler.handleException(currentCoroutineContext(), exception)

        assertEquals(1, arrangement.onCancellationCalledCount)
    }

    @Test
    fun givenCancellationException_whenHandlingException_thenShouldNotCallOnFailure() = runTest {
        val exception = CancellationException()
        val (arrangement, syncExceptionHandler) = Arrangement().arrange()

        syncExceptionHandler.handleException(currentCoroutineContext(), exception)

        assertEquals(0, arrangement.onFailureCalledCount)
    }

    @Test
    fun givenNonCancellationException_whenHandlingException_thenShouldNotCallOnCancellation() = runTest {
        val exception = IOException()
        val (arrangement, syncExceptionHandler) = Arrangement().arrange()

        syncExceptionHandler.handleException(currentCoroutineContext(), exception)

        assertEquals(0, arrangement.onCancellationCalledCount)
    }

    @Test
    fun givenNonCancellationException_whenHandlingException_thenShouldCallOnFailure() = runTest {
        val exception = IOException()
        val (arrangement, syncExceptionHandler) = Arrangement().arrange()

        syncExceptionHandler.handleException(currentCoroutineContext(), exception)

        assertEquals(1, arrangement.onFailureCalledCount)
    }

    @Test
    fun givenSyncException_whenHandlingException_thenShouldCallOnFailureWithCauseCoreFailure() = runTest {
        val coreFailure = CoreFailure.MissingClientRegistration
        val exception = KaliumSyncException("Oops", coreFailure)
        val (arrangement, syncExceptionHandler) = Arrangement().arrange()

        syncExceptionHandler.handleException(currentCoroutineContext(), exception)

        assertContains(arrangement.onFailureCalledArguments, coreFailure)
    }

    @Test
    fun givenNonSyncAndNonCancellationException_whenHandlingException_thenShouldCallOnFailureWithUnknownCoreFailure() = runTest {
        val exception = IOException()
        val (arrangement, syncExceptionHandler) = Arrangement().arrange()

        syncExceptionHandler.handleException(currentCoroutineContext(), exception)

        assertContains(arrangement.onFailureCalledArguments, CoreFailure.Unknown(exception))
    }

    private class Arrangement {

        val onFailureCalledArguments = mutableListOf<CoreFailure>()
        val onFailureCalledCount: Int get() = onFailureCalledArguments.size

        var onCancellationCalledCount = 0

        private val syncExceptionHandler = SyncExceptionHandler({
            onCancellationCalledCount++
        }, {
            onFailureCalledArguments += it
        })

        fun arrange() = this to syncExceptionHandler
    }

}
