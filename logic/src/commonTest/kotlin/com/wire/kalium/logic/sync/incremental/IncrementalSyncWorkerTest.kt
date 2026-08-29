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
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.logic.framework.TestEvent
import com.wire.kalium.logic.framework.TestEvent.wrapInEnvelope
import com.wire.kalium.logic.sync.KaliumSyncException
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class IncrementalSyncWorkerTest {

    @Test
    fun givenGathererEmitsEvents_whenPerformingIncrementalSync_thenWorkerDelegatesTheCompleteEventList() =
        runTest(TestKaliumDispatcher.default) {
            val envelopes = listOf(
                TestEvent.memberJoin("event-1").wrapInEnvelope(),
                TestEvent.memberJoin("event-2").wrapInEnvelope(),
            )
            val (arrangement, worker) = Arrangement()
                .withEventGathererReturning(flowOf(EventStreamData.NewEvents(envelopes)))
                .arrange()

            worker.processEventsFlow().collect()

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.eventBatchProcessor.processEvents(eq(envelopes))
            }
        }

    @Test
    fun givenGathererEmitsSinglePageOfEvents_whenPerformingIncrementalSync_thenWorkerShouldEmitPendingSource() =
        runTest(TestKaliumDispatcher.default) {
            val event = TestEvent.memberJoin().wrapInEnvelope()
            val (_, worker) = Arrangement()
                .withEventGathererReturning(flowOf(EventStreamData.NewEvents(listOf(event))))
                .arrange()

            worker.processEventsFlow().test {
                assertEquals(EventSource.PENDING, awaitItem())
                awaitComplete()
            }
        }

    @Test
    fun givenGathererEmitsLiveSource_whenPerformingIncrementalSync_thenWorkerShouldEmitLiveSource() =
        runTest(TestKaliumDispatcher.default) {
            val event = TestEvent.memberJoin().wrapInEnvelope()
            val (_, worker) = Arrangement()
                .withEventGathererReturning(
                    flowOf(
                        EventStreamData.NewEvents(listOf(event)),
                        EventStreamData.IsUpToDate,
                    )
                )
                .arrange()

            worker.processEventsFlow().test {
                assertEquals(EventSource.PENDING, awaitItem())
                assertEquals(EventSource.LIVE, awaitItem())
                awaitComplete()
            }
        }

    @Test
    fun givenGathererThrows_whenPerformingIncrementalSync_thenTheFailureIsPropagated() =
        runTest(TestKaliumDispatcher.default) {
            val exception = KaliumSyncException("Oopsie", NetworkFailure.NoNetworkConnection(null))
            val (_, worker) = Arrangement()
                .withEventGathererReturning(flow { throw exception })
                .arrange()

            val resultException = assertFails {
                worker.processEventsFlow().collect()
            }

            assertEquals(exception, resultException)
        }

    @Test
    fun givenBatchProcessorThrows_whenPerformingIncrementalSync_thenTheFailureIsPropagated() = runTest {
        val event = TestEvent.memberJoin().wrapInEnvelope()
        val exception = KaliumSyncException("Processing failed", NetworkFailure.NoNetworkConnection(null))
        val (_, worker) = Arrangement()
            .withEventGathererReturning(flowOf(EventStreamData.NewEvents(listOf(event))))
            .withEventBatchProcessorThrowing(exception)
            .arrange()

        val resultException = assertFails {
            worker.processEventsFlow().collect()
        }

        assertEquals(exception, resultException)
    }

    private class Arrangement {
        val eventBatchProcessor: EventBatchProcessor = mock()
        private val eventGatherer: EventGatherer = mock()

        init {
            everySuspend { eventBatchProcessor.processEvents(any()) } returns Unit
        }

        fun withEventGathererReturning(eventFlow: Flow<EventStreamData>) = apply {
            everySuspend { eventGatherer.gatherEvents() } returns eventFlow
        }

        fun withEventBatchProcessorThrowing(exception: KaliumSyncException) = apply {
            everySuspend { eventBatchProcessor.processEvents(any()) } throws exception
        }

        fun arrange() = this to IncrementalSyncWorkerImpl(eventGatherer, eventBatchProcessor)
    }
}
