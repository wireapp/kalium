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

package com.wire.kalium.logic.sync.slow

import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.data.sync.SlowSyncStatus
import com.wire.kalium.logic.data.sync.SlowSyncStep
import com.wire.kalium.logic.sync.slow.migration.SyncMigrationStepsProvider
import com.wire.kalium.network.NetworkState
import com.wire.kalium.network.NetworkStateObserver
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import com.wire.kalium.logic.util.ExponentialDurationHelper
import com.wire.kalium.logic.util.arrangement.provider.SyncMigrationStepsProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.SyncMigrationStepsProviderArrangementImpl
import com.wire.kalium.logic.util.flowThatFailsOnFirstTime
import com.wire.kalium.util.DateTimeUtil
import io.mockative.Mock
import io.mockative.any
import io.mockative.anyInstanceOf
import io.mockative.classOf
import io.mockative.configure
import io.mockative.eq
import io.mockative.given
import io.mockative.matching
import io.mockative.mock
import io.mockative.once
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
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
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
            .suspendFunction(arrangement.slowSyncWorker::slowSyncStepsFlow)
            .with(any())
            .wasInvoked(exactly = once)

        verify(arrangement.slowSyncRepository)
            .suspendFunction(arrangement.slowSyncRepository::setSlowSyncVersion)
            .with(any())
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenCriteriaAreMet_whenWorkerThrowsNonCancellation_thenShouldRetry() = runTest(TestKaliumDispatcher.default) {
        val (arrangement, _) = Arrangement()
            .withSatisfiedCriteria()
            .withSlowSyncWorkerReturning(flowThatFailsOnFirstTime())
            .withRecoveringFromFailure()
            .arrange()

        advanceUntilIdle()

        verify(arrangement.slowSyncWorker)
            .suspendFunction(arrangement.slowSyncWorker::slowSyncStepsFlow)
            .with(any())
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
            .suspendFunction(arrangement.slowSyncWorker::slowSyncStepsFlow)
            .with(any())
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
    fun givenCriteriaAreMet_whenStepsAreOver_thenShouldUpdateLastCompletedDate() = runTest(TestKaliumDispatcher.default) {
        val initialTime = DateTimeUtil.currentInstant()

        val (arrangement, _) = Arrangement()
            .withSatisfiedCriteria()
            .withSlowSyncWorkerReturning(emptyFlow())
            .arrange()

        advanceUntilIdle()

        verify(arrangement.slowSyncRepository)
            .suspendFunction(arrangement.slowSyncRepository::setSlowSyncVersion)
            .with(any())
            .wasInvoked(once)

        val completedTime = DateTimeUtil.currentInstant()
        verify(arrangement.slowSyncRepository)
            .function(arrangement.slowSyncRepository::setLastSlowSyncCompletionInstant)
            .with(
                matching<Instant?> {
                    it != null && initialTime <= it && it <= completedTime
                }
            )
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenItWasCompletedRecently_whenCriteriaAreMet_thenShouldNotUpdateLastCompletedDate() = runTest(TestKaliumDispatcher.default) {
        val initialTime = DateTimeUtil.currentInstant()

        val (arrangement, _) = Arrangement()
            .withSatisfiedCriteria()
            .withLastSlowSyncPerformedAt(flowOf(initialTime))
            .arrange()

        advanceUntilIdle()

        verify(arrangement.slowSyncRepository)
            .function(arrangement.slowSyncRepository::setLastSlowSyncCompletionInstant)
            .with(any<Instant?>())
            .wasNotInvoked()
    }

    @Test
    fun givenItWasCompletedRecentlyAndVersionIsOutdated_whenCriteriaAreMet_thenShouldUpdateSlowSyncVersion() =
        runTest(TestKaliumDispatcher.default) {
            val (arrangement, _) = Arrangement()
                .arrange{
                    withSatisfiedCriteria()
                    withLastSlowSyncPerformedAt(flowOf(DateTimeUtil.currentInstant()))
                    withSlowSyncWorkerReturning(emptyFlow())
                    withLastSlowSyncPerformedOnAnOldVersion()
                    withSyncMigrationSteps(emptyList())
                }

            advanceUntilIdle()

            verify(arrangement.slowSyncRepository)
                .suspendFunction(arrangement.slowSyncRepository::setSlowSyncVersion)
                .with(any())
                .wasInvoked(once)
        }

    @Test
    fun givenItWasCompletedRecentlyAndVersionIsUpToDate_whenCriteriaAreMet_thenShouldNotUpdateSlowSyncVersion() =
        runTest(TestKaliumDispatcher.default) {
            val (arrangement, _) = Arrangement()
                .withSatisfiedCriteria()
                .withLastSlowSyncPerformedAt(flowOf(DateTimeUtil.currentInstant()))
                .withSlowSyncWorkerReturning(emptyFlow())
                .withLastSlowSyncPerformedOnANewVersion()
                .arrange()

            advanceUntilIdle()

            verify(arrangement.slowSyncRepository)
                .suspendFunction(arrangement.slowSyncRepository::setSlowSyncVersion)
                .with(any())
                .wasNotInvoked()
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
    fun givenItWasPerformedRecently_whenCriteriaAreMet_thenShouldNotStartSlowSync() = runTest(TestKaliumDispatcher.default) {
        val criteriaChannel = Channel<SyncCriteriaResolution>(Channel.UNLIMITED)
        val stepSharedFlow = MutableSharedFlow<SlowSyncStep>(extraBufferCapacity = 1)
        stepSharedFlow.emit(SlowSyncStep.CONVERSATIONS)
        val (arrangement, _) = Arrangement()
            .withSatisfiedCriteria()
            .withSlowSyncWorkerReturning(stepSharedFlow)
            .withLastSlowSyncPerformedAt(flowOf(DateTimeUtil.currentInstant()))
            .arrange()

        criteriaChannel.send(SyncCriteriaResolution.Ready)
        advanceUntilIdle()

        // Never updated to Ongoing
        verify(arrangement.slowSyncRepository)
            .suspendFunction(arrangement.slowSyncRepository::updateSlowSyncStatus)
            .with(anyInstanceOf(SlowSyncStatus.Ongoing::class))
            .wasNotInvoked()

        // No collectors
        assertEquals(0, stepSharedFlow.subscriptionCount.value)
    }

    @Test
    fun givenItWasPerformedRecently_whenLastPerformedIsCleared_thenShouldStartSlowSyncAgain() = runTest(TestKaliumDispatcher.default) {
        val criteriaChannel = Channel<SyncCriteriaResolution>(Channel.UNLIMITED)
        criteriaChannel.send(SyncCriteriaResolution.Ready)

        val lastPerformedInstant = Channel<Instant?>(Channel.UNLIMITED)
        lastPerformedInstant.send(DateTimeUtil.currentInstant())

        val stepSharedFlow = Channel<SlowSyncStep>(Channel.UNLIMITED)
        val step = SlowSyncStep.CONVERSATIONS
        stepSharedFlow.send(step)

        val (arrangement, _) = Arrangement()
            .withSatisfiedCriteria()
            .withSlowSyncWorkerReturning(stepSharedFlow.receiveAsFlow())
            .withLastSlowSyncPerformedAt(lastPerformedInstant.receiveAsFlow())
            .arrange()

        advanceUntilIdle()

        // Not updated to Ongoing
        verify(arrangement.slowSyncRepository)
            .suspendFunction(arrangement.slowSyncRepository::updateSlowSyncStatus)
            .with(anyInstanceOf(SlowSyncStatus.Ongoing::class))
            .wasNotInvoked()

        lastPerformedInstant.send(null)
        advanceUntilIdle()

        // Updated to Ongoing after clearing lastPerformed
        verify(arrangement.slowSyncRepository)
            .suspendFunction(arrangement.slowSyncRepository::updateSlowSyncStatus)
            .with(eq(SlowSyncStatus.Ongoing(step)))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenItWasPerformedLongAgoAndCriteriaAreMet_whenWorkerEmitsAStep_thenShouldUpdateStateInRepository() =
        runTest(TestKaliumDispatcher.default) {
            val stepChannel = Channel<SlowSyncStep>(Channel.UNLIMITED)
            val (arrangement, _) = Arrangement()
                .withSatisfiedCriteria()
                .withSlowSyncWorkerReturning(stepChannel.consumeAsFlow())
                .withLastSlowSyncPerformedAt(flowOf(DateTimeUtil.currentInstant() - 30.days))
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

    @Test
    fun givenCriteriaAreMet_whenStepsAreOver_thenShouldResetExponentialDuration() = runTest(TestKaliumDispatcher.default) {
        val (arrangement, _) = Arrangement()
            .withSatisfiedCriteria()
            .withSlowSyncWorkerReturning(emptyFlow())
            .arrange()

        advanceUntilIdle()

        verify(arrangement.exponentialDurationHelper)
            .function(arrangement.exponentialDurationHelper::reset)
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenCriteriaAreMet_whenRecovers_thenShouldRetry() = runTest(TestKaliumDispatcher.default) {
        val (arrangement, _) = Arrangement()
            .withSatisfiedCriteria()
            .withSlowSyncWorkerReturning(flowThatFailsOnFirstTime())
            .withRecoveringFromFailure()
            .withNextExponentialDuration(1.seconds)
            .arrange()

        advanceUntilIdle()

        verify(arrangement.exponentialDurationHelper)
            .function(arrangement.exponentialDurationHelper::next)
            .wasInvoked(exactly = once)
    }

    private class Arrangement: SyncMigrationStepsProviderArrangement by SyncMigrationStepsProviderArrangementImpl() {

        @Mock
        val slowSyncCriteriaProvider: SlowSyncCriteriaProvider = mock(classOf<SlowSyncCriteriaProvider>())

        @Mock
        val slowSyncRepository: SlowSyncRepository = configure(mock(classOf<SlowSyncRepository>())) { stubsUnitByDefault = true }

        @Mock
        val slowSyncWorker: SlowSyncWorker = mock(classOf<SlowSyncWorker>())

        @Mock
        val slowSyncRecoveryHandler: SlowSyncRecoveryHandler = mock(classOf<SlowSyncRecoveryHandler>())

        @Mock
        val networkStateObserver: NetworkStateObserver = mock(classOf<NetworkStateObserver>())

        @Mock
        val exponentialDurationHelper: ExponentialDurationHelper =
            configure(mock(classOf<ExponentialDurationHelper>())) { stubsUnitByDefault = true }

        init {
            withLastSlowSyncPerformedAt(flowOf(null))
            withNetworkState(MutableStateFlow(NetworkState.ConnectedWithInternet))
            withNextExponentialDuration(1.seconds)
            withLastSlowSyncPerformedOnANewVersion()
            apply {
                given(slowSyncRepository)
                    .suspendFunction(slowSyncRepository::setSlowSyncVersion)
                    .whenInvokedWith(any())
                    .thenReturn(Unit)
            }
        }

        fun withCriteriaProviderReturning(criteriaFlow: Flow<SyncCriteriaResolution>) = apply {
            given(slowSyncCriteriaProvider)
                .suspendFunction(slowSyncCriteriaProvider::syncCriteriaFlow)
                .whenInvoked()
                .thenReturn(criteriaFlow)
        }

        fun withLastSlowSyncPerformedAt(lasSyncFlow: Flow<Instant?>) = apply {
            given(slowSyncRepository)
                .suspendFunction(slowSyncRepository::observeLastSlowSyncCompletionInstant)
                .whenInvoked()
                .thenReturn(lasSyncFlow)
        }

        fun withSatisfiedCriteria() = withCriteriaProviderReturning(flowOf(SyncCriteriaResolution.Ready))

        fun withSlowSyncWorkerReturning(stepFlow: Flow<SlowSyncStep>) = apply {
            given(slowSyncWorker)
                .suspendFunction(slowSyncWorker::slowSyncStepsFlow)
                .whenInvokedWith(any())
                .thenReturn(stepFlow)
        }

        fun withRecoveringFromFailure() = apply {
            given(slowSyncRecoveryHandler)
                .suspendFunction(slowSyncRecoveryHandler::recover)
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

        fun withLastSlowSyncPerformedOnAnOldVersion() = apply {
            given(slowSyncRepository)
                .suspendFunction(slowSyncRepository::getSlowSyncVersion)
                .whenInvoked()
                .thenReturn(0)
        }

        fun withLastSlowSyncPerformedOnANewVersion() = apply {
            given(slowSyncRepository)
                .suspendFunction(slowSyncRepository::getSlowSyncVersion)
                .whenInvoked()
                .thenReturn(Int.MAX_VALUE)
        }

        private val slowSyncManager = SlowSyncManager(
            slowSyncCriteriaProvider = slowSyncCriteriaProvider,
            slowSyncRepository = slowSyncRepository,
            slowSyncWorker = slowSyncWorker,
            slowSyncRecoveryHandler = slowSyncRecoveryHandler,
            networkStateObserver = networkStateObserver,
            kaliumDispatcher = TestKaliumDispatcher,
            exponentialDurationHelper = exponentialDurationHelper,
            syncMigrationStepsProvider = { syncMigrationStepsProvider }
        )

        fun arrange(block: Arrangement.() -> Unit = { }) = apply(block).let {
            this to slowSyncManager
        }

    }
}
