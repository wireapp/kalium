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
