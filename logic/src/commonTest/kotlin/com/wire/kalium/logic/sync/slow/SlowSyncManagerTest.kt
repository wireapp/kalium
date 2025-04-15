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

package com.wire.kalium.logic.sync.slow

import app.cash.turbine.test
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.data.sync.SlowSyncStatus
import com.wire.kalium.logic.data.sync.SlowSyncStep
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import com.wire.kalium.logic.util.ExponentialDurationHelper
import com.wire.kalium.logic.util.arrangement.provider.SyncMigrationStepsProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.SyncMigrationStepsProviderArrangementImpl
import com.wire.kalium.logic.util.flowThatFailsOnFirstTime
import com.wire.kalium.network.NetworkState
import com.wire.kalium.network.NetworkStateObserver
import com.wire.kalium.util.DateTimeUtil
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.every
import io.mockative.instanceOf
import io.mockative.matches
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
        val (arrangement, slowSyncManager) = Arrangement().arrange {
            withSatisfiedCriteria()
            withSlowSyncWorkerReturning(stepFlow)
        }
        slowSyncManager.performSyncFlow().test {
            advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }

        assertTrue(isCollected)
        coVerify {
            arrangement.slowSyncWorker.slowSyncStepsFlow(any())
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.slowSyncRepository.setSlowSyncVersion(any())
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenCriteriaAreMet_whenWorkerThrowsNonCancellation_thenShouldRetry() = runTest(TestKaliumDispatcher.default) {
        val (arrangement, slowSyncManager) = Arrangement().arrange {
            withSatisfiedCriteria()
            withSlowSyncWorkerReturning(flowThatFailsOnFirstTime())
            withRecoveringFromFailure()
        }

        slowSyncManager.performSyncFlow().test {
            advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }

        coVerify {
            arrangement.slowSyncWorker.slowSyncStepsFlow(any())
        }.wasInvoked(exactly = twice)
    }

    @Test
    fun givenCriteriaAreMet_whenWorkerThrowsCancellation_thenShouldNotRetry() = runTest(TestKaliumDispatcher.default) {
        val (arrangement, slowSyncManager) = Arrangement().arrange {
            withSatisfiedCriteria()
            withSlowSyncWorkerReturning(flowThatFailsOnFirstTime(CancellationException("Cancelled")))
        }

        slowSyncManager.performSyncFlow().test {
            advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }

        coVerify {
            arrangement.slowSyncWorker.slowSyncStepsFlow(any())
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenCriteriaAreMet_whenStepsAreOver_thenShouldUpdateStateInRepository() = runTest(TestKaliumDispatcher.default) {
        val (arrangement, slowSyncManager) = Arrangement().arrange {
            withSatisfiedCriteria()
            withSlowSyncWorkerReturning(emptyFlow())
        }

        slowSyncManager.performSyncFlow().test {
            advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }

        verify {
            arrangement.slowSyncRepository.updateSlowSyncStatus(eq(SlowSyncStatus.Complete))
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenCriteriaAreMet_whenStepsAreOver_thenShouldUpdateLastCompletedDate() = runTest(TestKaliumDispatcher.default) {
        val initialTime = DateTimeUtil.currentInstant()

        val (arrangement, slowSyncManager) = Arrangement().arrange {
            withSatisfiedCriteria()
            withSlowSyncWorkerReturning(emptyFlow())
        }

        slowSyncManager.performSyncFlow().test {
            advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }

        coVerify {
            arrangement.slowSyncRepository.setSlowSyncVersion(any())
        }.wasInvoked(once)

        val completedTime = DateTimeUtil.currentInstant()
        coVerify {
            arrangement.slowSyncRepository.setLastSlowSyncCompletionInstant(
                matches<Instant> {
                    initialTime <= it && it <= completedTime
                }
            )
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenItWasCompletedRecently_whenCriteriaAreMet_thenShouldNotUpdateLastCompletedDate() = runTest(TestKaliumDispatcher.default) {
        val initialTime = DateTimeUtil.currentInstant()

        val (arrangement, slowSyncManager) = Arrangement().arrange {
            withSatisfiedCriteria()
            withLastSlowSyncPerformedAt(flowOf(initialTime))
        }

        advanceUntilIdle()

        coVerify {
            arrangement.slowSyncRepository.setLastSlowSyncCompletionInstant(any<Instant>())
        }.wasNotInvoked()
    }

    @Test
    fun givenItWasCompletedRecentlyAndVersionIsOutdated_whenCriteriaAreMet_thenShouldUpdateSlowSyncVersion() =
        runTest(TestKaliumDispatcher.default) {
            val (arrangement, slowSyncManager) = Arrangement().arrange {
                withSatisfiedCriteria()
                withLastSlowSyncPerformedAt(flowOf(DateTimeUtil.currentInstant()))
                withSlowSyncWorkerReturning(emptyFlow())
                withLastSlowSyncPerformedOnAnOldVersion()
                withSyncMigrationSteps(emptyList())
            }

            slowSyncManager.performSyncFlow().test {
                advanceUntilIdle()
                cancelAndIgnoreRemainingEvents()
            }

            coVerify {
                arrangement.slowSyncRepository.setSlowSyncVersion(any())
            }.wasInvoked(once)
        }

    @Test
    fun givenItWasCompletedRecentlyAndVersionIsUpToDate_whenCriteriaAreMet_thenShouldNotUpdateSlowSyncVersion() =
        runTest(TestKaliumDispatcher.default) {
            val (arrangement, slowSyncManager) = Arrangement().arrange {
                withSatisfiedCriteria()
                withLastSlowSyncPerformedAt(flowOf(DateTimeUtil.currentInstant()))
                withSlowSyncWorkerReturning(emptyFlow())
                withLastSlowSyncPerformedOnANewVersion()
            }

            advanceUntilIdle()

            coVerify {
                arrangement.slowSyncRepository.setSlowSyncVersion(any())
            }.wasNotInvoked()
        }

    @Test
    fun givenCriteriaAreMet_whenWorkerEmitsAStep_thenShouldUpdateStateInRepository() = runTest(TestKaliumDispatcher.default) {
        val stepChannel = Channel<SlowSyncStep>(Channel.UNLIMITED)
        val (arrangement, slowSyncManager) = Arrangement().arrange {
            withSatisfiedCriteria()
            withSlowSyncWorkerReturning(stepChannel.consumeAsFlow())
        }

        val step = SlowSyncStep.CONTACTS
        slowSyncManager.performSyncFlow().test {
            advanceUntilIdle()

            stepChannel.send(step)
            advanceUntilIdle()

            verify {
                arrangement.slowSyncRepository.updateSlowSyncStatus(eq(SlowSyncStatus.Ongoing(step)))
            }.wasInvoked(exactly = once)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenCriteriaAreMet_whenCriteriaAreBroken_thenShouldCancelCollection() = runTest(TestKaliumDispatcher.default) {
        val criteriaChannel = Channel<SyncCriteriaResolution>(Channel.UNLIMITED)
        val stepSharedFlow = MutableSharedFlow<SlowSyncStep>()
        val (_, slowSyncManager) = Arrangement().arrange {
            withCriteriaProviderReturning(criteriaChannel.consumeAsFlow())
            withSlowSyncWorkerReturning(stepSharedFlow)
        }

        slowSyncManager.performSyncFlow().test {
            criteriaChannel.send(SyncCriteriaResolution.Ready)
            advanceUntilIdle()

            // One collector
            assertEquals(1, stepSharedFlow.subscriptionCount.value)

            criteriaChannel.send(SyncCriteriaResolution.MissingRequirement("Missing requirement"))
            advanceUntilIdle()
            // No more collectors
            assertEquals(0, stepSharedFlow.subscriptionCount.value)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenItWasPerformedRecently_whenCriteriaAreMet_thenShouldNotStartSlowSync() = runTest(TestKaliumDispatcher.default) {
        val criteriaChannel = Channel<SyncCriteriaResolution>(Channel.UNLIMITED)
        val stepSharedFlow = MutableSharedFlow<SlowSyncStep>(extraBufferCapacity = 1)
        stepSharedFlow.emit(SlowSyncStep.CONVERSATIONS)
        val (arrangement, slowSyncManager) = Arrangement().arrange {
            withSatisfiedCriteria()
            withSlowSyncWorkerReturning(stepSharedFlow)
            withLastSlowSyncPerformedAt(flowOf(DateTimeUtil.currentInstant()))
        }

        criteriaChannel.send(SyncCriteriaResolution.Ready)
        advanceUntilIdle()

        // Never updated to Ongoing
        coVerify {
            arrangement.slowSyncRepository.updateSlowSyncStatus(instanceOf<SlowSyncStatus.Ongoing>())
        }.wasNotInvoked()

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

        val (arrangement, slowSyncManager) = Arrangement().arrange {
            withSatisfiedCriteria()
            withSlowSyncWorkerReturning(stepSharedFlow.receiveAsFlow())
            withLastSlowSyncPerformedAt(lastPerformedInstant.receiveAsFlow())
        }

        slowSyncManager.performSyncFlow().test {

            advanceUntilIdle()

            // Not updated to Ongoing
            coVerify {
                arrangement.slowSyncRepository.updateSlowSyncStatus(instanceOf<SlowSyncStatus.Ongoing>())
            }.wasNotInvoked()

            lastPerformedInstant.send(null)
            advanceUntilIdle()

            // Updated to Ongoing after clearing lastPerformed
            coVerify {
                arrangement.slowSyncRepository.updateSlowSyncStatus(eq(SlowSyncStatus.Ongoing(step)))
            }.wasInvoked(exactly = once)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenItWasPerformedLongAgoAndCriteriaAreMet_whenWorkerEmitsAStep_thenShouldUpdateStateInRepository() =
        runTest(TestKaliumDispatcher.default) {
            val stepChannel = Channel<SlowSyncStep>(Channel.UNLIMITED)
            val (arrangement, slowSyncManager) = Arrangement().arrange {
                withSatisfiedCriteria()
                withSlowSyncWorkerReturning(stepChannel.consumeAsFlow())
                withLastSlowSyncPerformedAt(flowOf(DateTimeUtil.currentInstant() - 32.days))
            }

            slowSyncManager.performSyncFlow().test {
                val step = SlowSyncStep.CONTACTS
                advanceUntilIdle()

                verify {
                    arrangement.slowSyncRepository.updateSlowSyncStatus(eq(SlowSyncStatus.Ongoing(step)))
                }.wasNotInvoked()

                stepChannel.send(step)
                advanceUntilIdle()

                verify {
                    arrangement.slowSyncRepository.updateSlowSyncStatus(eq(SlowSyncStatus.Ongoing(step)))
                }.wasInvoked(exactly = once)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun givenCriteriaAreNotMet_whenManagerIsCreated_thenShouldNotStartSlowSync() = runTest(TestKaliumDispatcher.default) {
        var isCollected = false
        val stepFlow = flow<SlowSyncStep> { isCollected = true }
        val (_, slowSyncManager) = Arrangement().arrange {
            withCriteriaProviderReturning(flowOf(SyncCriteriaResolution.MissingRequirement("Requirement Missing")))
            withSlowSyncWorkerReturning(stepFlow)
        }

        slowSyncManager.performSyncFlow().test {
            advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }

        assertFalse(isCollected)
    }

    @Test
    fun givenCriteriaAreMet_whenStepsAreOver_thenShouldResetExponentialDuration() = runTest(TestKaliumDispatcher.default) {
        val (arrangement, slowSyncManager) = Arrangement().arrange {
            withSatisfiedCriteria()
            withSlowSyncWorkerReturning(emptyFlow())
        }

        slowSyncManager.performSyncFlow().test {
            advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }

        verify {
            arrangement.exponentialDurationHelper.reset()
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenCriteriaAreMet_whenRecovers_thenShouldRetry() = runTest(TestKaliumDispatcher.default) {
        val (arrangement, slowSyncManager) = Arrangement().arrange {
            withSatisfiedCriteria()
            withSlowSyncWorkerReturning(flowThatFailsOnFirstTime())
            withRecoveringFromFailure()
            withNextExponentialDuration(1.seconds)
        }

        slowSyncManager.performSyncFlow().test {
            advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }

        verify {
            arrangement.exponentialDurationHelper.next()
        }.wasInvoked(exactly = once)
    }

    private class Arrangement : SyncMigrationStepsProviderArrangement by SyncMigrationStepsProviderArrangementImpl() {

        @Mock
        val slowSyncCriteriaProvider: SlowSyncCriteriaProvider = mock(SlowSyncCriteriaProvider::class)

        @Mock
        val slowSyncRepository: SlowSyncRepository = mock(SlowSyncRepository::class)

        @Mock
        val slowSyncWorker: SlowSyncWorker = mock(SlowSyncWorker::class)

        @Mock
        val slowSyncRecoveryHandler: SlowSyncRecoveryHandler = mock(SlowSyncRecoveryHandler::class)

        @Mock
        val networkStateObserver: NetworkStateObserver = mock(NetworkStateObserver::class)

        @Mock
        val exponentialDurationHelper: ExponentialDurationHelper =
            mock(ExponentialDurationHelper::class)


        init {
            every { slowSyncRepository.slowSyncStatus }.returns(MutableStateFlow(SlowSyncStatus.Pending))
        }

        suspend fun withCriteriaProviderReturning(criteriaFlow: Flow<SyncCriteriaResolution>) = apply {
            coEvery {
                slowSyncCriteriaProvider.syncCriteriaFlow()
            }.returns(criteriaFlow)
        }

        suspend fun withLastSlowSyncPerformedAt(lasSyncFlow: Flow<Instant?>) = apply {
            coEvery {
                slowSyncRepository.observeLastSlowSyncCompletionInstant()
            }.returns(lasSyncFlow)
        }

        suspend fun withSatisfiedCriteria() = withCriteriaProviderReturning(flowOf(SyncCriteriaResolution.Ready))

        suspend fun withSlowSyncWorkerReturning(stepFlow: Flow<SlowSyncStep>) = apply {
            coEvery {
                slowSyncWorker.slowSyncStepsFlow(any())
            }.returns(stepFlow)
        }

        suspend fun withRecoveringFromFailure() = apply {
            coEvery {
                slowSyncRecoveryHandler.recover(any(), any())
            }.invokes { args ->
                val onRetryCallback = args[1] as OnSlowSyncRetryCallback
                onRetryCallback.retry()
            }
        }

        fun withNetworkState(networkStateFlow: StateFlow<NetworkState>) = apply {
            every {
                networkStateObserver.observeNetworkState()
            }.returns(networkStateFlow)
        }

        fun withNextExponentialDuration(duration: Duration) = apply {
            every {
                exponentialDurationHelper.next()
            }.returns(duration)
        }

        suspend fun withLastSlowSyncPerformedOnAnOldVersion() = apply {
            coEvery {
                slowSyncRepository.getSlowSyncVersion()
            }.returns(0)
        }

        suspend fun withLastSlowSyncPerformedOnANewVersion() = apply {
            coEvery {
                slowSyncRepository.getSlowSyncVersion()
            }.returns(Int.MAX_VALUE)
        }

        private val slowSyncManager = SlowSyncManager(
            slowSyncCriteriaProvider = slowSyncCriteriaProvider,
            slowSyncRepository = slowSyncRepository,
            slowSyncWorker = slowSyncWorker,
            slowSyncRecoveryHandler = slowSyncRecoveryHandler,
            networkStateObserver = networkStateObserver,
            kaliumDispatcher = TestKaliumDispatcher,
            exponentialDurationHelper = exponentialDurationHelper,
            syncMigrationStepsProvider = { syncMigrationStepsProvider },
            userScopedLogger = kaliumLogger
        )

        suspend fun arrange(block: suspend Arrangement.() -> Unit = { }) = run {
            withLastSlowSyncPerformedAt(flowOf(null))
            withNetworkState(MutableStateFlow(NetworkState.ConnectedWithInternet))
            withNextExponentialDuration(1.seconds)
            withLastSlowSyncPerformedOnANewVersion()
            apply {
                coEvery {
                    slowSyncRepository.setSlowSyncVersion(any())
                }.returns(Unit)
            }
            block()
            this to slowSyncManager
        }
    }
}
