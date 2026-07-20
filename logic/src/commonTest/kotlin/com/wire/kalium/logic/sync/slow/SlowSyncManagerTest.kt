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
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.data.sync.SlowSyncStatus
import com.wire.kalium.logic.data.sync.SlowSyncStep
import com.wire.kalium.logic.fakes.network.FakeNetworkStateObserver
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import com.wire.kalium.logic.util.ExponentialDurationHelper
import com.wire.kalium.logic.util.flowThatFailsOnFirstTime
import com.wire.kalium.network.NetworkState
import com.wire.kalium.util.DateTimeUtil
import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.matcher.matches
import dev.mokkery.matcher.ofType
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verify
import dev.mokkery.verifySuspend
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
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
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import okio.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import com.wire.kalium.logic.sync.slow.migration.SyncMigrationStepsProvider

@OptIn(ExperimentalCoroutinesApi::class)
class SlowSyncManagerTest {

    @Test
    fun givenCriteriaAreMet_whenManagerIsCreated_thenShouldStartSlowSync() = runTest {
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
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.slowSyncWorker.slowSyncStepsFlow(any())
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.slowSyncRepository.setSlowSyncVersion(any())
        }
    }

    @Test
    fun givenCriteriaAreMet_whenWorkerThrowsNonCancellation_thenShouldRetry() = runTest {
        val (arrangement, slowSyncManager) = Arrangement().arrange {
            withSatisfiedCriteria()
            withSlowSyncWorkerReturning(flowThatFailsOnFirstTime())
            withRecoveringFromFailure()
        }

        slowSyncManager.performSyncFlow().test {
            advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }

        verifySuspend(VerifyMode.exactly(2)) {
            arrangement.slowSyncWorker.slowSyncStepsFlow(any())
        }
    }

    @Test
    fun givenCriteriaAreMet_whenWorkerThrowsCancellation_thenShouldNotRetry() = runTest {
        val (arrangement, slowSyncManager) = Arrangement().arrange {
            withSatisfiedCriteria()
            withSlowSyncWorkerReturning(flowThatFailsOnFirstTime(CancellationException("Cancelled")))
        }

        slowSyncManager.performSyncFlow().test {
            advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.slowSyncWorker.slowSyncStepsFlow(any())
        }
    }

    @Test
    fun givenCriteriaAreMet_whenStepsAreOver_thenShouldUpdateStateInRepository() = runTest {
        val (arrangement, slowSyncManager) = Arrangement().arrange {
            withSatisfiedCriteria()
            withSlowSyncWorkerReturning(emptyFlow())
        }

        slowSyncManager.performSyncFlow().test {
            advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }

        verify(VerifyMode.exactly(1)) {
            arrangement.slowSyncRepository.updateSlowSyncStatus(eq(SlowSyncStatus.Complete))
        }
    }

    @Test
    fun givenCriteriaAreMet_whenStepsAreOver_thenShouldUpdateLastCompletedDate() = runTest {
        val initialTime = DateTimeUtil.currentInstant()

        val (arrangement, slowSyncManager) = Arrangement().arrange {
            withSatisfiedCriteria()
            withSlowSyncWorkerReturning(emptyFlow())
        }

        slowSyncManager.performSyncFlow().test {
            advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.slowSyncRepository.setSlowSyncVersion(any())
        }

        val completedTime = DateTimeUtil.currentInstant()
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.slowSyncRepository.setLastSlowSyncCompletionInstant(
                matches<Instant> {
                    initialTime <= it && it <= completedTime
                }
            )
        }
    }

    @Test
    fun givenItWasCompletedRecently_whenCriteriaAreMet_thenShouldNotUpdateLastCompletedDate() = runTest {
        val initialTime = DateTimeUtil.currentInstant()

        val (arrangement, slowSyncManager) = Arrangement().arrange {
            withSatisfiedCriteria()
            withLastSlowSyncPerformedAt(flowOf(initialTime))
        }

        advanceUntilIdle()

        verifySuspend(VerifyMode.not) {
            arrangement.slowSyncRepository.setLastSlowSyncCompletionInstant(any<Instant>())
        }
    }

    @Test
    fun givenItWasCompletedRecentlyAndVersionIsOutdated_whenCriteriaAreMet_thenShouldUpdateSlowSyncVersion() =
        runTest {
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

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.slowSyncRepository.setSlowSyncVersion(any())
            }
        }

    @Test
    fun givenItWasCompletedRecentlyAndVersionIsUpToDate_whenCriteriaAreMet_thenShouldNotUpdateSlowSyncVersion() =
        runTest {
            val (arrangement, slowSyncManager) = Arrangement().arrange {
                withSatisfiedCriteria()
                withLastSlowSyncPerformedAt(flowOf(DateTimeUtil.currentInstant()))
                withSlowSyncWorkerReturning(emptyFlow())
                withLastSlowSyncPerformedOnANewVersion()
            }

            advanceUntilIdle()

            verifySuspend(VerifyMode.not) {
                arrangement.slowSyncRepository.setSlowSyncVersion(any())
            }
        }

    @Test
    fun givenCriteriaAreMet_whenWorkerEmitsAStep_thenShouldUpdateStateInRepository() = runTest {
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

            verify(VerifyMode.exactly(1)) {
                arrangement.slowSyncRepository.updateSlowSyncStatus(eq(SlowSyncStatus.Ongoing(step)))
            }

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenCriteriaAreMet_whenCriteriaAreBroken_thenShouldCancelCollection() = runTest {
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
    fun givenItWasPerformedRecently_whenCriteriaAreMet_thenShouldNotStartSlowSync() = runTest {
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
        verify(VerifyMode.not) {
            arrangement.slowSyncRepository.updateSlowSyncStatus(ofType<SlowSyncStatus.Ongoing>())
        }

        // No collectors
        assertEquals(0, stepSharedFlow.subscriptionCount.value)
    }

    @Test
    fun givenItWasPerformedRecently_whenLastPerformedIsCleared_thenShouldStartSlowSyncAgain() = runTest {
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
            verify(VerifyMode.not) {
                arrangement.slowSyncRepository.updateSlowSyncStatus(ofType<SlowSyncStatus.Ongoing>())
            }

            lastPerformedInstant.send(null)
            advanceUntilIdle()

            // Updated to Ongoing after clearing lastPerformed
            verify(VerifyMode.exactly(1)) {
                arrangement.slowSyncRepository.updateSlowSyncStatus(eq(SlowSyncStatus.Ongoing(step)))
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenCriteriaAreNotMet_whenManagerIsCreated_thenShouldNotStartSlowSync() = runTest {
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
    fun givenCriteriaAreMet_whenStepsAreOver_thenShouldResetExponentialDuration() = runTest {
        val (arrangement, slowSyncManager) = Arrangement().arrange {
            withSatisfiedCriteria()
            withSlowSyncWorkerReturning(emptyFlow())
        }

        slowSyncManager.performSyncFlow().test {
            advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }

        verify(VerifyMode.exactly(2)) {
            arrangement.exponentialDurationHelper.reset()
        }
    }

    @Test
    fun givenCriteriaAreMet_whenRecovers_thenShouldRetry() = runTest {
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

        verify(VerifyMode.exactly(1)) {
            arrangement.exponentialDurationHelper.next()
        }
    }

    @Test
    fun givenSlowSyncIsBackingOff_whenNetworkReconnects_thenShouldResetDelayAndRetryImmediately() = runTest {
        val reconnectResult = CompletableDeferred<Boolean>()
        val exponentialDurationHelper = RecordingExponentialDurationHelper(10.minutes)
        var attempts = 0
        val workerFlow = flow<SlowSyncStep> {
            attempts++
            throw IOException("Network unavailable")
        }
        val (arrangement, slowSyncManager) = Arrangement(
            exponentialDurationHelper = exponentialDurationHelper,
            configureDefaultExponentialDuration = false,
        ).arrange {
            withSatisfiedCriteria()
            withSlowSyncWorkerReturning(workerFlow)
            withRecoveringFromFailure()
            withReconnectWaitOverride {
                if (attempts == 1) reconnectResult.await() else awaitCancellation()
            }
        }

        val syncJob = slowSyncManager.performSyncFlow().launchIn(backgroundScope)
        runCurrent()
        assertEquals(1, attempts)
        assertEquals(1, arrangement.networkStateObserver.reconnectWaitCallCount)
        assertEquals(1, exponentialDurationHelper.resetCount)

        reconnectResult.complete(true)
        runCurrent()

        assertEquals(2, attempts)
        assertEquals(2, arrangement.networkStateObserver.reconnectWaitCallCount)
        assertEquals(2, exponentialDurationHelper.resetCount)
        syncJob.cancel()
    }

    private class Arrangement(
        val exponentialDurationHelper: ExponentialDurationHelper = mock(mode = MockMode.autoUnit),
        private val configureDefaultExponentialDuration: Boolean = true,
    ) {
        val slowSyncCriteriaProvider: SlowSyncCriteriaProvider = mock()
        val slowSyncRepository: SlowSyncRepository = mock(mode = MockMode.autoUnit)
        val slowSyncWorker: SlowSyncWorker = mock()
        val slowSyncRecoveryHandler: SlowSyncRecoveryHandler = mock()
        val networkStateObserver = FakeNetworkStateObserver()
        val syncMigrationStepsProvider: SyncMigrationStepsProvider = mock()


        init {
            every { slowSyncRepository.slowSyncStatus } returns MutableStateFlow(SlowSyncStatus.Pending)
        }

        suspend fun withCriteriaProviderReturning(criteriaFlow: Flow<SyncCriteriaResolution>) = apply {
            everySuspend {
                slowSyncCriteriaProvider.syncCriteriaFlow()
            } returns criteriaFlow
        }

        suspend fun withLastSlowSyncPerformedAt(lasSyncFlow: Flow<Instant?>) = apply {
            everySuspend {
                slowSyncRepository.observeLastSlowSyncCompletionInstant()
            } returns lasSyncFlow
        }

        suspend fun withSatisfiedCriteria() = withCriteriaProviderReturning(flowOf(SyncCriteriaResolution.Ready))

        suspend fun withSlowSyncWorkerReturning(stepFlow: Flow<SlowSyncStep>) = apply {
            everySuspend {
                slowSyncWorker.slowSyncStepsFlow(any())
            } returns stepFlow
        }

        suspend fun withRecoveringFromFailure() = apply {
            everySuspend {
                slowSyncRecoveryHandler.recover(any(), any())
            } calls { invocation ->
                val onRetryCallback = invocation.args[1] as OnSlowSyncRetryCallback
                onRetryCallback.retry()
            }
        }

        fun withNetworkState(networkStateFlow: StateFlow<NetworkState>) = apply {
            networkStateObserver.networkStateFlow = networkStateFlow
        }

        fun withReconnectWaitOverride(override: suspend (Duration) -> Boolean) = apply {
            networkStateObserver.reconnectWaitOverride = override
        }

        fun withNextExponentialDuration(duration: Duration) = apply {
            every {
                exponentialDurationHelper.next()
            } returns duration
        }

        suspend fun withLastSlowSyncPerformedOnAnOldVersion() = apply {
            everySuspend {
                slowSyncRepository.getSlowSyncVersion()
            } returns 0
        }

        suspend fun withLastSlowSyncPerformedOnANewVersion() = apply {
            everySuspend {
                slowSyncRepository.getSlowSyncVersion()
            } returns Int.MAX_VALUE
        }

        fun withSyncMigrationSteps(steps: List<com.wire.kalium.logic.sync.slow.migration.steps.SyncMigrationStep>) = apply {
            every {
                syncMigrationStepsProvider.getMigrationSteps(any(), any())
            } returns steps
        }

        private val slowSyncManager = SlowSyncManager(
            slowSyncCriteriaProvider = slowSyncCriteriaProvider,
            slowSyncRepository = slowSyncRepository,
            slowSyncWorker = slowSyncWorker,
            slowSyncRecoveryHandler = slowSyncRecoveryHandler,
            networkStateObserver = networkStateObserver,
            exponentialDurationHelper = exponentialDurationHelper,
            syncMigrationStepsProvider = { syncMigrationStepsProvider },
            userScopedLogger = kaliumLogger
        )

        suspend fun arrange(block: suspend Arrangement.() -> Unit = { }) = run {
            withLastSlowSyncPerformedAt(flowOf(null))
            withNetworkState(MutableStateFlow(NetworkState.ConnectedWithInternet))
            if (configureDefaultExponentialDuration) {
                withNextExponentialDuration(1.seconds)
            }
            withLastSlowSyncPerformedOnANewVersion()
            apply {
                everySuspend {
                    slowSyncRepository.setSlowSyncVersion(any())
                } returns Unit
            }
            block()
            this to slowSyncManager
        }
    }

    private class RecordingExponentialDurationHelper(
        private val duration: Duration,
    ) : ExponentialDurationHelper {

        var resetCount: Int = 0
            private set

        override fun reset() {
            resetCount++
        }

        override fun next(): Duration = duration
    }
}
