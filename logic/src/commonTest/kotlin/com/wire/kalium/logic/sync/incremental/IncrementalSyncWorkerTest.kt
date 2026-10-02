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
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import app.cash.turbine.test
import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.event.EventEnvelope
import com.wire.kalium.logic.data.event.EventRepository
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.framework.TestEvent
import com.wire.kalium.logic.framework.TestEvent.wrapInEnvelope
import com.wire.kalium.logic.sync.receiver.conversation.message.DurableMLSReceiveContext
import com.wire.kalium.logic.sync.KaliumSyncException
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementMokkeryImpl
import com.wire.kalium.persistence.TestUserDatabase
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.every
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
    fun connectionReceiptDoesNotSuppressDeferredResolutionOnReplay() = runTest {
        val event = TestEvent.newConnection(status = com.wire.kalium.logic.data.user.ConnectionState.ACCEPTED).wrapInEnvelope()
        val (arrangement, worker) = Arrangement()
            .withEventGathererReturning(flowOf(EventStreamData.NewEvents(listOf(event))))
            .withEventProcessorReturning(Either.Right(event.event.id))
            .arrange(record = { _, _ -> }, acknowledge = {}, effectsProcessed = { _, _ -> true })

        worker.processEventsFlow().collect()

        verifySuspend(VerifyMode.exactly(1)) { arrangement.eventProcessor.processEvent(any(), eq(event)) }
    }

    @Test
    fun receiptSkipsMetadataEffectsButStillPassesProteusToItsNativeHandler() = runTest {
        val metadata = TestEvent.memberLeave(eventId = "metadata").wrapInEnvelope()
        val proteus = TestEvent.newMessageEvent("encrypted").copy(id = "proteus").wrapInEnvelope()
        val (arrangement, worker) = Arrangement()
            .withEventGathererReturning(flowOf(EventStreamData.NewEvents(listOf(metadata, proteus))))
            .arrange(record = { _, _ -> }, acknowledge = { assertEquals(listOf("metadata", "proteus"), it) },
                effectsProcessed = { _, _ -> true })
        everySuspend { arrangement.eventProcessor.processEvent(any(), any()) } calls {
            assertTrue((it.args[0] as DurableMLSReceiveContext).effectsAlreadyPersisted)
            assertEquals(proteus, it.args[1])
            Either.Right("proteus")
        }

        worker.processEventsFlow().collect()

        verifySuspend(VerifyMode.exactly(1)) { arrangement.eventProcessor.processEvent(any(), any()) }
    }

    @Test
    fun recoveryReceiptsFollowFlushAndAtomicAcknowledgementFollowsOuterCommit() = runTest {
        val prefix = TestEvent.newMLSMessageEvent(kotlin.time.Instant.DISTANT_PAST).copy(id = "prefix").wrapInEnvelope()
        val proteus = TestEvent.newMessageEvent("encrypted").copy(id = "proteus").wrapInEnvelope()
        val local = TestEvent.newMLSMessageEvent(kotlin.time.Instant.DISTANT_PAST).copy(id = "local-response").wrapInEnvelope()
        val trigger = TestEvent.newMLSMessageEvent(kotlin.time.Instant.DISTANT_FUTURE).copy(id = "trigger").wrapInEnvelope()
        val order = mutableListOf<String>()
        val (arrangement, worker) = Arrangement()
            .withEventGathererReturning(flowOf(EventStreamData.NewEvents(listOf(prefix, proteus, local, trigger))))
            .arrange(
                record = { context, events ->
                    assertTrue(context is DurableMLSReceiveContext)
                    assertEquals(listOf(prefix, proteus, trigger), events)
                    order += "receipt"
                },
                acknowledge = { ids ->
                    assertEquals(listOf("prefix", "proteus", "trigger"), ids)
                    order += "acknowledge"
                },
            )
        everySuspend { arrangement.eventProcessor.processEvent(any(), any()) } calls {
            assertTrue(it.args[0] is DurableMLSReceiveContext)
            val event = (it.args[1] as EventEnvelope).event
            Either.Right(event.id.takeUnless { id -> id == "local-response" })
        }
        everySuspend { arrangement.eventProcessor.flushPendingSideEffects() } calls {
            order += "flush"
            Either.Right(Unit)
        }
        everySuspend { arrangement.cryptoTransactionProvider.transaction<MutableList<String>>(any(), any()) } calls {
            @Suppress("UNCHECKED_CAST")
            val block = it.args[1] as suspend (CryptoTransactionContext) -> Either<CoreFailure, MutableList<String>>
            block(arrangement.transactionContext).also { order += "outer commit" }
        }

        worker.processEventsFlow().collect()

        assertEquals(listOf("flush", "receipt", "outer commit", "acknowledge"), order)
        verifySuspend(VerifyMode.not) { arrangement.eventRepository.setEventsAsProcessed(any()) }
    }

    @Test
    fun outerCommitFailurePreservesReceiptsWithoutAcknowledgement() = runTest {
        val event = TestEvent.newMLSMessageEvent(kotlin.time.Instant.DISTANT_PAST).wrapInEnvelope()
        var recorded = false
        var acknowledged = false
        val (arrangement, worker) = Arrangement()
            .withEventGathererReturning(flowOf(EventStreamData.NewEvents(listOf(event))))
            .withEventProcessorReturning(Either.Right(event.event.id))
            .arrange(record = { _, _ -> recorded = true }, acknowledge = { acknowledged = true })
        everySuspend { arrangement.cryptoTransactionProvider.transaction<MutableList<String>>(any(), any()) } calls {
            @Suppress("UNCHECKED_CAST")
            val block = it.args[1] as suspend (CryptoTransactionContext) -> Either<CoreFailure, MutableList<String>>
            block(arrangement.transactionContext)
            Either.Left(StorageFailure.DataNotFound)
        }

        assertFailsWith<KaliumSyncException> { worker.processEventsFlow().collect() }
        assertTrue(recorded)
        assertFalse(acknowledged)
        verifySuspend(VerifyMode.not) { arrangement.eventRepository.setEventsAsProcessed(any()) }
    }

    @Test
    fun effectFailurePreventsReceiptAndAcknowledgement() = runTest {
        val event = TestEvent.newMLSMessageEvent(kotlin.time.Instant.DISTANT_PAST).wrapInEnvelope()
        var recorded = false
        var acknowledged = false
        val (arrangement, worker) = Arrangement()
            .withEventGathererReturning(flowOf(EventStreamData.NewEvents(listOf(event))))
            .withEventProcessorReturning(Either.Right(event.event.id))
            .arrange(record = { _, _ -> recorded = true }, acknowledge = { acknowledged = true })
        everySuspend { arrangement.eventProcessor.flushPendingSideEffects() } returns Either.Left(StorageFailure.DataNotFound)

        assertFailsWith<KaliumSyncException> { worker.processEventsFlow().collect() }
        assertFalse(recorded)
        assertFalse(acknowledged)
    }

    @Test
    fun receiptFailureThrowsBeforeCryptoCallbackCanCommit() = runTest {
        val event = TestEvent.newMLSMessageEvent(kotlin.time.Instant.DISTANT_PAST).wrapInEnvelope()
        var callbackReturned = false
        val (arrangement, worker) = Arrangement()
            .withEventGathererReturning(flowOf(EventStreamData.NewEvents(listOf(event))))
            .withEventProcessorReturning(Either.Right(event.event.id))
            .arrange(record = { _, _ -> error("receipt failed") }, acknowledge = {})
        everySuspend { arrangement.cryptoTransactionProvider.transaction<MutableList<String>>(any(), any()) } calls {
            @Suppress("UNCHECKED_CAST")
            val block = it.args[1] as suspend (CryptoTransactionContext) -> Either<CoreFailure, MutableList<String>>
            block(arrangement.transactionContext).also { callbackReturned = true }
        }

        assertFailsWith<IllegalStateException> { worker.processEventsFlow().collect() }
        assertFalse(callbackReturned)
        verifySuspend(VerifyMode.not) { arrangement.eventRepository.setEventsAsProcessed(any()) }
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
            arrangement.eventProcessor.processEvent(eq(arrangement.transactionContext), eq(envelope))
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
    fun givenFirstEventFails_whenLegacyReceive_thenLaterEventsAreStillProcessed() = runTest {
        val failure = StorageFailure.DataNotFound
        val first = TestEvent.memberJoin().wrapInEnvelope()
        val later = TestEvent.memberLeave().wrapInEnvelope()
        val (arrangement, worker) = Arrangement()
            .withEventGathererReturning(flowOf(EventStreamData.NewEvents(listOf(first, later))))
            .withEventProcessorFailingWith(failure)
            .arrange()

        val thrown = assertFailsWith<KaliumSyncException> { worker.processEventsFlow().collect() }

        assertEquals(failure, thrown.coreFailureCause)
        verifySuspend(VerifyMode.exactly(1)) { arrangement.eventProcessor.processEvent(any(), eq(first)) }
        verifySuspend(VerifyMode.exactly(1)) { arrangement.eventProcessor.processEvent(any(), eq(later)) }
        verifySuspend(VerifyMode.not) { arrangement.eventProcessor.flushPendingSideEffects() }
        verifySuspend(VerifyMode.not) { arrangement.eventRepository.setEventsAsProcessed(any()) }
    }

    @Test
    fun givenFirstEventFails_whenDurableReceive_thenLaterEventAndProcessedMarkersAreSkipped() = runTest {
        val failure = StorageFailure.DataNotFound
        val first = TestEvent.memberJoin().wrapInEnvelope()
        val later = TestEvent.memberLeave().wrapInEnvelope()
        val (arrangement, worker) = Arrangement()
            .withEventGathererReturning(flowOf(EventStreamData.NewEvents(listOf(first, later))))
            .withEventProcessorFailingWith(failure)
            .arrange(record = { _, _ -> }, acknowledge = {})

        val resultException = assertFailsWith<KaliumSyncException> {
            worker.processEventsFlow().collect()
        }

        assertEquals(failure, resultException.coreFailureCause)
        verifySuspend(VerifyMode.exactly(1)) { arrangement.eventProcessor.processEvent(any(), eq(first)) }
        verifySuspend(VerifyMode.not) { arrangement.eventProcessor.processEvent(any(), eq(later)) }
        verifySuspend(VerifyMode.not) { arrangement.eventProcessor.flushPendingSideEffects() }
        verifySuspend(VerifyMode.not) { arrangement.eventRepository.setEventsAsProcessed(any()) }
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
    fun failedProcessedMarkerDoesNotStopLegacyReceiveBeforeTheNextBatch() = runTest {
        val first = TestEvent.memberJoin().wrapInEnvelope()
        val later = TestEvent.memberLeave().wrapInEnvelope()
        val failure = StorageFailure.Generic(IllegalStateException("marker write failed"))
        val (arrangement, worker) = Arrangement()
            .withEventGathererReturning(flowOf(
                EventStreamData.NewEvents(listOf(first)),
                EventStreamData.NewEvents(listOf(later)),
            ))
            .withEventProcessorReturning(Either.Right(first.event.id))
            .withSetEventsAsProcessedReturning(Either.Left(failure))
            .arrange()

        worker.processEventsFlow().collect()

        verifySuspend(VerifyMode.exactly(2)) { arrangement.eventRepository.setEventsAsProcessed(any()) }
        verifySuspend(VerifyMode.exactly(1)) { arrangement.eventProcessor.processEvent(any(), eq(later)) }
    }

    @Test
    fun failedDurableAcknowledgementStopsBeforeTheNextBatch() = runTest {
        val first = TestEvent.memberJoin().wrapInEnvelope()
        val later = TestEvent.memberLeave().wrapInEnvelope()
        val failure = StorageFailure.Generic(IllegalStateException("marker write failed"))
        var acknowledgedIds: List<String>? = null
        val (arrangement, worker) = Arrangement()
            .withEventGathererReturning(flowOf(
                EventStreamData.NewEvents(listOf(first)),
                EventStreamData.NewEvents(listOf(later)),
            ))
            .withEventProcessorReturning(Either.Right(first.event.id))
            .arrange(record = { _, _ -> }, acknowledge = {
                acknowledgedIds = it
                throw KaliumSyncException("Marking processed events failed", failure)
            })

        val thrown = assertFailsWith<KaliumSyncException> { worker.processEventsFlow().collect() }

        assertEquals(failure, thrown.coreFailureCause)
        assertEquals(listOf(first.event.id), acknowledgedIds)
        verifySuspend(VerifyMode.not) { arrangement.eventProcessor.processEvent(any(), eq(later)) }
        verifySuspend(VerifyMode.not) { arrangement.eventRepository.setEventsAsProcessed(any()) }
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
            every { eventProcessor.disableEventProcessing } returns false
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

        suspend fun arrange(
            consumeInvalidation: suspend () -> Boolean = { false },
            record: (suspend (CryptoTransactionContext, List<EventEnvelope>) -> Unit)? = null,
            acknowledge: (suspend (List<String>) -> Unit)? = null,
            effectsProcessed: (suspend (CryptoTransactionContext, EventEnvelope) -> Boolean)? = null,
            block: suspend Arrangement.() -> Unit = {},
        ) = let {
            block()
            withTransactionReturning(Either.Right(Unit))
            this to IncrementalSyncWorkerImpl(
                eventGatherer, eventProcessor, cryptoTransactionProvider, database.builder, eventRepository,
                recordMLSReceipts = record, acknowledgeMLSRecovery = acknowledge, werePayloadEffectsProcessed = effectsProcessed,
                consumePendingAccountInvalidation = consumeInvalidation,
            )
        }
    }
}
