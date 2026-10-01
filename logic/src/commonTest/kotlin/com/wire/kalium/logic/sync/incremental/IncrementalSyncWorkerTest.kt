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

import com.wire.kalium.cryptography.CryptoTransactionContext
import kotlin.test.assertFalse
import app.cash.turbine.test
import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith

class IncrementalSyncWorkerTest {

    @Test
    fun terminalAccountEventBehindBlockedPageLogsOutBeforeNetworkAndKeepsQueue() = runTest(TestKaliumDispatcher.default) {
        for (deleted in listOf(false, true)) {
            val fixture = Arrangement()
            val database = fixture.database.builder
            val user = QualifiedID("terminal-self", "example.test")
            val blocked = """[{"type":"user.legalhold-enable","id":"${user.value}"}]"""
            val terminal = if (deleted)
                """[{"type":"user.delete","id":"${user.value}","qualified_id":{"id":"${user.value}","domain":"${user.domain}"}}]"""
            else """[{"type":"user.client-remove","client":{"id":"current"}}]"""
            val events = (0..100).map {
                com.wire.kalium.persistence.dao.event.NewEventEntity("blocked-$it", blocked, false, false)
            } + com.wire.kalium.persistence.dao.event.NewEventEntity("terminal", terminal, false, false)
            database.notificationEventStore.storePage(null, events)
            val invalidated = CompletableDeferred<Unit>()
            val (_, worker) = fixture.arrange(consumeInvalidation = {
                val result = com.wire.kalium.logic.sync.receiving.pendingAccountInvalidation(database, user, "current")
                assertEquals(if (deleted) com.wire.kalium.logic.sync.receiving.PendingAccountInvalidation.DELETED_ACCOUNT
                    else com.wire.kalium.logic.sync.receiving.PendingAccountInvalidation.REMOVED_CLIENT, result)
                invalidated.complete(Unit)
                result != null
            })
            val job = launch { worker.processEventsFlow().collect() }
            invalidated.await()
            job.cancel(); job.join()
            verifySuspend(VerifyMode.not) { fixture.eventGatherer.gatherEvents() }
            assertEquals("blocked-0", database.notificationEventStore.nextPending()?.eventId)
            assertEquals(false, database.eventDAO.getEventById("terminal")?.isProcessed)
        }
    }

    @Test
    fun terminalPreflightIgnoresOtherClientAndQualifiedUserAndProcessedEvents() = runTest(TestKaliumDispatcher.default) {
        val fixture = Arrangement()
        val database = fixture.database.builder
        val user = QualifiedID("self", "one.example")
        val events = listOf(
            """[{"type":"user.client-remove","client":{"id":"other"}}]""",
            """[{"type":"user.delete","id":"self","qualified_id":{"id":"self","domain":"two.example"}}]""",
            """[{"type":"user.client-remove","client":{"id":"current"}}]""",
        ).mapIndexed { i, payload -> com.wire.kalium.persistence.dao.event.NewEventEntity("event-$i", payload, false, false) }
        database.notificationEventStore.storePage(null, events)
        database.eventDAO.markEventAsProcessed("event-2")
        assertEquals(null, com.wire.kalium.logic.sync.receiving.pendingAccountInvalidation(database, user, "current"))
        assertEquals("event-0", database.notificationEventStore.nextPending()?.eventId)
    }

    @Test
    fun pendingAccountInvalidationRunsBeforeAnyNetworkGathering() = runTest {
        val invalidated = CompletableDeferred<Unit>()
        val (arrangement, worker) = Arrangement().arrange(consumeInvalidation = {
            invalidated.complete(Unit)
            true
        })
        val job = launch { worker.processEventsFlow().collect() }
        invalidated.await()
        job.cancel()
        job.join()
        verifySuspend(VerifyMode.not) { arrangement.eventGatherer.gatherEvents() }
    }

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
    fun requiredProcessingAndFlushFailuresAbortTheGatheredCryptoCallback() = runTest {
        for (failOnFlush in listOf(false, true)) {
            val failure = StorageFailure.Generic(IllegalStateException("required write failed"))
            val event = TestEvent.memberJoin().wrapInEnvelope()
            val (arrangement, worker) = Arrangement()
                .withEventGathererReturning(flowOf(EventStreamData.NewEvents(listOf(event))))
                .withEventProcessorReturning(if (failOnFlush) Either.Right(event.event.id) else Either.Left(failure))
                .arrange()
            if (failOnFlush) {
                everySuspend { arrangement.eventProcessor.flushPendingSideEffects() } returns Either.Left(failure)
            }
            var callbackReturned = false
            everySuspend { arrangement.cryptoTransactionProvider.transaction<MutableList<String>>(any(), any()) } calls {
                @Suppress("UNCHECKED_CAST")
                val block = it.args[1] as suspend (CryptoTransactionContext) -> Either<CoreFailure, MutableList<String>>
                block(arrangement.transactionContext).also { callbackReturned = true }
            }
            assertFailsWith<KaliumSyncException> { worker.processEventsFlow().collect() }
            assertFalse(callbackReturned, "Returning an Either.Left still lets native crypto commit")
            verifySuspend(VerifyMode.not) { arrangement.eventRepository.setEventsAsProcessed(any()) }
        }
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

        suspend fun arrange(consumeInvalidation: suspend () -> Boolean = { false }, block: suspend Arrangement.() -> Unit = {}) = let {
            block()
            withTransactionReturning(Either.Right(Unit))
            this to IncrementalSyncWorkerImpl(
                eventGatherer, eventProcessor, cryptoTransactionProvider, database.builder, eventRepository, consumePendingAccountInvalidation = consumeInvalidation
            )
        }
    }
}
