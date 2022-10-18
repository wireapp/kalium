package com.wire.kalium.logic.sync.slow

import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.data.sync.SlowSyncStatus
import com.wire.kalium.logic.data.sync.SlowSyncStep
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import com.wire.kalium.logic.util.flowThatFailsOnFirstTime
import io.mockative.Mock
import io.mockative.classOf
import io.mockative.configure
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.twice
import io.mockative.verify
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SlowSyncManagerTest {

    @Test
    fun givenCriteriaAreMet_whenManagerIsCreated_thenShouldStartSlowSync() = runTest(TestKaliumDispatcher.default) {
        var isCollected = false
        val stepFlow = flow<SlowSyncStep> { isCollected = true }
        val (arrangement, _) = Arrangement()
            .withSatisfiedCriteria()
            .withSlowSyncWorkerReturning(stepFlow)
            .arrange()

        advanceUntilIdle()

        assertTrue(isCollected)
        verify(arrangement.slowSyncWorker)
            .suspendFunction(arrangement.slowSyncWorker::performSlowSyncSteps)
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenCriteriaAreMet_whenWorkerThrowsNonCancellation_thenShouldRetry() = runTest(TestKaliumDispatcher.default) {
        val (arrangement, _) = Arrangement()
            .withSatisfiedCriteria()
            .withSlowSyncWorkerReturning(flowThatFailsOnFirstTime())
            .arrange()

        advanceUntilIdle()

        verify(arrangement.slowSyncWorker)
            .suspendFunction(arrangement.slowSyncWorker::performSlowSyncSteps)
            .wasInvoked(exactly = twice)
    }

    @Test
    fun givenCriteriaAreMet_whenWorkerThrowsCancellation_thenShouldNotRetry() = runTest(TestKaliumDispatcher.default) {
        val (arrangement, _) = Arrangement()
            .withSatisfiedCriteria()
            .withSlowSyncWorkerReturning(flowThatFailsOnFirstTime(CancellationException("Cancelled")))
            .arrange()

        advanceUntilIdle()

        verify(arrangement.slowSyncWorker)
            .suspendFunction(arrangement.slowSyncWorker::performSlowSyncSteps)
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenCriteriaAreMet_whenStepsAreOver_thenShouldUpdateStateInRepository() = runTest(TestKaliumDispatcher.default) {
        val (arrangement, _) = Arrangement()
            .withSatisfiedCriteria()
            .withSlowSyncWorkerReturning(emptyFlow())
            .arrange()

        advanceUntilIdle()

        verify(arrangement.slowSyncRepository)
            .function(arrangement.slowSyncRepository::updateSlowSyncStatus)
            .with(eq(SlowSyncStatus.Complete))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenCriteriaAreMet_whenWorkerEmitsAStep_thenShouldUpdateStateInRepository() = runTest(TestKaliumDispatcher.default) {
        val stepChannel = Channel<SlowSyncStep>(Channel.UNLIMITED)
        val (arrangement, _) = Arrangement()
            .withSatisfiedCriteria()
            .withSlowSyncWorkerReturning(stepChannel.consumeAsFlow())
            .arrange()

        val step = SlowSyncStep.CONTACTS
        advanceUntilIdle()

        verify(arrangement.slowSyncRepository)
            .function(arrangement.slowSyncRepository::updateSlowSyncStatus)
            .with(eq(SlowSyncStatus.Ongoing(step)))
            .wasNotInvoked()

        stepChannel.send(step)
        advanceUntilIdle()

        verify(arrangement.slowSyncRepository)
            .function(arrangement.slowSyncRepository::updateSlowSyncStatus)
            .with(eq(SlowSyncStatus.Ongoing(step)))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenCriteriaAreMet_whenCriteriaAreBroken_thenShouldCancelCollection() = runTest(TestKaliumDispatcher.default) {
        val criteriaChannel = Channel<SyncCriteriaResolution>(Channel.UNLIMITED)
        val stepSharedFlow = MutableSharedFlow<SlowSyncStep>()
        val (_, _) = Arrangement()
            .withCriteriaProviderReturning(criteriaChannel.consumeAsFlow())
            .withSlowSyncWorkerReturning(stepSharedFlow)
            .arrange()

        criteriaChannel.send(SyncCriteriaResolution.Ready)
        advanceUntilIdle()

        // One collector
        assertEquals(1, stepSharedFlow.subscriptionCount.value)

        criteriaChannel.send(SyncCriteriaResolution.MissingRequirement("Missing requirement"))
        advanceUntilIdle()
        // No more collectors
        assertEquals(0, stepSharedFlow.subscriptionCount.value)
    }

    @Test
    fun givenCriteriaAreNotMet_whenManagerIsCreated_thenShouldNotStartSlowSync() = runTest(TestKaliumDispatcher.default) {
        var isCollected = false
        val stepFlow = flow<SlowSyncStep> { isCollected = true }
        val (_, _) = Arrangement()
            .withCriteriaProviderReturning(flowOf(SyncCriteriaResolution.MissingRequirement("Requirement Missing")))
            .withSlowSyncWorkerReturning(stepFlow)
            .arrange()

        advanceUntilIdle()

        assertFalse(isCollected)
    }

    private class Arrangement {

        @Mock
        val syncCriteriaProvider: SyncCriteriaProvider = mock(classOf<SyncCriteriaProvider>())

        @Mock
        val slowSyncRepository: SlowSyncRepository = configure(mock(classOf<SlowSyncRepository>())) { stubsUnitByDefault = true }

        @Mock
        val slowSyncWorker: SlowSyncWorker = mock(classOf<SlowSyncWorker>())

        fun withCriteriaProviderReturning(criteriaFlow: Flow<SyncCriteriaResolution>) = apply {
            given(syncCriteriaProvider)
                .suspendFunction(syncCriteriaProvider::syncCriteriaFlow)
                .whenInvoked()
                .thenReturn(criteriaFlow)
        }

        fun withSatisfiedCriteria() = withCriteriaProviderReturning(flowOf(SyncCriteriaResolution.Ready))

        fun withSlowSyncWorkerReturning(stepFlow: Flow<SlowSyncStep>) = apply {
            given(slowSyncWorker)
                .suspendFunction(slowSyncWorker::performSlowSyncSteps)
                .whenInvoked()
                .thenReturn(stepFlow)
        }

        private val slowSyncManager = SlowSyncManager(
            syncCriteriaProvider,
            slowSyncRepository,
            slowSyncWorker,
            TestKaliumDispatcher
        )

        fun arrange() = this to slowSyncManager

    }
}
