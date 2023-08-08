/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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

import com.wire.kalium.logic.data.sync.ConnectionPolicy
import com.wire.kalium.logic.data.sync.IncrementalSyncRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncStatus
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.data.sync.SlowSyncRepositoryImpl
import com.wire.kalium.logic.data.sync.SlowSyncStatus
import com.wire.kalium.network.NetworkState
import com.wire.kalium.network.NetworkStateObserver
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import com.wire.kalium.logic.util.ExponentialDurationHelper
import com.wire.kalium.logic.util.flowThatFailsOnFirstTime
import com.wire.kalium.persistence.TestUserDatabase
import com.wire.kalium.persistence.dao.UserIDEntity
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.configure
import io.mockative.eq
import io.mockative.given
import io.mockative.matching
import io.mockative.mock
import io.mockative.once
import io.mockative.times
import io.mockative.twice
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
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
            .withRecoveringFromFailure()
            .arrange()

        arrangement.slowSyncRepository.updateSlowSyncStatus(SlowSyncStatus.Complete)
        advanceUntilIdle()

        verify(arrangement.incrementalSyncRecoveryHandler)
            .suspendFunction(arrangement.incrementalSyncRecoveryHandler::recover)
            .with(any(), any())
            .wasInvoked(exactly = once)

        verify(arrangement.incrementalSyncWorker)
            .suspendFunction(arrangement.incrementalSyncWorker::processEventsWhilePolicyAllowsFlow)
            .wasInvoked(exactly = twice)
    }

    @Test
    fun givenSlowSyncIsCompleted_whenWorkerThrowsCancellation_thenShouldNotRetry() = runTest(TestKaliumDispatcher.default) {
        val (arrangement, _) = Arrangement()
            .withWorkerReturning(flowThatFailsOnFirstTime(CancellationException("Cancelled")))
            .withKeepAliveConnectionPolicy()
            .withRecoveringFromFailure()
            .arrange()

        arrangement.slowSyncRepository.updateSlowSyncStatus(SlowSyncStatus.Complete)
        advanceUntilIdle()

        verify(arrangement.incrementalSyncRecoveryHandler)
            .suspendFunction(arrangement.incrementalSyncRecoveryHandler::recover)
            .with(any(), any())
            .wasNotInvoked()

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
            .withRecoveringFromFailure()
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

    @Test
    fun givenBothSyncsAreCompleted_whenWorkerEmitsSources_thenShouldResetExponentialDuration() = runTest(TestKaliumDispatcher.default) {
        val sourceFlow = Channel<EventSource>(Channel.UNLIMITED)

        val (arrangement, _) = Arrangement()
            .withWorkerReturning(sourceFlow.consumeAsFlow())
            .withKeepAliveConnectionPolicy()
            .arrange()
        arrangement.slowSyncRepository.updateSlowSyncStatus(SlowSyncStatus.Complete)

        sourceFlow.send(EventSource.LIVE)
        advanceUntilIdle()

        verify(arrangement.exponentialDurationHelper)
            .function(arrangement.exponentialDurationHelper::reset)
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenSlowSyncIsCompleted_whenRecovers_thenShouldCalculateNextExponentialDelay() = runTest(TestKaliumDispatcher.default) {
        val (arrangement, _) = Arrangement()
            .withWorkerReturning(flowThatFailsOnFirstTime())
            .withDisconnectConnectionPolicy()
            .withRecoveringFromFailure()
            .withNextExponentialDuration(1.seconds)
            .arrange()

        arrangement.slowSyncRepository.updateSlowSyncStatus(SlowSyncStatus.Complete)
        advanceUntilIdle()

            verify(arrangement.exponentialDurationHelper)
                .function(arrangement.exponentialDurationHelper::next)
                .wasInvoked(exactly = once)
        }

    @Test
    fun givenSlowSyncIsCompletedAndWorkerFails_whenPolicyIsUpgraded_thenShouldResetExponentialDuration() =
        runTest(TestKaliumDispatcher.default) {
            val policyFlow = MutableStateFlow(ConnectionPolicy.DISCONNECT_AFTER_PENDING_EVENTS)

            val (arrangement, _) = Arrangement()
                .withWorkerReturning(emptyFlow())
                .withRecoveringFromFailure()
                .withConnectionPolicyReturning(policyFlow)
                .arrange()

            arrangement.slowSyncRepository.updateSlowSyncStatus(SlowSyncStatus.Complete)

            verify(arrangement.exponentialDurationHelper)
                .function(arrangement.exponentialDurationHelper::reset)
                .wasNotInvoked()

            policyFlow.emit(ConnectionPolicy.KEEP_ALIVE)
            advanceUntilIdle()

            verify(arrangement.exponentialDurationHelper)
                .function(arrangement.exponentialDurationHelper::reset)
                .wasInvoked(exactly = once)
        }

    @Test
    fun givenWorkerFailsAndDelayUntilRetry_whenPolicyIsUpgraded_thenShouldRetryImmediately() =
        runTest(TestKaliumDispatcher.default) {
            val policyFlow = Channel<ConnectionPolicy>(capacity = Channel.UNLIMITED)
            policyFlow.send(ConnectionPolicy.DISCONNECT_AFTER_PENDING_EVENTS)
            policyFlow.send(ConnectionPolicy.KEEP_ALIVE)

            val retryDelay = 10.seconds

            val (arrangement, _) = Arrangement()
                .withWorkerReturning(flowThatFailsOnFirstTime())
                .withNextExponentialDuration(retryDelay)
                .withRecoveringFromFailure()
                .withConnectionPolicyReturning(policyFlow.receiveAsFlow())
                .arrange()

            arrangement.slowSyncRepository.updateSlowSyncStatus(SlowSyncStatus.Complete)

            // Should ignore the rest of the timer and immediately retry
            advanceTimeBy(retryDelay.inWholeMilliseconds / 2)
            verify(arrangement.incrementalSyncWorker)
                .suspendFunction(arrangement.incrementalSyncWorker::processEventsWhilePolicyAllowsFlow)
                .wasInvoked(exactly = 2.times)
        }

    private class Arrangement {

        val database = TestUserDatabase(UserIDEntity("SELF_USER", "DOMAIN"))
        val slowSyncRepository: SlowSyncRepository = SlowSyncRepositoryImpl(database.builder.metadataDAO)

        @Mock
        val incrementalSyncWorker = mock(classOf<IncrementalSyncWorker>())

        @Mock
        val incrementalSyncRepository = configure(mock(classOf<IncrementalSyncRepository>())) { stubsUnitByDefault = true }

        @Mock
        val incrementalSyncRecoveryHandler = mock(classOf<IncrementalSyncRecoveryHandler>())

        @Mock
        val networkStateObserver: NetworkStateObserver = mock(classOf<NetworkStateObserver>())

        @Mock
        val exponentialDurationHelper: ExponentialDurationHelper =
            configure(mock(classOf<ExponentialDurationHelper>())) { stubsUnitByDefault = true }

        private val incrementalSyncManager by lazy {
            IncrementalSyncManager(
                slowSyncRepository = slowSyncRepository,
                incrementalSyncWorker = incrementalSyncWorker,
                incrementalSyncRepository = incrementalSyncRepository,
                incrementalSyncRecoveryHandler = incrementalSyncRecoveryHandler,
                networkStateObserver = networkStateObserver,
                kaliumDispatcher = TestKaliumDispatcher,
                exponentialDurationHelper = exponentialDurationHelper,
            )
        }

        init {
            withNetworkState(MutableStateFlow(NetworkState.ConnectedWithInternet))
            withNextExponentialDuration(1.seconds)
        }

        fun withWorkerReturning(sourceFlow: Flow<EventSource>) = apply {
            given(incrementalSyncWorker)
                .suspendFunction(incrementalSyncWorker::processEventsWhilePolicyAllowsFlow)
                .whenInvoked()
                .thenReturn(sourceFlow)
        }

        fun withConnectionPolicyReturning(connectionPolicyFlow: Flow<ConnectionPolicy>) = apply {
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

        fun withRecoveringFromFailure() = apply {
            given(incrementalSyncRecoveryHandler)
                .suspendFunction(incrementalSyncRecoveryHandler::recover)
                .whenInvokedWith(any(), any())
                .then { _, onRetryCallback -> onRetryCallback.retry() }
        }

        fun withNetworkState(networkStateFlow: StateFlow<NetworkState>) = apply {
            given(networkStateObserver)
                .function(networkStateObserver::observeNetworkState)
                .whenInvoked()
                .thenReturn(networkStateFlow)
        }

        fun withNextExponentialDuration(duration: Duration) = apply {
            given(exponentialDurationHelper)
                .function(exponentialDurationHelper::next)
                .whenInvoked()
                .thenReturn(duration)
        }

        fun arrange() = this to incrementalSyncManager

    }
}
