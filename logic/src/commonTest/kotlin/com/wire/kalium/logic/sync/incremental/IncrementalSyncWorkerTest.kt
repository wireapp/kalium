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

package com.wire.kalium.logic.sync.incremental

import app.cash.turbine.test
import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.cryptography.CryptoTransactionContext
import com.wire.kalium.logic.data.event.EventRepository
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.framework.TestEvent
import com.wire.kalium.logic.framework.TestEvent.wrapInEnvelope
import com.wire.kalium.logic.sync.KaliumSyncException
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementMokkeryImpl
import com.wire.kalium.persistence.TestUserDatabase
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith

class IncrementalSyncWorkerTest {

    @Test
    fun givenGathererEmitsEvent_whenPerformingIncrementalSync_thenProcessorShouldReceiveTheEvent() = runTest(TestKaliumDispatcher.default) {
        // Given
        val envelope = TestEvent.memberJoin().wrapInEnvelope()
        val (arrangement, worker) = Arrangement()
            .withEventGathererReturning(flowOf(EventStreamData.NewEvents(listOf(envelope))))
            .arrange()

        // When
        worker.processEventsFlow().collect()

        // Then
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.eventProcessor.processEvent(any(), eq(envelope))
        }
    }

    @Test
    fun givenGathererEmitsSinglePageOfEvents_whenPerformingIncrementalSync_thenWorkerShouldEmitPendingSource() =
        runTest(TestKaliumDispatcher.default) {
            // Given
            val event = TestEvent.memberJoin().wrapInEnvelope()
            val (_, worker) = Arrangement()
                .withEventGathererReturning(flowOf(EventStreamData.NewEvents(listOf(event))))
                .arrange()

            // When
            worker.processEventsFlow().test {
                // Then
                assertEquals(EventSource.PENDING, awaitItem())
                awaitComplete()
            }
        }

    @Test
    fun givenGathererEmitsLiveSource_whenPerformingIncrementalSync_thenWorkerShouldEmitLiveSource() =
        runTest(TestKaliumDispatcher.default) {
            // Given
            val event = TestEvent.memberJoin().wrapInEnvelope()
            val (_, worker) = Arrangement()
                .withEventGathererReturning(
                    flowOf(
                        EventStreamData.NewEvents(listOf(event)),
                        EventStreamData.IsUpToDate
                    )
                )
                .arrange()

            // When
            worker.processEventsFlow().test {
                // Then
                assertEquals(EventSource.PENDING, awaitItem())
                assertEquals(EventSource.LIVE, awaitItem())
                awaitComplete()
            }
        }

    @Test
    fun givenGathererThrows_whenPerformingIncrementalSync_thenTheFailureIsPropagated() = runTest(TestKaliumDispatcher.default) {
        // Given
        val coreFailureCause = NetworkFailure.NoNetworkConnection(null)
        val exception = KaliumSyncException("Oopsie", coreFailureCause)
        val (_, worker) = Arrangement()
            .withEventGathererReturning(flow { throw exception })
            .arrange()

        // When
        val resultException = assertFails {
            worker.processEventsFlow().collect()
        }

        assertEquals(exception, resultException)
    }

    @Test
    fun givenProcessorFails_whenPerformingIncrementalSync_thenShouldThrowKaliumSyncException() = runTest {
        val coreFailureCause = NetworkFailure.NoNetworkConnection(null)
        val event = TestEvent.memberJoin().wrapInEnvelope()
        val (_, worker) = Arrangement()
            .withEventGathererReturning(flowOf(EventStreamData.NewEvents(listOf(event))))
            .withEventProcessorFailingWith(coreFailureCause)
            .arrange()

        val resultException = assertFailsWith<KaliumSyncException> {
            worker.processEventsFlow().collect()
        }

        assertEquals(coreFailureCause, resultException.coreFailureCause)
    }

    @Test
    fun givenProcessorReturnsEventId_whenPerformingIncrementalSync_thenWorkerMarksEventAsProcessed() = runTest {
        val envelope = TestEvent.memberJoin().wrapInEnvelope()
        val eventId = envelope.event.id

        val (arrangement, worker) = Arrangement()
            .withEventGathererReturning(flowOf(EventStreamData.NewEvents(listOf(envelope))))
            .withEventProcessorReturning(Either.Right(eventId))
            .withSetEventsAsProcessedReturning(Either.Right(Unit))
            .arrange()

        worker.processEventsFlow().collect()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.eventRepository.setEventsAsProcessed(eq(listOf(eventId)))
        }
    }

    @Test
    fun givenPendingSideEffects_whenPerformingIncrementalSync_thenSideEffectsAreFlushedOutsideTransaction() = runTest {
        val envelope = TestEvent.memberJoin().wrapInEnvelope()
        val eventId = envelope.event.id

        val (arrangement, worker) = Arrangement()
            .withEventGathererReturning(flowOf(EventStreamData.NewEvents(listOf(envelope))))
            .withEventProcessorReturning(Either.Right(eventId))
            .withSetEventsAsProcessedReturning(Either.Right(Unit))
            .arrange()

        everySuspend {
            arrangement.eventProcessor.processEvent(any(), eq(envelope))
        } calls {
            assertEquals(true, arrangement.transactionActive)
            Either.Right(eventId)
        }
        everySuspend {
            arrangement.eventProcessor.flushPendingSideEffects()
        } calls {
            assertEquals(false, arrangement.transactionActive)
            Either.Right(Unit)
        }

        worker.processEventsFlow().collect()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.eventRepository.setEventsAsProcessed(eq(listOf(eventId)))
        }
    }

    @Test
    fun givenProcessorReturnsNull_whenPerformingIncrementalSync_thenWorkerDoesNotMarkEventsAsProcessed() = runTest {
        val envelope = TestEvent.memberJoin().wrapInEnvelope()

        val (arrangement, worker) = Arrangement()
            .withEventGathererReturning(flowOf(EventStreamData.NewEvents(listOf(envelope))))
            .withEventProcessorReturning(Either.Right(null))
            .arrange()

        worker.processEventsFlow().collect()

        verifySuspend(VerifyMode.not) {
            arrangement.eventRepository.setEventsAsProcessed(any())
        }
    }

    @Test
    fun givenCancellationDuringEventProcessing_whenPerformingIncrementalSync_thenWorkerMarksEventAsProcessed() = runTest {
        val envelope = TestEvent.memberJoin().wrapInEnvelope()
        val eventId = envelope.event.id
        val processingStarted = CompletableDeferred<Unit>()
        val allowProcessingToFinish = CompletableDeferred<Unit>()

        val (arrangement, worker) = Arrangement()
            .withEventGathererReturning(flowOf(EventStreamData.NewEvents(listOf(envelope))))
            .withSetEventsAsProcessedReturning(Either.Right(Unit))
            .arrange()

        everySuspend {
            arrangement.eventProcessor.processEvent(any(), eq(envelope))
        } calls {
            processingStarted.complete(Unit)
            allowProcessingToFinish.await()
            Either.Right(eventId)
        }

        val job = launch {
            worker.processEventsFlow().collect()
        }

        processingStarted.await()
        job.cancel()
        allowProcessingToFinish.complete(Unit)
        job.join()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.eventRepository.setEventsAsProcessed(eq(listOf(eventId)))
        }
    }

    private class Arrangement : CryptoTransactionProviderArrangement by CryptoTransactionProviderArrangementMokkeryImpl() {
        val eventProcessor: EventProcessor = mock()
        val eventGatherer: EventGatherer = mock()
        val eventRepository: EventRepository = mock()
        var transactionActive = false
        val database = TestUserDatabase(
            userId = QualifiedID("value", "domain").toDao(),
            dispatcher = TestKaliumDispatcher.default
        )

        init {
            runBlocking {
                withEventProcessorSucceeding()
                withSetEventsAsProcessedReturning(Either.Right(Unit))
            }
        }

        suspend fun withEventGathererReturning(eventFlow: Flow<EventStreamData>) = apply {
            everySuspend {
                eventGatherer.gatherEvents()
            } returns eventFlow
        }

        suspend fun withEventProcessorReturning(result: Either<CoreFailure, String?>) = apply {
            everySuspend {
                eventProcessor.processEvent(any(), any())
            } returns result
            everySuspend {
                eventProcessor.flushPendingSideEffects()
            } returns Either.Right(Unit)
        }

        suspend fun withEventProcessorSucceeding() = withEventProcessorReturning(Either.Right(null))

        suspend fun withEventProcessorFailingWith(failure: CoreFailure) = withEventProcessorReturning(Either.Left(failure))

        suspend fun withSetEventsAsProcessedReturning(result: Either<StorageFailure, Unit>) = apply {
            everySuspend { eventRepository.setEventsAsProcessed(any()) } returns result
        }

        @Suppress("UNCHECKED_CAST")
        override suspend fun <R> withTransactionReturning(result: Either<CoreFailure, R>): CryptoTransactionProviderArrangement = apply {
            everySuspend {
                cryptoTransactionProvider.transaction<R>(any(), any())
            } calls {
                val block = it.args[1] as suspend (CryptoTransactionContext) -> Either<CoreFailure, R>
                transactionActive = true
                try {
                    block(transactionContext)
                } finally {
                    transactionActive = false
                }
            }
        }

        suspend fun arrange(block: suspend Arrangement.() -> Unit = {}) = let {
            block()
            withTransactionReturning(Either.Right(Unit))
            this to IncrementalSyncWorkerImpl(
                eventGatherer, eventProcessor, cryptoTransactionProvider, database.builder, eventRepository
            )
        }
    }
}
