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

package com.wire.kalium.logic.sync.incremental

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.cryptography.CryptoTransactionContext
import com.wire.kalium.cryptography.MlsCoreCryptoContext
import com.wire.kalium.cryptography.ProteusCoreCryptoContext
import com.wire.kalium.logic.data.client.CryptoTransactionProvider
import com.wire.kalium.logic.data.client.MLSClientProvider
import com.wire.kalium.logic.data.client.ProteusClientProvider
import com.wire.kalium.logic.data.event.EventEnvelope
import com.wire.kalium.logic.data.event.EventRepository
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.framework.TestEvent
import com.wire.kalium.logic.framework.TestEvent.wrapInEnvelope
import com.wire.kalium.logic.sync.KaliumSyncException
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import com.wire.kalium.persistence.TestUserDatabase
import dev.mokkery.answering.calls
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class EventBatchProcessorTest {

    @Test
    fun givenMultipleEvents_whenProcessingBatch_thenEventsAreProcessedInInputOrder() = runTest {
        val envelopes = listOf(
            TestEvent.memberJoin("event-1").wrapInEnvelope(),
            TestEvent.memberJoin("event-2").wrapInEnvelope(),
            TestEvent.memberJoin("event-3").wrapInEnvelope(),
        )
        val (arrangement, processor) = Arrangement().arrange()

        processor.processEvents(envelopes)

        assertEquals(envelopes, arrangement.processedEnvelopes)
    }

    @Test
    fun givenSuccessfulEvent_whenProcessingBatch_thenFlushCompletesInsideTransactionAndMarkerIsWrittenAfterwards() = runTest {
        val envelope = TestEvent.memberJoin("event-1").wrapInEnvelope()
        val (arrangement, processor) = Arrangement().arrange()

        processor.processEvents(listOf(envelope))

        assertEquals(
            listOf(
                "transaction-start",
                "process:event-1",
                "flush",
                "transaction-complete",
                "marker",
            ),
            arrangement.callOrder,
        )
    }

    @Test
    fun givenTransactionFailsAfterSuccessfulProcessing_whenProcessingBatch_thenSuccessfulIdsAreNotMarked() = runTest {
        val failure = NetworkFailure.NoNetworkConnection(null)
        val envelope = TestEvent.memberJoin("event-1").wrapInEnvelope()
        val (arrangement, processor) = Arrangement()
            .withTransactionFailureAfterBlock(failure)
            .arrange()

        val exception = assertFailsWith<KaliumSyncException> {
            processor.processEvents(listOf(envelope))
        }

        assertEquals(failure, exception.coreFailureCause)
        assertTrue(arrangement.markedEventIdLists.isEmpty())
    }

    @Test
    fun givenEventProcessingReturnsNoIds_whenProcessingBatch_thenMarkerIsNotWritten() = runTest {
        val envelope = TestEvent.memberJoin("event-1").wrapInEnvelope()
        val (arrangement, processor) = Arrangement()
            .withEventProcessorAnswering { Either.Right(null) }
            .arrange()

        processor.processEvents(listOf(envelope))

        assertTrue(arrangement.markedEventIdLists.isEmpty())
    }

    @Test
    fun givenEventProcessingFails_whenProcessingBatch_thenAllMappedEventsAreProcessedAndKaliumSyncExceptionIsThrown() = runTest {
        val failure = NetworkFailure.NoNetworkConnection(null)
        val envelopes = listOf(
            TestEvent.memberJoin("failing-event").wrapInEnvelope(),
            TestEvent.memberJoin("following-event").wrapInEnvelope(),
        )
        val (arrangement, processor) = Arrangement()
            .withEventProcessorAnswering { envelope ->
                if (envelope.event.id == "failing-event") Either.Left(failure) else Either.Right(envelope.event.id)
            }
            .arrange()

        val exception = assertFailsWith<KaliumSyncException> {
            processor.processEvents(envelopes)
        }

        assertEquals(failure, exception.coreFailureCause)
        assertEquals(envelopes, arrangement.processedEnvelopes)
        assertTrue("flush" !in arrangement.callOrder)
        assertTrue(arrangement.markedEventIdLists.isEmpty())
    }

    @Test
    fun givenMarkerWriteFails_whenProcessingBatch_thenFailureRemainsIgnored() = runTest {
        val envelope = TestEvent.memberJoin("event-1").wrapInEnvelope()
        val (arrangement, processor) = Arrangement()
            .withSetEventsAsProcessedReturning(Either.Left(StorageFailure.DataNotFound))
            .arrange()

        processor.processEvents(listOf(envelope))

        assertEquals(listOf(listOf("event-1")), arrangement.markedEventIdLists)
    }

    @Test
    fun givenCompleteBatch_whenProcessingEvents_thenOneCombinedCryptoTransactionIsUsed() = runTest {
        val envelopes = listOf(
            TestEvent.memberJoin("event-1").wrapInEnvelope(),
            TestEvent.memberJoin("event-2").wrapInEnvelope(),
            TestEvent.memberJoin("event-3").wrapInEnvelope(),
        )
        val (arrangement, processor) = Arrangement().arrange()

        processor.processEvents(envelopes)

        assertEquals(1, arrangement.transactionProvider.combinedTransactionCount)
        assertEquals(listOf<String?>("processEvents"), arrangement.transactionProvider.combinedTransactionNames)
        assertEquals(0, arrangement.transactionProvider.proteusTransactionCount)
        assertEquals(0, arrangement.transactionProvider.mlsTransactionCount)
        assertEquals(
            List(envelopes.size) { arrangement.transactionProvider.transactionContext },
            arrangement.processedTransactionContexts,
        )
    }

    @Test
    fun givenCancellationDuringEventProcessing_whenProcessingBatch_thenMarkerIsStillWritten() = runTest {
        val envelope = TestEvent.memberJoin("event-1").wrapInEnvelope()
        val processingStarted = CompletableDeferred<Unit>()
        val allowProcessingToFinish = CompletableDeferred<Unit>()
        val (arrangement, processor) = Arrangement()
            .withEventProcessorAnswering {
                processingStarted.complete(Unit)
                allowProcessingToFinish.await()
                Either.Right(it.event.id)
            }
            .arrange()

        val job = launch {
            processor.processEvents(listOf(envelope))
        }

        processingStarted.await()
        job.cancel()
        allowProcessingToFinish.complete(Unit)
        job.join()

        assertEquals(listOf(listOf("event-1")), arrangement.markedEventIdLists)
    }

    private class Arrangement {
        val eventProcessor: EventProcessor = mock()
        val eventRepository: EventRepository = mock()
        val database = TestUserDatabase(
            userId = QualifiedID("event-batch-processor", "domain").toDao(),
            dispatcher = TestKaliumDispatcher.default,
        )
        val callOrder = mutableListOf<String>()
        val processedEnvelopes = mutableListOf<EventEnvelope>()
        val processedTransactionContexts = mutableListOf<CryptoTransactionContext>()
        val markedEventIdLists = mutableListOf<List<String>>()
        val transactionProvider = RecordingCryptoTransactionProvider(callOrder)

        init {
            runBlocking {
                withEventProcessorAnswering { Either.Right(it.event.id) }
                withSetEventsAsProcessedReturning(Either.Right(Unit))
            }
        }

        suspend fun withEventProcessorAnswering(
            answer: suspend (EventEnvelope) -> Either<CoreFailure, String?>
        ) = apply {
            everySuspend { eventProcessor.processEvent(any(), any()) } calls { invocation ->
                val transactionContext = invocation.args[0] as CryptoTransactionContext
                val envelope = invocation.args[1] as EventEnvelope
                processedTransactionContexts += transactionContext
                processedEnvelopes += envelope
                callOrder += "process:${envelope.event.id}"
                answer(envelope)
            }
            everySuspend { eventProcessor.flushPendingSideEffects() } calls {
                callOrder += "flush"
                Either.Right(Unit)
            }
        }

        fun withTransactionFailureAfterBlock(failure: CoreFailure) = apply {
            transactionProvider.failureAfterBlock = failure
        }

        suspend fun withSetEventsAsProcessedReturning(result: Either<StorageFailure, Unit>) = apply {
            everySuspend { eventRepository.setEventsAsProcessed(any()) } calls { invocation ->
                @Suppress("UNCHECKED_CAST")
                val eventIds = invocation.args[0] as List<String>
                markedEventIdLists += eventIds
                callOrder += "marker"
                result
            }
        }

        fun arrange() = this to EventBatchProcessorImpl(
            eventProcessor = eventProcessor,
            transactionProvider = transactionProvider,
            databaseBuilder = database.builder,
            eventRepository = eventRepository,
        )
    }

    private class RecordingCryptoTransactionProvider(
        private val callOrder: MutableList<String>,
    ) : CryptoTransactionProvider {
        override val mlsClientProvider: MLSClientProvider = mock()
        override val proteusClientProvider: ProteusClientProvider = mock()
        val transactionContext: CryptoTransactionContext = mock()
        val combinedTransactionNames = mutableListOf<String?>()
        var combinedTransactionCount = 0
        var proteusTransactionCount = 0
        var mlsTransactionCount = 0
        var failureAfterBlock: CoreFailure? = null

        override suspend fun <R> proteusTransaction(
            name: String?,
            block: suspend (ProteusCoreCryptoContext) -> Either<CoreFailure, R>,
        ): Either<CoreFailure, R> {
            proteusTransactionCount++
            error("A combined transaction was expected")
        }

        override suspend fun <R> mlsTransaction(
            name: String?,
            block: suspend (MlsCoreCryptoContext) -> Either<CoreFailure, R>,
        ): Either<CoreFailure, R> {
            mlsTransactionCount++
            error("A combined transaction was expected")
        }

        override suspend fun <R> transaction(
            name: String?,
            block: suspend (CryptoTransactionContext) -> Either<CoreFailure, R>,
        ): Either<CoreFailure, R> {
            combinedTransactionCount++
            combinedTransactionNames += name
            callOrder += "transaction-start"
            val result = block(transactionContext)
            callOrder += "transaction-complete"
            return failureAfterBlock?.let { Either.Left(it) } ?: result
        }
    }
}
