package com.wire.kalium.logic.sync.incremental

import com.wire.kalium.logic.data.sync.InMemorySlowSyncRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncStatus
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.data.sync.SlowSyncStatus
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import com.wire.kalium.logic.util.flowThatFailsOnFirstTime
import io.mockative.Mock
import io.mockative.classOf
import io.mockative.configure
import io.mockative.eq
import io.mockative.given
import io.mockative.matching
import io.mockative.mock
import io.mockative.once
import io.mockative.twice
import io.mockative.verify
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals

class IncrementalSyncManagerTest {

    @Test
    fun givenSlowSyncIsComplete_whenStartingIncrementalManager_thenShouldStartWorker() = runTest(TestKaliumDispatcher.default) {
        val sharedFlow = MutableSharedFlow<EventSource>()

        val (arrangement, _) = Arrangement()
            .withWorkerReturning(sharedFlow)
            .arrange()
        arrangement.slowSyncRepository.updateSlowSyncStatus(SlowSyncStatus.Complete)

        advanceUntilIdle()

        verify(arrangement.incrementalSyncWorker)
            .suspendFunction(arrangement.incrementalSyncWorker::incrementalSyncFlow)
            .wasInvoked(exactly = once)
        assertEquals(1, sharedFlow.subscriptionCount.value)
    }

    @Test
    fun givenSlowSyncIsNotComplete_whenStartingIncrementalManager_thenShouldNotStartWorker() = runTest(TestKaliumDispatcher.default) {
        val sharedFlow = MutableSharedFlow<EventSource>()

        val (arrangement, _) = Arrangement()
            .withWorkerReturning(sharedFlow)
            .arrange()
        arrangement.slowSyncRepository.updateSlowSyncStatus(SlowSyncStatus.Pending)

        advanceUntilIdle()

        verify(arrangement.incrementalSyncWorker)
            .suspendFunction(arrangement.incrementalSyncWorker::incrementalSyncFlow)
            .wasNotInvoked()
        assertEquals(0, sharedFlow.subscriptionCount.value)
    }

    @Test
    fun givenSlowSyncIsCompleted_whenWorkerEmitsSources_thenShouldUpdateRepositoryWithState() = runTest(TestKaliumDispatcher.default) {
        val sourceFlow = Channel<EventSource>(Channel.UNLIMITED)

        val (arrangement, _) = Arrangement()
            .withWorkerReturning(sourceFlow.consumeAsFlow())
            .arrange()
        arrangement.slowSyncRepository.updateSlowSyncStatus(SlowSyncStatus.Complete)

        sourceFlow.send(EventSource.PENDING)
        advanceUntilIdle()
        verify(arrangement.incrementalSyncRepository)
            .function(arrangement.incrementalSyncRepository::updateIncrementalSyncState)
            .with(eq(IncrementalSyncStatus.FetchingPendingEvents))
            .wasInvoked(exactly = once)
        sourceFlow.send(EventSource.LIVE)
        advanceUntilIdle()
        verify(arrangement.incrementalSyncRepository)
            .function(arrangement.incrementalSyncRepository::updateIncrementalSyncState)
            .with(eq(IncrementalSyncStatus.Live))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenSlowSyncIsCompleted_whenWorkerThrows_thenShouldUpdateRepositoryWithFailedState() = runTest(TestKaliumDispatcher.default) {
        val (arrangement, _) = Arrangement()
            .withWorkerReturning(flowThatFailsOnFirstTime())
            .arrange()
        arrangement.slowSyncRepository.updateSlowSyncStatus(SlowSyncStatus.Complete)

        advanceUntilIdle()
        verify(arrangement.incrementalSyncRepository)
            .function(arrangement.incrementalSyncRepository::updateIncrementalSyncState)
            .with(matching { it is IncrementalSyncStatus.Failed })
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenSlowSyncIsCompleted_whenWorkerThrowsNonCancellation_thenShouldRetry() = runTest(TestKaliumDispatcher.default) {
        val (arrangement, _) = Arrangement()
            .withWorkerReturning(flowThatFailsOnFirstTime())
            .arrange()
        arrangement.slowSyncRepository.updateSlowSyncStatus(SlowSyncStatus.Complete)

        advanceUntilIdle()

        verify(arrangement.incrementalSyncWorker)
            .suspendFunction(arrangement.incrementalSyncWorker::incrementalSyncFlow)
            .wasInvoked(exactly = twice)
    }

    @Test
    fun givenSlowSyncIsCompleted_whenWorkerThrowsCancellation_thenShouldNotRetry() = runTest(TestKaliumDispatcher.default) {
        val (arrangement, _) = Arrangement()
            .withWorkerReturning(flowThatFailsOnFirstTime(CancellationException("Cancelled")))
            .arrange()
        arrangement.slowSyncRepository.updateSlowSyncStatus(SlowSyncStatus.Complete)

        advanceUntilIdle()

        verify(arrangement.incrementalSyncWorker)
            .suspendFunction(arrangement.incrementalSyncWorker::incrementalSyncFlow)
            .wasInvoked(exactly = once)
    }

    private class Arrangement {

        val slowSyncRepository: SlowSyncRepository = InMemorySlowSyncRepository()

        @Mock
        val incrementalSyncWorker = mock(classOf<IncrementalSyncWorker>())

        @Mock
        val incrementalSyncRepository = configure(mock(classOf<IncrementalSyncRepository>())) { stubsUnitByDefault = true }

        private val incrementalSyncManager = IncrementalSyncManager(
            slowSyncRepository, incrementalSyncWorker, incrementalSyncRepository, TestKaliumDispatcher
        )

        fun withWorkerReturning(sourceFlow: Flow<EventSource>) = apply {
            given(incrementalSyncWorker)
                .suspendFunction(incrementalSyncWorker::incrementalSyncFlow)
                .whenInvoked()
                .thenReturn(sourceFlow)
        }

        fun arrange() = this to incrementalSyncManager

    }
}
