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
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementMokkeryImpl
import com.wire.kalium.network.api.authenticated.notification.EventContentDTO
import com.wire.kalium.persistence.TestUserDatabase
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
import kotlin.test.assertTrue

class EventBatchProcessorTest {

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
        // Side effects of the events that succeeded are kept: their messages are already decrypted.
        verifySuspend(VerifyMode.exactly(1)) { arrangement.eventProcessor.flushPendingSideEffects() }
        verifySuspend(VerifyMode.not) { arrangement.eventRepository.setEventsAsProcessed(any()) }
    }

    @Test
    fun givenProcessingOrFlushFails_whenProcessingBatch_thenCryptoCommitsButNothingIsAcknowledged() = runTest(TestKaliumDispatcher.default) {
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
            assertTrue(callbackReturned, "A failed batch commits native crypto and is retried")
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
    fun failedStandardAcknowledgementStopsBeforeTheNextBatch() = runTest(TestKaliumDispatcher.default) {
        val first = TestEvent.memberJoin().wrapInEnvelope()
        val later = TestEvent.memberLeave().wrapInEnvelope()
        val failure = StorageFailure.Generic(IllegalStateException("marker write failed"))
        val (arrangement, processor) = Arrangement()
            .withEventBatches(listOf(listOf(first), listOf(later)))
            .withEventProcessorReturning(Either.Right(first.event.id))
            .withSetEventsAsProcessedReturning(Either.Left(failure))
            .arrange()

        val thrown = assertFailsWith<KaliumSyncException> { processor.processBatches(arrangement.batches) }

        assertEquals(failure, thrown.coreFailureCause)
        verifySuspend(VerifyMode.exactly(1)) { arrangement.eventRepository.setEventsAsProcessed(listOf(first.event.id)) }
        verifySuspend(VerifyMode.not) { arrangement.eventProcessor.processEvent(any(), later) }
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
    fun givenPayloadIdentity_whenStandardReceiving_thenHandledEventIdIsRetained() = runTest(TestKaliumDispatcher.default) {
        val envelope = TestEvent.memberJoin().wrapInEnvelope().copy(
            payloadIdentity = ReceivedPayloadIdentity("backend-notification", EventContentDTO.Unknown("test.event")),
        )
        val (arrangement, processor) = Arrangement()
            .withEventProcessorReturning(Either.Right(envelope.event.id))
            .arrange()

        processor.processBatch(listOf(envelope))

        verifySuspend(VerifyMode.exactly(1)) { arrangement.eventRepository.setEventsAsProcessed(listOf(envelope.event.id)) }
    }

    private suspend fun EventBatchProcessor.processBatches(batches: List<List<EventEnvelope>>) {
        batches.forEach { processBatch(it) }
    }

    private class Arrangement : CryptoTransactionProviderArrangement by CryptoTransactionProviderArrangementMokkeryImpl() {
        val eventProcessor: EventProcessor = mock()
        val eventRepository: EventRepository = mock()
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

        suspend fun arrange(): Pair<Arrangement, EventBatchProcessor> {
            withTransactionReturning(Either.Right(Unit))
            val processor = EventBatchProcessor.standard(eventProcessor, cryptoTransactionProvider, database.builder, eventRepository)
            return this to processor
        }
    }
}
