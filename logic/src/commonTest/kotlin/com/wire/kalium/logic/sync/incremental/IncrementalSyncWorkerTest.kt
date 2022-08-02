package com.wire.kalium.logic.sync.incremental

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.framework.TestEvent
import com.wire.kalium.logic.sync.KaliumSyncException
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import io.mockative.Mock
import io.mockative.configure
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class IncrementalSyncWorkerTest {

    @Test
    fun givenGathererEmitsEvent_whenPerformingIncrementalSync_thenProcessorShouldReceiveTheEvent() = runTest(TestKaliumDispatcher.default) {
        // Given
        val event = TestEvent.memberJoin()
        val (arrangement, worker) = Arrangement()
            .withEventGathererReturning(flowOf(event))
            .arrange()

        // When
        worker.performIncrementalSync()
        advanceUntilIdle()

        // Then
        verify(arrangement.eventProcessor)
            .suspendFunction(arrangement.eventProcessor::processEvent)
            .with(eq(event))
            .wasInvoked(exactly = once)
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
            worker.performIncrementalSync()
            advanceUntilIdle()
        }

        assertEquals(exception, resultException)
    }

    private class Arrangement {

        @Mock
        val eventProcessor: EventProcessor = configure(mock(EventProcessor::class)) { stubsUnitByDefault = true }

        @Mock
        val eventGatherer: EventGatherer = mock(EventGatherer::class)

        fun withEventGathererReturning(eventFlow: Flow<Event>) = apply {
            given(eventGatherer)
                .suspendFunction(eventGatherer::gatherEvents)
                .whenInvoked()
                .thenReturn(eventFlow)
        }

        fun withEventGathererThrowing(throwable: Throwable) = apply {
            given(eventGatherer)
                .suspendFunction(eventGatherer::gatherEvents)
                .whenInvoked()
                .thenThrow(throwable)
        }

        private val incrementalSyncWorker = IncrementalSyncWorkerImpl(
            eventGatherer, eventProcessor
        )

        fun arrange() = this to incrementalSyncWorker

    }
}
