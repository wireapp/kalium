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
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.event.EventEnvelope
import com.wire.kalium.logic.framework.TestEvent
import com.wire.kalium.logic.framework.TestEvent.wrapInEnvelope
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.sync.KaliumSyncException
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.every
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
    fun givenGathererEmitsEvent_whenPerformingIncrementalSync_thenProcessorShouldReceiveTheEvent() = runTest(TestKaliumDispatcher.default) {
        // Given
        val envelope = TestEvent.memberJoin().wrapInEnvelope()
        val (arrangement, worker) = Arrangement()
            .withEventGathererSourceReturning(MutableStateFlow(EventSource.LIVE))
            .withEventGathererReturning(flowOf(envelope))
            .arrange()

        // When
        worker.processEventsWhilePolicyAllowsFlow().collect()

        // Then
        coVerify {
            arrangement.eventProcessor.processEvent(eq(envelope))
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenGathererEmitsEventDuringLiveSource_whenPerformingIncrementalSync_thenWorkerShouldEmitLiveSource() =
        runTest(TestKaliumDispatcher.default) {
            // Given
            val event = TestEvent.memberJoin().wrapInEnvelope()
            val (_, worker) = Arrangement()
                .withEventGathererReturning(flowOf(event))
                .withEventGathererSourceReturning(MutableStateFlow(EventSource.LIVE))
                .arrange()

            // When
            worker.processEventsWhilePolicyAllowsFlow().test {
                // Then
                assertEquals(EventSource.LIVE, awaitItem())
                awaitComplete()
            }
        }

    @Test
    fun givenGathererEmitsEventDuringPendingSource_whenPerformingIncrementalSync_thenWorkerShouldEmitPendingSource() =
        runTest(TestKaliumDispatcher.default) {
            // Given
            val event = TestEvent.memberJoin().wrapInEnvelope()
            val (_, worker) = Arrangement()
                .withEventGathererReturning(flowOf(event))
                .withEventGathererSourceReturning(MutableStateFlow(EventSource.PENDING))
                .arrange()

            // When
            worker.processEventsWhilePolicyAllowsFlow().test {
                // Then
                assertEquals(EventSource.PENDING, awaitItem())
                awaitComplete()
            }
        }

    @Test
    fun givenGathererThrows_whenPerformingIncrementalSync_thenTheFailureIsPropagated() = runTest(TestKaliumDispatcher.default) {
        // Given
        val coreFailureCause = NetworkFailure.NoNetworkConnection(null)
        val exception = KaliumSyncException("Oopsie", coreFailureCause)
        val (_, worker) = Arrangement()
            .withEventGathererSourceReturning(MutableStateFlow(EventSource.PENDING))
            .withEventGathererReturning(flow { throw exception })
            .arrange()

        // When
        val resultException = assertFails {
            worker.processEventsWhilePolicyAllowsFlow().collect()
        }

        assertEquals(exception, resultException)
    }

    @Test
    fun givenProcessorFails_whenPerformingIncrementalSync_thenShouldThrowKaliumSyncException() = runTest {
        val coreFailureCause = NetworkFailure.NoNetworkConnection(null)
        val (_, worker) = Arrangement()
            .withEventGathererSourceReturning(MutableStateFlow(EventSource.PENDING))
            .withEventGathererReturning(flowOf(TestEvent.memberJoin().wrapInEnvelope()))
            .withEventProcessorFailingWith(coreFailureCause)
            .arrange()

        val resultException = assertFailsWith<KaliumSyncException> {
            worker.processEventsWhilePolicyAllowsFlow().collect()
        }

        assertEquals(coreFailureCause, resultException.coreFailureCause)
    }

    private class Arrangement {

        @Mock
        val eventProcessor: EventProcessor = mock(EventProcessor::class)

        @Mock
        val eventGatherer: EventGatherer = mock(EventGatherer::class)

        init {
            runBlocking {
                withEventProcessorSucceeding()
            }
        }

        suspend fun withEventGathererReturning(eventFlow: Flow<EventEnvelope>) = apply {
            coEvery {
                eventGatherer.gatherEvents()
            }.returns(eventFlow)
        }

        fun withEventGathererSourceReturning(sourceFlow: StateFlow<EventSource>) = apply {
            every {
                eventGatherer.currentSource
            }.returns(sourceFlow)
        }

        suspend fun withEventGathererThrowing(throwable: Throwable) = apply {
            coEvery { eventGatherer.gatherEvents() }
                .throws(throwable)
        }

        suspend fun withEventProcessorReturning(result: Either<CoreFailure, Unit>) = apply {
            coEvery {
                eventProcessor.processEvent(any())
            }.returns(result)
        }

        suspend fun withEventProcessorSucceeding() = withEventProcessorReturning(Either.Right(Unit))

        suspend fun withEventProcessorFailingWith(failure: CoreFailure) = withEventProcessorReturning(Either.Left(failure))

        fun arrange() = this to IncrementalSyncWorkerImpl(
            eventGatherer, eventProcessor
        )
    }
}
