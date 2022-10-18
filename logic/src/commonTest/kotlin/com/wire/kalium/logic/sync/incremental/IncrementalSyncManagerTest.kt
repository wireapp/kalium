package com.wire.kalium.logic.sync.incremental

import com.wire.kalium.logic.data.sync.ConnectionPolicy
import com.wire.kalium.logic.data.sync.SlowSyncRepositoryImpl
import com.wire.kalium.logic.data.sync.IncrementalSyncRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncStatus
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.data.sync.SlowSyncStatus
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import com.wire.kalium.logic.util.flowThatFailsOnFirstTime
import com.wire.kalium.persistence.TestUserDatabase
import com.wire.kalium.persistence.dao.UserIDEntity
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.emptyFlow
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
            .withKeepAliveConnectionPolicy()
            .arrange()
        arrangement.slowSyncRepository.updateSlowSyncStatus(SlowSyncStatus.Complete)

        advanceUntilIdle()

        verify(arrangement.incrementalSyncWorker)
            .suspendFunction(arrangement.incrementalSyncWorker::processEventsWhilePolicyAllowsFlow)
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
            .suspendFunction(arrangement.incrementalSyncWorker::processEventsWhilePolicyAllowsFlow)
            .wasNotInvoked()
        assertEquals(0, sharedFlow.subscriptionCount.value)
    }

    @Test
    fun givenSlowSyncIsCompleted_whenWorkerEmitsSources_thenShouldUpdateRepositoryWithState() = runTest(TestKaliumDispatcher.default) {
        val sourceFlow = Channel<EventSource>(Channel.UNLIMITED)

        val (arrangement, _) = Arrangement()
            .withWorkerReturning(sourceFlow.consumeAsFlow())
            .withKeepAliveConnectionPolicy()
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
            .withKeepAliveConnectionPolicy()
            .arrange()
        arrangement.slowSyncRepository.updateSlowSyncStatus(SlowSyncStatus.Complete)

        advanceUntilIdle()
        verify(arrangement.incrementalSyncRepository)
            .suspendFunction(arrangement.incrementalSyncRepository::updateIncrementalSyncState)
            .with(matching { it is IncrementalSyncStatus.Failed })
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenSlowSyncIsCompleted_whenWorkerThrowsNonCancellation_thenShouldRetry() = runTest(TestKaliumDispatcher.default) {
        val (arrangement, _) = Arrangement()
            .withWorkerReturning(flowThatFailsOnFirstTime())
            // EventProcessing will be called twice if it ends and policy is KEEP_ALIVE
            // So, DISCONNECT is used for a realistic test scenario
            .withDisconnectConnectionPolicy()
            .arrange()
        arrangement.slowSyncRepository.updateSlowSyncStatus(SlowSyncStatus.Complete)

        advanceUntilIdle()

        verify(arrangement.incrementalSyncWorker)
            .suspendFunction(arrangement.incrementalSyncWorker::processEventsWhilePolicyAllowsFlow)
            .wasInvoked(exactly = twice)
    }

    @Test
    fun givenSlowSyncIsCompleted_whenWorkerThrowsCancellation_thenShouldNotRetry() = runTest(TestKaliumDispatcher.default) {
        val (arrangement, _) = Arrangement()
            .withWorkerReturning(flowThatFailsOnFirstTime(CancellationException("Cancelled")))
            .withKeepAliveConnectionPolicy()
            .arrange()
        arrangement.slowSyncRepository.updateSlowSyncStatus(SlowSyncStatus.Complete)

        advanceUntilIdle()

        verify(arrangement.incrementalSyncWorker)
            .suspendFunction(arrangement.incrementalSyncWorker::processEventsWhilePolicyAllowsFlow)
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenWorkerEndsAndDisconnectPolicy_whenPolicyIsUpgraded_thenShouldRetry() = runTest(TestKaliumDispatcher.default) {
        val connectionPolicyState = MutableStateFlow(ConnectionPolicy.DISCONNECT_AFTER_PENDING_EVENTS)

        val (arrangement, _) = Arrangement()
            .withWorkerReturning(emptyFlow())
            .withConnectionPolicyReturning(connectionPolicyState)
            .arrange()
        arrangement.slowSyncRepository.updateSlowSyncStatus(SlowSyncStatus.Complete)

        advanceUntilIdle()

        // Starts processing once until it ends
        verify(arrangement.incrementalSyncWorker)
            .suspendFunction(arrangement.incrementalSyncWorker::processEventsWhilePolicyAllowsFlow)
            .wasInvoked(exactly = once)

        // Policy is upgraded
        connectionPolicyState.value = ConnectionPolicy.KEEP_ALIVE
        advanceUntilIdle()

        // Starts processing again
        verify(arrangement.incrementalSyncWorker)
            .suspendFunction(arrangement.incrementalSyncWorker::processEventsWhilePolicyAllowsFlow)
            .wasInvoked(exactly = once)

        // Policy is downgraded and upgraded again
        connectionPolicyState.value = ConnectionPolicy.DISCONNECT_AFTER_PENDING_EVENTS
        advanceUntilIdle()
        connectionPolicyState.value = ConnectionPolicy.KEEP_ALIVE
        advanceUntilIdle()

        // Starts processing one more time. Three times in total.
        verify(arrangement.incrementalSyncWorker)
            .suspendFunction(arrangement.incrementalSyncWorker::processEventsWhilePolicyAllowsFlow)
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenDisconnectPolicy_whenWorkerCompletes_thenShouldUpdateIncrementalSyncStatusToPending() = runTest(TestKaliumDispatcher.default) {
        val connectionPolicyState = MutableStateFlow(ConnectionPolicy.DISCONNECT_AFTER_PENDING_EVENTS)

        val (arrangement, _) = Arrangement()
            .withWorkerReturning(emptyFlow())
            .withConnectionPolicyReturning(connectionPolicyState)
            .arrange()
        arrangement.slowSyncRepository.updateSlowSyncStatus(SlowSyncStatus.Complete)

        advanceUntilIdle()

        verify(arrangement.incrementalSyncRepository)
            .suspendFunction(arrangement.incrementalSyncRepository::updateIncrementalSyncState)
            .with(eq(IncrementalSyncStatus.Pending))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenPolicyUpAndDowngrade_whenWorkerTheSecondTime_thenShouldUpdateIncrementalSyncStatusToPendingAgain() =
        runTest(TestKaliumDispatcher.default) {
            val connectionPolicyState = MutableStateFlow(ConnectionPolicy.DISCONNECT_AFTER_PENDING_EVENTS)

            val (arrangement, _) = Arrangement()
                .withWorkerReturning(emptyFlow())
                .withConnectionPolicyReturning(connectionPolicyState)
                .arrange()
            arrangement.slowSyncRepository.updateSlowSyncStatus(SlowSyncStatus.Complete)

            advanceUntilIdle()

            // Upgrade
            connectionPolicyState.value = ConnectionPolicy.KEEP_ALIVE
            advanceUntilIdle()

            // Downgrade
            connectionPolicyState.value = ConnectionPolicy.DISCONNECT_AFTER_PENDING_EVENTS
            advanceUntilIdle()

            verify(arrangement.incrementalSyncRepository)
                .suspendFunction(arrangement.incrementalSyncRepository::updateIncrementalSyncState)
                .with(eq(IncrementalSyncStatus.Pending))
                .wasInvoked(exactly = twice)
        }

    private class Arrangement {

        val database = TestUserDatabase(UserIDEntity("SELF_USER", "DOMAIN"))
        val slowSyncRepository: SlowSyncRepository = SlowSyncRepositoryImpl(database.builder.metadataDAO)

        @Mock
        val incrementalSyncWorker = mock(classOf<IncrementalSyncWorker>())

        @Mock
        val incrementalSyncRepository = configure(mock(classOf<IncrementalSyncRepository>())) { stubsUnitByDefault = true }

        private val incrementalSyncManager by lazy {
            IncrementalSyncManager(
                slowSyncRepository, incrementalSyncWorker, incrementalSyncRepository, TestKaliumDispatcher
            )
        }

        fun withWorkerReturning(sourceFlow: Flow<EventSource>) = apply {
            given(incrementalSyncWorker)
                .suspendFunction(incrementalSyncWorker::processEventsWhilePolicyAllowsFlow)
                .whenInvoked()
                .thenReturn(sourceFlow)
        }

        fun withConnectionPolicyReturning(connectionPolicyFlow: StateFlow<ConnectionPolicy>) = apply {
            given(incrementalSyncRepository)
                .getter(incrementalSyncRepository::connectionPolicyState)
                .whenInvoked()
                .thenReturn(connectionPolicyFlow)
        }

        fun withKeepAliveConnectionPolicy() = apply {
            given(incrementalSyncRepository)
                .getter(incrementalSyncRepository::connectionPolicyState)
                .whenInvoked()
                .thenReturn(MutableStateFlow(ConnectionPolicy.KEEP_ALIVE))
        }

        fun withDisconnectConnectionPolicy() = apply {
            given(incrementalSyncRepository)
                .getter(incrementalSyncRepository::connectionPolicyState)
                .whenInvoked()
                .thenReturn(MutableStateFlow(ConnectionPolicy.DISCONNECT_AFTER_PENDING_EVENTS))
        }

        fun arrange() = this to incrementalSyncManager

    }
}
