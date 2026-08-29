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

package com.wire.kalium.eventprocessing

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DefaultEventBatchProcessorTest {

    @Test
    fun givenBatch_whenProcessing_thenRunMutedWrapsOrderedProcessingAndFlushInsideOneTransaction() = runTest {
        val runtime = RecordingRuntime()
        val processor = DefaultEventBatchProcessor(runtime)

        processor.processEvents(listOf("event-1", "event-2"))

        assertEquals(1, runtime.transactionCount)
        assertEquals(
            listOf(
                "transaction-start",
                "muted-start",
                "process:event-1",
                "process:event-2",
                "flush",
                "muted-complete",
                "transaction-complete",
                "marker:event-1,event-2",
            ),
            runtime.callOrder,
        )
    }

    @Test
    fun givenProcessingFailure_whenProcessing_thenEagerMappingAndFailureMappingArePreserved() = runTest {
        val failure = CoreFailure.Unknown(null)
        val runtime = RecordingRuntime().apply {
            processEvent = { event ->
                if (event == "event-1") Either.Left(failure) else Either.Right(event)
            }
        }
        val processor = DefaultEventBatchProcessor(runtime)

        val exception = assertFailsWith<ProcessingException> {
            processor.processEvents(listOf("event-1", "event-2"))
        }

        assertEquals(failure, exception.failure)
        assertEquals(listOf("event-1", "event-2"), runtime.processedEvents)
        assertTrue("flush" !in runtime.callOrder)
        assertTrue(runtime.markedEventIds.isEmpty())
    }

    @Test
    fun givenTransactionFailsAfterBlock_whenProcessing_thenMarkerIsNotWritten() = runTest {
        val failure = CoreFailure.Unknown(null)
        val runtime = RecordingRuntime().apply { transactionFailureAfterBlock = failure }
        val processor = DefaultEventBatchProcessor(runtime)

        val exception = assertFailsWith<ProcessingException> {
            processor.processEvents(listOf("event-1"))
        }

        assertEquals(failure, exception.failure)
        assertTrue(runtime.markedEventIds.isEmpty())
    }

    @Test
    fun givenMarkerFailure_whenProcessing_thenFailureRemainsIgnored() = runTest {
        val runtime = RecordingRuntime().apply { markerSucceeds = false }
        val processor = DefaultEventBatchProcessor(runtime)

        processor.processEvents(listOf("event-1"))

        assertEquals(listOf("event-1"), runtime.markedEventIds)
    }

    @Test
    fun givenCancellationDuringProcessing_whenProcessing_thenBroadNonCancellableScopeFinishesMarkerWrite() = runTest {
        val processingStarted = CompletableDeferred<Unit>()
        val allowProcessingToFinish = CompletableDeferred<Unit>()
        val runtime = RecordingRuntime().apply {
            processEvent = { event ->
                processingStarted.complete(Unit)
                allowProcessingToFinish.await()
                Either.Right(event)
            }
        }
        val processor = DefaultEventBatchProcessor(runtime)

        val job = launch {
            processor.processEvents(listOf("event-1"))
        }

        processingStarted.await()
        job.cancel()
        allowProcessingToFinish.complete(Unit)
        job.join()

        assertEquals(listOf("event-1"), runtime.markedEventIds)
    }

    private class RecordingRuntime : EventBatchProcessingRuntime<String, Unit> {
        val callOrder = mutableListOf<String>()
        val processedEvents = mutableListOf<String>()
        val markedEventIds = mutableListOf<String>()
        var transactionCount = 0
        var transactionFailureAfterBlock: CoreFailure? = null
        var markerSucceeds = true
        var processEvent: suspend (String) -> Either<CoreFailure, String?> = { Either.Right(it) }

        override suspend fun transaction(
            block: suspend (Unit) -> Either<CoreFailure, List<String>>
        ): Either<CoreFailure, List<String>> {
            transactionCount++
            callOrder += "transaction-start"
            val result = block(Unit)
            callOrder += "transaction-complete"
            return transactionFailureAfterBlock?.let { Either.Left(it) } ?: result
        }

        override suspend fun runMuted(
            block: suspend () -> Either<CoreFailure, List<String>>
        ): Either<CoreFailure, List<String>> {
            callOrder += "muted-start"
            return block().also { callOrder += "muted-complete" }
        }

        override suspend fun processEvent(
            transactionContext: Unit,
            event: String,
        ): Either<CoreFailure, String?> {
            callOrder += "process:$event"
            processedEvents += event
            return processEvent(event)
        }

        override suspend fun flushPendingSideEffects(): Either<CoreFailure, Unit> {
            callOrder += "flush"
            return Either.Right(Unit)
        }

        override suspend fun markEventsAsProcessed(eventIds: List<String>): Boolean {
            callOrder += "marker:${eventIds.joinToString(",")}"
            markedEventIds += eventIds
            return markerSucceeds
        }

        override fun processingException(failure: CoreFailure): Throwable = ProcessingException(failure)
    }

    private class ProcessingException(val failure: CoreFailure) : RuntimeException()
}
