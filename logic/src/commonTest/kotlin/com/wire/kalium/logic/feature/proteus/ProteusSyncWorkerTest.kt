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
package com.wire.kalium.logic.feature.proteus

import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.data.sync.IncrementalSyncStatus
import com.wire.kalium.logic.util.arrangement.IncrementalSyncRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.IncrementalSyncRepositoryArrangementImpl
import com.wire.kalium.logic.util.arrangement.PreKeyRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.PreKeyRepositoryArrangementImpl
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

@OptIn(ExperimentalCoroutinesApi::class)
class ProteusSyncWorkerTest {

    @Test
    fun givenLastCheckWasRecentAndSyncIsLive_thenShouldNotAttemptToRefillPreKeys() = runTest {
        val lastCheck = Clock.System.now() - 1.hours
        val (arrangement, proteusSyncWorker) = arrange {
            minIntervalBetweenRefills = 24.hours
            withObserveLastPreKeyUploadInstantReturning(flowOf(lastCheck))
            withIncrementalSyncState(flowOf(IncrementalSyncStatus.Live))
            withSetLastPreKeyUploadInstantReturning(Either.Right(Unit))
        }

        val workerJob = launch {
            proteusSyncWorker.execute()
        }
        advanceTimeBy(1.minutes)

        coVerify {
            arrangement.proteusPreKeyRefiller.refillIfNeeded()
        }.wasNotInvoked()

        workerJob.cancel()
    }

    @Test
    fun givenLastCheckWasLongAgoAndSyncIsLive_thenShouldAttemptToRefillPreKeys() = runTest {
        val lastCheck = Clock.System.now() - 25.hours
        val (arrangement, proteusSyncWorker) = arrange {
            minIntervalBetweenRefills = 24.hours
            withObserveLastPreKeyUploadInstantReturning(flowOf(lastCheck))
            withIncrementalSyncState(flowOf(IncrementalSyncStatus.Live))
            withSetLastPreKeyUploadInstantReturning(Either.Right(Unit))
        }

        val workerJob = launch {
            proteusSyncWorker.execute()
        }
        advanceTimeBy(1.minutes)

        coVerify {
            arrangement.proteusPreKeyRefiller.refillIfNeeded()
        }.wasInvoked(exactly = once)

        workerJob.cancel()
    }

    @Test
    fun givenLastCheckWasLongAgoAndSyncIsNotLive_thenShouldAttemptToRefillPreKeysAfterSyncBecomesLive() = runTest {
        val lastCheck = Clock.System.now() - 25.hours
        val incrementalSyncStateFlow = MutableStateFlow<IncrementalSyncStatus>(IncrementalSyncStatus.Pending)
        val (arrangement, mlsPublicKeysSyncWorker) = arrange {
            minIntervalBetweenRefills = 24.hours
            withObserveLastPreKeyUploadInstantReturning(flowOf(lastCheck))
            withIncrementalSyncState(incrementalSyncStateFlow)
            withSetLastPreKeyUploadInstantReturning(Either.Right(Unit))
        }

        val workerJob = launch {
            mlsPublicKeysSyncWorker.execute()
        }
        advanceTimeBy(1.minutes)

        coVerify {
            arrangement.proteusPreKeyRefiller.refillIfNeeded()
        }.wasNotInvoked() // Sync is not live yet

        incrementalSyncStateFlow.value = IncrementalSyncStatus.Live // Change sync state to live
        advanceTimeBy(1.minutes)

        coVerify {
            arrangement.proteusPreKeyRefiller.refillIfNeeded()
        }.wasInvoked(exactly = once) // Sync is now live

        workerJob.cancel()
    }

    @Test
    fun givenLastCheckWasRecentAndSyncIsLive_whenTimeElapses_thenShouldAttemptToRefillPreKeys() = runTest {
        val lastCheck = Clock.System.now() - 1.hours
        val (arrangement, proteusSyncWorker) = arrange {
            minIntervalBetweenRefills = 24.hours
            withObserveLastPreKeyUploadInstantReturning(flowOf(lastCheck))
            withIncrementalSyncState(flowOf(IncrementalSyncStatus.Live))
            withSetLastPreKeyUploadInstantReturning(Either.Right(Unit))
        }

        val workerJob = launch {
            proteusSyncWorker.execute()
        }

        coVerify {
            arrangement.proteusPreKeyRefiller.refillIfNeeded()
        }.wasNotInvoked()

        // Advance time until it's time to refill
        advanceTimeBy(23.hours + 1.minutes)

        coVerify {
            arrangement.proteusPreKeyRefiller.refillIfNeeded()
        }.wasInvoked(exactly = once)

        workerJob.cancel()
    }

