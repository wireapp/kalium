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
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.data.sync.InMemoryIncrementalSyncRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncStatus
import com.wire.kalium.logic.fakes.network.FakeNetworkStateObserver
import com.wire.kalium.logic.sync.UserSessionWorkScheduler
import com.wire.kalium.logic.util.ExponentialDurationHelper
import com.wire.kalium.logic.util.flowThatFailsOnFirstTime
import com.wire.kalium.network.NetworkState
import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verify
import dev.mokkery.verifySuspend
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okio.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class IncrementalSyncManagerTest {

    @Test
    fun givenDefaultState_whenStartingIncrementalManager_thenShouldStartWorker() = runTest {
        val sharedFlow = MutableSharedFlow<EventSource>()

        val (arrangement, incrementalSyncManager) = Arrangement()
            .withWorkerReturning(sharedFlow)
            .arrange()

        incrementalSyncManager.performSyncFlow().test {
            val firstStatus = awaitItem()
            assertEquals(IncrementalSyncStatus.Pending, firstStatus)
            assertEquals(1, sharedFlow.subscriptionCount.value)
            cancelAndIgnoreRemainingEvents()
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.incrementalSyncWorker.processEventsFlow()
        }
    }

    @Test
    fun givenDefaultState_whenStartingIncrementalSync_thenShouldResetRetryTimer() = runTest {
        val sharedFlow = MutableSharedFlow<EventSource>()

        val (arrangement, incrementalSyncManager) = Arrangement()
            .withWorkerReturning(sharedFlow)
            .arrange()

        incrementalSyncManager.performSyncFlow().test {
            advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }

        verify(VerifyMode.exactly(1)) {
            arrangement.exponentialDurationHelper.reset()
        }
    }

    @Test
    fun givenNormalOperation_whenWorkerEmitsSources_thenShouldUpdateRepositoryWithState() = runTest {
        val sourceFlow = Channel<EventSource>(Channel.UNLIMITED)

        val (arrangement, incrementalSyncManager) = Arrangement()
            .withWorkerReturning(sourceFlow.consumeAsFlow())
            .arrange()

        incrementalSyncManager.performSyncFlow().test {
            sourceFlow.send(EventSource.PENDING)

            advanceUntilIdle()
            assertEquals(IncrementalSyncStatus.FetchingPendingEvents, arrangement.incrementalSyncRepository.incrementalSyncState.first())

            sourceFlow.send(EventSource.LIVE)
            advanceUntilIdle()
            assertEquals(IncrementalSyncStatus.Live, arrangement.incrementalSyncRepository.incrementalSyncState.first())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenWorkerThrows_whenPerformingSync_thenShouldUpdateRepositoryWithFailedState() = runTest {
        val (arrangement, incrementalSyncManager) = Arrangement()
            .withWorkerReturning(flowThatFailsOnFirstTime())
            .arrange()

        incrementalSyncManager.performSyncFlow().test {
            advanceUntilIdle()
            assertIs<IncrementalSyncStatus.Failed>(arrangement.incrementalSyncRepository.incrementalSyncState.first())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenWorkerThrowsNonCancellation_whenPerformingSync_thenShouldRetry() = runTest {
        val (arrangement, incrementalSyncManager) = Arrangement()
            .withWorkerReturning(flowThatFailsOnFirstTime())
            .withRecoveringFromFailure()
            .arrange()

        incrementalSyncManager.performSyncFlow().test {
            advanceUntilIdle()
            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.incrementalSyncRecoveryHandler.recover(any(), any())
            }

            verifySuspend(VerifyMode.exactly(2)) {
                arrangement.incrementalSyncWorker.processEventsFlow()
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenDnsIsNotReadyImmediately_whenNetworkReconnects_thenShouldRetryWithMinimumDelay() = runTest {
        val networkState = MutableStateFlow<NetworkState>(NetworkState.NotConnected)
        var attempts = 0
        val workerFlow = flow<EventSource> {
            attempts++
            if (attempts <= 2) throw IOException("DNS is not ready")
        }
        val (arrangement, incrementalSyncManager) = Arrangement()
            .withWorkerReturning(workerFlow)
            .withRecoveringFromFailure()
            .withNetworkState(networkState)
            .withExponentialDurationHelper(ExponentialDurationHelper(1.seconds, 10.minutes))
            .arrange()

        incrementalSyncManager.performSyncFlow().test {
            awaitItem()
            runCurrent()
            assertEquals(
                1.seconds,
                (arrangement.incrementalSyncRepository.incrementalSyncState.first() as IncrementalSyncStatus.Failed).retryDelay
            )

            networkState.value = NetworkState.ConnectedWithInternet
            runCurrent()

            assertEquals(2, attempts)
            assertEquals(
                1.seconds,
                (arrangement.incrementalSyncRepository.incrementalSyncState.first() as IncrementalSyncStatus.Failed).retryDelay
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenRetryIsWaitingForExponentialDelay_whenRestartingSync_thenShouldPerformOnlyTwice() = runTest {
        val recoverJob = Job()
        val (arrangement, incrementalSyncManager) = Arrangement()
            .withWorkerReturning(flowThatFailsOnFirstTime())
            .withRecoveringFromFailure { recoverJob.join(); it.retry() }
            .arrange()

        incrementalSyncManager.performSyncFlow().test {
            advanceUntilIdle()
            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.incrementalSyncWorker.processEventsFlow()
            }
            cancelAndIgnoreRemainingEvents()
        }

        incrementalSyncManager.performSyncFlow().test {
            // Now recover from the exponential backoff
            recoverJob.complete()
            advanceUntilIdle()

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.incrementalSyncWorker.processEventsFlow()
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenWorkerThrowsCancellation_whenPerformingSync_thenShouldNotRetry() = runTest {
        val workerFlow = Channel<EventSource>(Channel.UNLIMITED)
        val (arrangement, incrementalSyncManager) = Arrangement()
            .withWorkerReturning(workerFlow.consumeAsFlow())
            .withRecoveringFromFailure()
            .arrange()

        incrementalSyncManager.performSyncFlow().test {
            advanceUntilIdle()
            verifySuspend(VerifyMode.not) {
                arrangement.incrementalSyncRecoveryHandler.recover(any(), any())
            }

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.incrementalSyncWorker.processEventsFlow()
            }

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenDefaultState_whenCancellingSync_thenShouldUpdateIncrementalSyncStatusToPendingAgain() =
        runTest {
            val workerChannel = Channel<EventSource>(capacity = Channel.UNLIMITED)
            val (arrangement, incrementalSyncManager) = Arrangement()
                .withWorkerReturning(workerChannel.consumeAsFlow())
                .arrange()

            incrementalSyncManager.performSyncFlow().test { // Start
                workerChannel.send(EventSource.PENDING)
                advanceUntilIdle()
                cancelAndIgnoreRemainingEvents()
            } // Stop

            assertEquals(IncrementalSyncStatus.Pending, arrangement.incrementalSyncRepository.incrementalSyncState.first())
        }

    @Test
    fun givenLiveState_whenCancellingSync_thenShouldUpdateIncrementalSyncStatusToPendingAgain() =
        runTest {
            val workerChannel = Channel<EventSource>(capacity = Channel.UNLIMITED)
            val (arrangement, incrementalSyncManager) = Arrangement()
                .withWorkerReturning(workerChannel.consumeAsFlow())
                .arrange()

            incrementalSyncManager.performSyncFlow().test { // Start
                workerChannel.send(EventSource.LIVE)
                advanceUntilIdle()
                cancelAndIgnoreRemainingEvents()
            } // Stop

            assertEquals(IncrementalSyncStatus.Pending, arrangement.incrementalSyncRepository.incrementalSyncState.first())
        }

    @Test
    fun givenSyncIsLive_whenWorkerEmitsSources_thenShouldResetExponentialDuration() = runTest {
        val sourceFlow = Channel<EventSource>(Channel.UNLIMITED)

        val (arrangement, incrementalSyncManager) = Arrangement()
            .withWorkerReturning(sourceFlow.consumeAsFlow())
            .arrange()

        incrementalSyncManager.performSyncFlow().test {
            sourceFlow.send(EventSource.LIVE)
            advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }

        verify(VerifyMode.exactly(2)) {
            arrangement.exponentialDurationHelper.reset()
        }
        // IncrementalSyncManager resets when it starts as well
    }

    @Test
    fun givenSyncIsLive_whenWorkerEmitsSources_thenShouldResetBackoffForUserConfigSync() = runTest {
        val sourceFlow = Channel<EventSource>(Channel.UNLIMITED)

        val (arrangement, incrementalSyncManager) = Arrangement()
            .withWorkerReturning(sourceFlow.consumeAsFlow())
            .arrange()

        incrementalSyncManager.performSyncFlow().test {
            sourceFlow.send(EventSource.LIVE)
            advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }

        verify(VerifyMode.exactly(1)) {
            arrangement.userSessionWorkScheduler.resetBackoffForPeriodicUserConfigSync()
        }
    }

    @Test
    fun givenWorkerFailure_whenPerformingSync_thenShouldCalculateNextExponentialDelay() = runTest {
        val (arrangement, incrementalSyncManager) = Arrangement()
            .withWorkerReturning(flowThatFailsOnFirstTime())
            .withRecoveringFromFailure()
            .withNextExponentialDuration(1.seconds)
            .arrange()

        incrementalSyncManager.performSyncFlow().test {
            advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }

        verify(VerifyMode.exactly(1)) {
            arrangement.exponentialDurationHelper.next()
        }
    }

    @Test
    fun givenDefaultState_whenStartingSync_thenShouldResetExponentialDuration() = runTest {
        val sourceFlow = Channel<EventSource>(Channel.UNLIMITED)

        val (arrangement, incrementalSyncManager) = Arrangement()
            .withWorkerReturning(sourceFlow.consumeAsFlow())
            .arrange()

        incrementalSyncManager.performSyncFlow().test {
            advanceUntilIdle()
            verify(VerifyMode.exactly(1)) {
                arrangement.exponentialDurationHelper.reset()
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    private class Arrangement {

        val incrementalSyncWorker = mock<IncrementalSyncWorker>()
        val incrementalSyncRepository: IncrementalSyncRepository = InMemoryIncrementalSyncRepository()
        val incrementalSyncRecoveryHandler = mock<IncrementalSyncRecoveryHandler>()
        val networkStateObserver = FakeNetworkStateObserver()
        var exponentialDurationHelper: ExponentialDurationHelper = mock(mode = MockMode.autoUnit)
        val userSessionWorkScheduler: UserSessionWorkScheduler = mock(mode = MockMode.autoUnit)

        init {
            withNetworkState(MutableStateFlow(NetworkState.ConnectedWithInternet))
            withNextExponentialDuration(1.seconds)
        }

        suspend fun withWorkerReturning(sourceFlow: Flow<EventSource>) = apply {
            everySuspend {
                incrementalSyncWorker.processEventsFlow()
            } returns sourceFlow
        }

        suspend fun withRecoveringFromFailure(onRecover: suspend (OnIncrementalSyncRetryCallback) -> Unit = { it.retry() }) = apply {
            everySuspend {
                incrementalSyncRecoveryHandler.recover(any(), any())
            } calls { invocation ->
                val onRetryCallback = invocation.args[1] as OnIncrementalSyncRetryCallback
                onRecover(onRetryCallback)
            }
        }

        fun withNetworkState(networkStateFlow: StateFlow<NetworkState>) = apply {
            networkStateObserver.networkStateFlow = networkStateFlow
        }

        fun withNextExponentialDuration(duration: Duration) = apply {
            every {
                exponentialDurationHelper.next()
            } returns duration
        }

        fun withExponentialDurationHelper(helper: ExponentialDurationHelper) = apply {
            exponentialDurationHelper = helper
        }

        fun arrange() = this to IncrementalSyncManager(
            incrementalSyncWorker = incrementalSyncWorker,
            incrementalSyncRepository = incrementalSyncRepository,
            incrementalSyncRecoveryHandler = incrementalSyncRecoveryHandler,
            networkStateObserver = networkStateObserver,
            exponentialDurationHelper = exponentialDurationHelper,
            userScopedLogger = kaliumLogger,
            userSessionWorkScheduler = userSessionWorkScheduler,
        )
    }
}
