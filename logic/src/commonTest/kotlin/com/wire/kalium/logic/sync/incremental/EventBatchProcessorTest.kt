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
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.cryptography.CryptoTransactionContext
import com.wire.kalium.logic.data.event.EventEnvelope
import com.wire.kalium.logic.data.event.EventRepository
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.framework.TestEvent
import com.wire.kalium.logic.framework.TestEvent.wrapInEnvelope
import com.wire.kalium.logic.sync.KaliumSyncException
import com.wire.kalium.logic.sync.receiving.ReceivedPayloadIdentity
import com.wire.kalium.logic.sync.receiver.conversation.message.DurableMLSReceiveContext
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementMokkeryImpl
import com.wire.kalium.network.api.authenticated.notification.EventContentDTO
import com.wire.kalium.persistence.TestUserDatabase
import com.wire.kalium.persistence.dao.event.NewEventEntity
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EventBatchProcessorTest {

    @Test
    fun connectionReceiptDoesNotSuppressDeferredResolutionOnReplay() = runTest(TestKaliumDispatcher.default) {
        val event = TestEvent.newConnection(status = com.wire.kalium.logic.data.user.ConnectionState.ACCEPTED).wrapInEnvelope()
        val (arrangement, processor) = Arrangement()
            .withEventBatches(listOf(listOf(event)))
            .withEventProcessorReturning(Either.Right(event.event.id))
            .arrange(durable = true, effectsProcessed = { _, _ -> true })

        processor.processBatches(arrangement.batches)

        verifySuspend(VerifyMode.exactly(1)) { arrangement.eventProcessor.processEvent(any(), event) }
    }

    @Test
    fun receiptSkipsMetadataEffectsButStillPassesProteusToItsNativeHandler() = runTest(TestKaliumDispatcher.default) {
        val metadata = TestEvent.memberLeave(eventId = "metadata").wrapInEnvelope()
        val proteus = TestEvent.newMessageEvent("encrypted").copy(id = "proteus").wrapInEnvelope()
        val (arrangement, processor) = Arrangement()
            .withEventBatches(listOf(listOf(metadata, proteus)))
            .arrange(
                durable = true,
                acknowledge = { assertEquals(listOf("metadata", "proteus"), it) },
                effectsProcessed = { _, _ -> true },
            )
        everySuspend { arrangement.eventProcessor.processEvent(any(), any()) } calls {
            assertTrue((it.args[0] as DurableMLSReceiveContext).effectsAlreadyPersisted)
            assertEquals(proteus, it.args[1])
            Either.Right("proteus")
        }

        processor.processBatches(arrangement.batches)

        verifySuspend(VerifyMode.exactly(1)) { arrangement.eventProcessor.processEvent(any(), any()) }
    }

    @Test
    fun recoveryReceiptsFollowFlushAndAtomicAcknowledgementFollowsOuterCommit() = runTest(TestKaliumDispatcher.default) {
        val prefix = TestEvent.newMLSMessageEvent(kotlin.time.Instant.DISTANT_PAST).copy(id = "prefix").wrapInEnvelope()
        val proteus = TestEvent.newMessageEvent("encrypted").copy(id = "proteus").wrapInEnvelope()
        val local = TestEvent.newMLSMessageEvent(kotlin.time.Instant.DISTANT_PAST).copy(id = "local-response").wrapInEnvelope()
        val trigger = TestEvent.newMLSMessageEvent(kotlin.time.Instant.DISTANT_FUTURE).copy(id = "trigger").wrapInEnvelope()
        val order = mutableListOf<String>()
        val (arrangement, processor) = Arrangement()
            .withEventBatches(listOf(listOf(prefix, proteus, local, trigger)))
            .arrange(
                durable = true,
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
        everySuspend { arrangement.cryptoTransactionProvider.transaction<List<String>>(any(), any()) } calls {
            @Suppress("UNCHECKED_CAST")
            val block = it.args[1] as suspend (CryptoTransactionContext) -> Either<CoreFailure, List<String>>
            block(arrangement.transactionContext).also { order += "outer commit" }
        }

        processor.processBatches(arrangement.batches)

        assertEquals(listOf("flush", "receipt", "outer commit", "acknowledge"), order)
        verifySuspend(VerifyMode.not) { arrangement.eventRepository.setEventsAsProcessed(any()) }
    }

    @Test
    fun outerCommitFailurePreservesReceiptsWithoutAcknowledgement() = runTest(TestKaliumDispatcher.default) {
        val event = TestEvent.newMLSMessageEvent(kotlin.time.Instant.DISTANT_PAST).wrapInEnvelope()
        var recorded = false
        var acknowledged = false
        val (arrangement, processor) = Arrangement()
            .withEventBatches(listOf(listOf(event)))
            .withEventProcessorReturning(Either.Right(event.event.id))
            .arrange(durable = true, record = { _, _ -> recorded = true }, acknowledge = { acknowledged = true })
        everySuspend { arrangement.cryptoTransactionProvider.transaction<List<String>>(any(), any()) } calls {
            @Suppress("UNCHECKED_CAST")
            val block = it.args[1] as suspend (CryptoTransactionContext) -> Either<CoreFailure, List<String>>
            block(arrangement.transactionContext)
            Either.Left(StorageFailure.DataNotFound)
        }

        assertFailsWith<KaliumSyncException> { processor.processBatches(arrangement.batches) }
        assertTrue(recorded)
        assertFalse(acknowledged)
        verifySuspend(VerifyMode.not) { arrangement.eventRepository.setEventsAsProcessed(any()) }
    }

    @Test
    fun effectFailurePreventsReceiptAndAcknowledgement() = runTest(TestKaliumDispatcher.default) {
        val event = TestEvent.newMLSMessageEvent(kotlin.time.Instant.DISTANT_PAST).wrapInEnvelope()
        var recorded = false
        var acknowledged = false
        val (arrangement, processor) = Arrangement()
            .withEventBatches(listOf(listOf(event)))
            .withEventProcessorReturning(Either.Right(event.event.id))
            .arrange(durable = true, record = { _, _ -> recorded = true }, acknowledge = { acknowledged = true })
        everySuspend { arrangement.eventProcessor.flushPendingSideEffects() } returns Either.Left(StorageFailure.DataNotFound)

        assertFailsWith<KaliumSyncException> { processor.processBatches(arrangement.batches) }
        assertFalse(recorded)
        assertFalse(acknowledged)
    }

    @Test
    fun receiptFailureThrowsBeforeCryptoCallbackCanCommit() = runTest(TestKaliumDispatcher.default) {
        val event = TestEvent.newMLSMessageEvent(kotlin.time.Instant.DISTANT_PAST).wrapInEnvelope()
        var callbackReturned = false
        val (arrangement, processor) = Arrangement()
            .withEventBatches(listOf(listOf(event)))
            .withEventProcessorReturning(Either.Right(event.event.id))
            .arrange(durable = true, record = { _, _ -> error("receipt failed") }, acknowledge = {})
        everySuspend { arrangement.cryptoTransactionProvider.transaction<List<String>>(any(), any()) } calls {
            @Suppress("UNCHECKED_CAST")
            val block = it.args[1] as suspend (CryptoTransactionContext) -> Either<CoreFailure, List<String>>
            block(arrangement.transactionContext).also { callbackReturned = true }
        }

        assertFailsWith<IllegalStateException> { processor.processBatches(arrangement.batches) }
        assertFalse(callbackReturned)
        verifySuspend(VerifyMode.not) { arrangement.eventRepository.setEventsAsProcessed(any()) }
    }

    @Test
    fun givenFirstEventFails_whenStandardReceive_thenLaterEventsAreStillProcessed() = runTest(TestKaliumDispatcher.default) {
        val failure = StorageFailure.DataNotFound
        val first = TestEvent.memberJoin().wrapInEnvelope()
        val later = TestEvent.memberLeave().wrapInEnvelope()
        val (arrangement, processor) = Arrangement()
            .withEventBatches(listOf(listOf(first, later)))
            .withEventProcessorFailingWith(failure)
            .arrange()

        val thrown = assertFailsWith<KaliumSyncException> { processor.processBatches(arrangement.batches) }

        assertEquals(failure, thrown.coreFailureCause)
        verifySuspend(VerifyMode.exactly(1)) { arrangement.eventProcessor.processEvent(any(), first) }
        verifySuspend(VerifyMode.exactly(1)) { arrangement.eventProcessor.processEvent(any(), later) }
        verifySuspend(VerifyMode.not) { arrangement.eventProcessor.flushPendingSideEffects() }
        verifySuspend(VerifyMode.not) { arrangement.eventRepository.setEventsAsProcessed(any()) }
    }

    @Test
    fun givenFirstEventFails_whenDurableReceive_thenLaterEventAndProcessedMarkersAreSkipped() = runTest(TestKaliumDispatcher.default) {
        val failure = StorageFailure.DataNotFound
        val first = TestEvent.memberJoin().wrapInEnvelope()
        val later = TestEvent.memberLeave().wrapInEnvelope()
        val (arrangement, processor) = Arrangement()
            .withEventBatches(listOf(listOf(first, later)))
            .withEventProcessorFailingWith(failure)
            .arrange(durable = true)

        val resultException = assertFailsWith<KaliumSyncException> {
            processor.processBatches(arrangement.batches)
        }

        assertEquals(failure, resultException.coreFailureCause)
        verifySuspend(VerifyMode.exactly(1)) { arrangement.eventProcessor.processEvent(any(), first) }
        verifySuspend(VerifyMode.not) { arrangement.eventProcessor.processEvent(any(), later) }
        verifySuspend(VerifyMode.not) { arrangement.eventProcessor.flushPendingSideEffects() }
        verifySuspend(VerifyMode.not) { arrangement.eventRepository.setEventsAsProcessed(any()) }
    }

    @Test
    fun requiredProcessingAndFlushFailuresAbortTheGatheredCryptoCallback() = runTest(TestKaliumDispatcher.default) {
        for (failOnFlush in listOf(false, true)) {
            val failure = StorageFailure.Generic(IllegalStateException("required write failed"))
            val event = TestEvent.memberJoin().wrapInEnvelope()
            val (arrangement, processor) = Arrangement()
                .withEventBatches(listOf(listOf(event)))
                .withEventProcessorReturning(if (failOnFlush) Either.Right(event.event.id) else Either.Left(failure))
                .arrange()
            if (failOnFlush) {
                everySuspend { arrangement.eventProcessor.flushPendingSideEffects() } returns Either.Left(failure)
            }
            var callbackReturned = false
            everySuspend { arrangement.cryptoTransactionProvider.transaction<List<String>>(any(), any()) } calls {
                @Suppress("UNCHECKED_CAST")
                val block = it.args[1] as suspend (CryptoTransactionContext) -> Either<CoreFailure, List<String>>
                block(arrangement.transactionContext).also { callbackReturned = true }
            }
            assertFailsWith<KaliumSyncException> { processor.processBatches(arrangement.batches) }
            assertFalse(callbackReturned, "Returning an Either.Left still lets native crypto commit")
            verifySuspend(VerifyMode.not) { arrangement.eventRepository.setEventsAsProcessed(any()) }
        }
    }

    @Test
    fun givenProcessorReturnsEventId_whenProcessingEventBatch_thenBatchMarksEventAsProcessed() = runTest(TestKaliumDispatcher.default) {
        val envelope = TestEvent.memberJoin().wrapInEnvelope()
        val eventId = envelope.event.id

        val (arrangement, processor) = Arrangement()
            .withEventBatches(listOf(listOf(envelope)))
            .withEventProcessorReturning(Either.Right(eventId))
            .withSetEventsAsProcessedReturning(Either.Right(Unit))
            .arrange()

        processor.processBatches(arrangement.batches)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.eventRepository.setEventsAsProcessed(listOf(eventId))
        }
    }

    @Test
    fun failedProcessedMarkerDoesNotStopStandardReceiveBeforeTheNextBatch() = runTest(TestKaliumDispatcher.default) {
        val first = TestEvent.memberJoin().wrapInEnvelope()
        val later = TestEvent.memberLeave().wrapInEnvelope()
        val failure = StorageFailure.Generic(IllegalStateException("marker write failed"))
        val (arrangement, processor) = Arrangement()
            .withEventBatches(listOf(listOf(first), listOf(later)))
            .withEventProcessorReturning(Either.Right(first.event.id))
            .withSetEventsAsProcessedReturning(Either.Left(failure))
            .arrange()

        processor.processBatches(arrangement.batches)

        verifySuspend(VerifyMode.exactly(2)) { arrangement.eventRepository.setEventsAsProcessed(any()) }
        verifySuspend(VerifyMode.exactly(1)) { arrangement.eventProcessor.processEvent(any(), later) }
    }

    @Test
    fun failedDurableAcknowledgementStopsBeforeTheNextBatch() = runTest(TestKaliumDispatcher.default) {
        val first = TestEvent.memberJoin().wrapInEnvelope()
        val later = TestEvent.memberLeave().wrapInEnvelope()
        val failure = StorageFailure.Generic(IllegalStateException("marker write failed"))
        var acknowledgedIds: List<String>? = null
        val (arrangement, processor) = Arrangement()
            .withEventBatches(listOf(listOf(first), listOf(later)))
            .withEventProcessorReturning(Either.Right(first.event.id))
            .arrange(
                durable = true,
                acknowledge = {
                    acknowledgedIds = it
                    throw KaliumSyncException("Marking processed events failed", failure)
                },
            )

        val thrown = assertFailsWith<KaliumSyncException> { processor.processBatches(arrangement.batches) }

        assertEquals(failure, thrown.coreFailureCause)
        assertEquals(listOf(first.event.id), acknowledgedIds)
        verifySuspend(VerifyMode.not) { arrangement.eventProcessor.processEvent(any(), later) }
        verifySuspend(VerifyMode.not) { arrangement.eventRepository.setEventsAsProcessed(any()) }
    }

    @Test
    fun givenProcessorReturnsNull_whenProcessingEventBatch_thenBatchDoesNotMarkEventsAsProcessed() = runTest(TestKaliumDispatcher.default) {
        val envelope = TestEvent.memberJoin().wrapInEnvelope()

        val (arrangement, processor) = Arrangement()
            .withEventBatches(listOf(listOf(envelope)))
            .withEventProcessorReturning(Either.Right(null))
            .arrange()

        processor.processBatches(arrangement.batches)

        verifySuspend(VerifyMode.not) {
            arrangement.eventRepository.setEventsAsProcessed(any())
        }
    }

    @Test
    fun givenCancellationDuringEventProcessing_whenProcessingEventBatch_thenBatchMarksEventAsProcessed() = runTest(TestKaliumDispatcher.default) {
        val envelope = TestEvent.memberJoin().wrapInEnvelope()
        val eventId = envelope.event.id
        val processingStarted = CompletableDeferred<Unit>()
        val allowProcessingToFinish = CompletableDeferred<Unit>()

        val (arrangement, processor) = Arrangement()
            .withEventBatches(listOf(listOf(envelope)))
            .withSetEventsAsProcessedReturning(Either.Right(Unit))
            .arrange()

        everySuspend {
            arrangement.eventProcessor.processEvent(any(), envelope)
        } calls {
            processingStarted.complete(Unit)
            allowProcessingToFinish.await()
            Either.Right(eventId)
        }

        val job = launch {
            processor.processBatches(arrangement.batches)
        }

        processingStarted.await()
        job.cancel()
        allowProcessingToFinish.complete(Unit)
        job.join()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.eventRepository.setEventsAsProcessed(listOf(eventId))
        }
    }

    @Test
    fun givenMappedPayload_whenDurableReceiving_thenBackendQueueRowIsAcknowledged() = runTest(TestKaliumDispatcher.default) {
        val envelope = TestEvent.memberJoin().wrapInEnvelope().copy(
            payloadIdentity = ReceivedPayloadIdentity("backend-notification", EventContentDTO.Unknown("test.event")),
        )
        val fixture = Arrangement().withEventProcessorReturning(Either.Right(envelope.event.id))
        val database = fixture.database.builder
        database.notificationEventStore.storePage(null, listOf(NewEventEntity("backend-notification", "[]", false, false)))
        val (_, processor) = fixture.arrange(
            durable = true,
            acknowledge = { ids -> database.notificationEventStore.markProcessed(ids, emptyMap()) },
        )

        processor.processBatch(listOf(envelope))

        assertTrue(database.eventDAO.getEventById("backend-notification")!!.isProcessed)
        assertEquals(null, database.notificationEventStore.nextPending())
        verifySuspend(VerifyMode.not) { fixture.eventRepository.setEventsAsProcessed(any()) }
    }

    @Test
    fun givenPayloadIdentity_whenStandardReceiving_thenHandledEventIdIsRetained() = runTest(TestKaliumDispatcher.default) {
        val envelope = TestEvent.memberJoin().wrapInEnvelope().copy(
            payloadIdentity = ReceivedPayloadIdentity("backend-notification", EventContentDTO.Unknown("test.event")),
        )
        val (arrangement, processor) = Arrangement()
            .withEventProcessorReturning(Either.Right(envelope.event.id))
            .arrange()

        processor.processBatch(listOf(envelope))

        verifySuspend(VerifyMode.exactly(1)) { arrangement.eventRepository.setEventsAsProcessed(listOf(envelope.event.id)) }
        verifySuspend(VerifyMode.not) { arrangement.recovery.acknowledge(any()) }
    }

    @Test
    fun givenDebugDisabledProcessing_whenDurableReceiving_thenReceiptsCannotAcknowledgeSkippedEvents() = runTest(TestKaliumDispatcher.default) {
        val envelope = TestEvent.memberLeave().wrapInEnvelope()
        val (arrangement, processor) = Arrangement().arrange(durable = true, effectsProcessed = { _, _ -> true })
        every { arrangement.eventProcessor.disableEventProcessing } returns true

        processor.processBatch(listOf(envelope))

        verifySuspend(VerifyMode.exactly(1)) { arrangement.eventProcessor.processEvent(any(), envelope) }
        verifySuspend(VerifyMode.not) { arrangement.recovery.wereEffectsProcessed(any(), any()) }
        verifySuspend(VerifyMode.not) { arrangement.recovery.acknowledge(any()) }
    }

    @Test
    fun givenCancellationDuringReceiptOrAcknowledgement_whenDurableReceiving_thenOwnedBatchFinishes() = runTest(TestKaliumDispatcher.default) {
        for (cancelDuringReceipt in listOf(true, false)) {
            val envelope = TestEvent.memberJoin().wrapInEnvelope()
            val started = CompletableDeferred<Unit>()
            val finish = CompletableDeferred<Unit>()
            val fixture = Arrangement().withEventProcessorReturning(Either.Right(envelope.event.id))
            val database = fixture.database.builder
            database.notificationEventStore.storePage(null, listOf(NewEventEntity(envelope.event.id, "[]", false, false)))
            val (_, processor) = fixture.arrange(
                durable = true,
                record = { _, _ ->
                    if (cancelDuringReceipt) {
                        started.complete(Unit)
                        finish.await()
                    }
                },
                acknowledge = { ids ->
                    if (!cancelDuringReceipt) {
                        started.complete(Unit)
                        finish.await()
                    }
                    database.notificationEventStore.markProcessed(ids, emptyMap())
                },
            )

            val job = launch { processor.processBatch(listOf(envelope)) }
            started.await()
            job.cancel()
            finish.complete(Unit)
            job.join()

            assertTrue(database.eventDAO.getEventById(envelope.event.id)!!.isProcessed)
            assertEquals(null, database.notificationEventStore.nextPending())
        }
    }

    private suspend fun EventBatchProcessor.processBatches(batches: List<List<EventEnvelope>>) {
        batches.forEach { processBatch(it) }
    }

    private class Arrangement : CryptoTransactionProviderArrangement by CryptoTransactionProviderArrangementMokkeryImpl() {
        val eventProcessor: EventProcessor = mock()
        val eventRepository: EventRepository = mock()
        val recovery: EventBatchRecovery = mock()
        val database = TestUserDatabase(
            userId = QualifiedID("value", "domain").toDao(),
            dispatcher = TestKaliumDispatcher.default,
        )
        var batches: List<List<EventEnvelope>> = emptyList()

        init {
            every { eventProcessor.disableEventProcessing } returns false
            runBlocking {
                withEventProcessorReturning(Either.Right(null))
                withSetEventsAsProcessedReturning(Either.Right(Unit))
            }
        }

        fun withEventBatches(events: List<List<EventEnvelope>>) = apply { batches = events }

        suspend fun withEventProcessorReturning(result: Either<CoreFailure, String?>) = apply {
            everySuspend { eventProcessor.processEvent(any(), any()) } returns result
            everySuspend { eventProcessor.flushPendingSideEffects() } returns Either.Right(Unit)
        }

        suspend fun withEventProcessorFailingWith(failure: CoreFailure) = withEventProcessorReturning(Either.Left(failure))

        suspend fun withSetEventsAsProcessedReturning(result: Either<StorageFailure, Unit>) = apply {
            everySuspend { eventRepository.setEventsAsProcessed(any()) } returns result
        }

        suspend fun arrange(
            durable: Boolean = false,
            record: suspend (CryptoTransactionContext, List<EventEnvelope>) -> Unit = { _, _ -> },
            acknowledge: suspend (List<String>) -> Unit = {},
            effectsProcessed: suspend (CryptoTransactionContext, EventEnvelope) -> Boolean = { _, _ -> false },
        ): Pair<Arrangement, EventBatchProcessor> {
            withTransactionReturning(Either.Right(Unit))
            everySuspend { recovery.wereEffectsProcessed(any(), any()) } calls {
                effectsProcessed(it.args[0] as CryptoTransactionContext, it.args[1] as EventEnvelope)
            }
            everySuspend { recovery.recordProcessed(any(), any()) } calls {
                @Suppress("UNCHECKED_CAST")
                record(it.args[0] as CryptoTransactionContext, it.args[1] as List<EventEnvelope>)
            }
            everySuspend { recovery.acknowledge(any()) } calls {
                @Suppress("UNCHECKED_CAST")
                acknowledge(it.args[0] as List<String>)
            }
            val processor = if (durable) {
                EventBatchProcessor.durable(eventProcessor, cryptoTransactionProvider, database.builder, recovery = { recovery })
            } else {
                EventBatchProcessor.standard(eventProcessor, cryptoTransactionProvider, database.builder, eventRepository)
            }
            return this to processor
        }
    }
}