    @Test
    fun givenLastCheckWasLongAgoAndSyncIsLive_whenSyncChangesToLiveAgainBeforeNextIntervalPasses_thenShouldNotRefillPreKeys() = runTest {
        val lastCheck = Clock.System.now() - 25.hours
        val lastCheckStateFlow = MutableStateFlow(lastCheck)
        val incrementalSyncStateFlow = MutableStateFlow<IncrementalSyncStatus>(IncrementalSyncStatus.Live)
        val (arrangement, proteusSyncWorker) = arrange {
            minIntervalBetweenRefills = 24.hours
            withObserveLastPreKeyUploadInstantReturning(lastCheckStateFlow)
            withIncrementalSyncState(incrementalSyncStateFlow)
            withSetLastPreKeyUploadInstantReturning(Either.Right(Unit))
        }

        val workerJob = launch {
            proteusSyncWorker.execute()
        }
        advanceTimeBy(1.minutes)

        coVerify {
            arrangement.proteusPreKeyRefiller.refillIfNeeded()
        }.wasInvoked(exactly = once)
        lastCheckStateFlow.value = Clock.System.now()

        incrementalSyncStateFlow.value = IncrementalSyncStatus.Pending // Change sync state to not live
        incrementalSyncStateFlow.value = IncrementalSyncStatus.Live // Change sync state back to live
        advanceTimeBy(23.hours) // This should not trigger a refill since the interval hasn't elapsed

        coVerify {
            arrangement.proteusPreKeyRefiller.refillIfNeeded()
        }.wasNotInvoked()

        workerJob.cancel()
    }

    @Test
    fun givenLastCheckWasLongAgoAndSyncIsLive_whenSyncChangesToLiveAgainAndNextIntervalPasses_thenShouldRefillPreKeysAgain() = runTest {
        val lastCheck = Clock.System.now() - 25.hours
        val lastCheckStateFlow = MutableStateFlow(lastCheck)
        val incrementalSyncStateFlow = MutableStateFlow<IncrementalSyncStatus>(IncrementalSyncStatus.Live)
        val (arrangement, proteusSyncWorker) = arrange {
            minIntervalBetweenRefills = 24.hours
            withObserveLastPreKeyUploadInstantReturning(lastCheckStateFlow)
            withIncrementalSyncState(incrementalSyncStateFlow)
            withSetLastPreKeyUploadInstantReturning(Either.Right(Unit))
        }

        val workerJob = launch {
            proteusSyncWorker.execute()
        }
        advanceTimeBy(1.minutes)

        coVerify {
            arrangement.proteusPreKeyRefiller.refillIfNeeded()
        }.wasInvoked(exactly = once)
        lastCheckStateFlow.value = Clock.System.now()

        incrementalSyncStateFlow.value = IncrementalSyncStatus.Pending // Change sync state to not live
        incrementalSyncStateFlow.value = IncrementalSyncStatus.Live // Change sync state back to live
        advanceTimeBy(24.hours + 1.minutes) // This should  trigger a refill since the interval elapsed

        coVerify {
            arrangement.proteusPreKeyRefiller.refillIfNeeded()
        }.wasInvoked(exactly = once)

        workerJob.cancel()
    }

    @Test
    fun givenLastCheckWasLongAgoAndSyncIsLive_whenNextIntervalPasses_thenShouldRefillPreKeysAgainOnlyAfterSyncIsLiveAgain() = runTest {
        val lastCheck = Clock.System.now() - 25.hours
        val lastCheckStateFlow = MutableStateFlow(lastCheck)
        val incrementalSyncStateFlow = MutableStateFlow<IncrementalSyncStatus>(IncrementalSyncStatus.Live)
        val (arrangement, proteusSyncWorker) = arrange {
            minIntervalBetweenRefills = 24.hours
            withObserveLastPreKeyUploadInstantReturning(lastCheckStateFlow)
            withIncrementalSyncState(incrementalSyncStateFlow)
            withSetLastPreKeyUploadInstantReturning(Either.Right(Unit))
        }

        val workerJob = launch {
            proteusSyncWorker.execute()
        }
        advanceTimeBy(1.minutes)

        coVerify {
            arrangement.proteusPreKeyRefiller.refillIfNeeded()
        }.wasInvoked(exactly = once)
        lastCheckStateFlow.value = Clock.System.now()

        incrementalSyncStateFlow.value = IncrementalSyncStatus.Pending // Change sync state to not live
        advanceTimeBy(24.hours + 1.minutes) // Next interval elapses, trigger refill when sync becomes live again

        coVerify {
            arrangement.proteusPreKeyRefiller.refillIfNeeded()
        }.wasNotInvoked() // Should not refill yet since sync is not live

        incrementalSyncStateFlow.value = IncrementalSyncStatus.Live // Change sync state back to live
        advanceTimeBy(1.minutes)

        coVerify {
            arrangement.proteusPreKeyRefiller.refillIfNeeded()
        }.wasInvoked(exactly = once) // Should refill now since sync is live again

        workerJob.cancel()
    }

    private class Arrangement(private val configure: suspend Arrangement.() -> Unit) :
        IncrementalSyncRepositoryArrangement by IncrementalSyncRepositoryArrangementImpl(),
        PreKeyRepositoryArrangement by PreKeyRepositoryArrangementImpl() {
        val proteusPreKeyRefiller = mock(ProteusPreKeyRefiller::class)
        var minIntervalBetweenRefills: Duration = 1.days

        suspend fun arrange(): Pair<Arrangement, ProteusSyncWorker> = run {
            coEvery {
                proteusPreKeyRefiller.refillIfNeeded()
            }.returns(Either.Right(Unit))
            configure()
            this@Arrangement to ProteusSyncWorkerImpl(
                incrementalSyncRepository = incrementalSyncRepository,
                proteusPreKeyRefiller = proteusPreKeyRefiller,
                preKeyRepository = preKeyRepository,
                minIntervalBetweenRefills = minIntervalBetweenRefills,
                kaliumLogger = kaliumLogger
            )
        }
    }

    private companion object {
        suspend fun arrange(configure: suspend Arrangement.() -> Unit) = Arrangement(configure).arrange()
    }
}
