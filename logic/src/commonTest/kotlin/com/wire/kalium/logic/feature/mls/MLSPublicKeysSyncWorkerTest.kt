/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
package com.wire.kalium.logic.feature.mls

import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.data.mls.MLSPublicKeys
import com.wire.kalium.logic.data.mlspublickeys.MLSPublicKeysRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncStatus
import com.wire.kalium.logic.util.arrangement.IncrementalSyncRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.IncrementalSyncRepositoryArrangementImpl
import io.mockative.Mock
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
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

@OptIn(ExperimentalCoroutinesApi::class)
class MLSPublicKeysSyncWorkerTest {

    @Test
    fun givenLastCheckWasRecentAndSyncIsLive_whenScheduling_thenNotAttemptToFetchKeys() = runTest {
        val lastCheck = Clock.System.now() - 1.hours
        val (arrangement, mlsPublicKeysSyncWorker) = arrange {
            minIntervalBetweenRefills = 24.hours
            lastFetchInstantFlow = MutableStateFlow(lastCheck)
            withIncrementalSyncState(flowOf(IncrementalSyncStatus.Live))
        }

        val workerJob = launch {
            mlsPublicKeysSyncWorker.schedule()
        }
        advanceTimeBy(1.minutes)

        coVerify {
            arrangement.mlsPublicKeysRepository.fetchKeys()
        }.wasNotInvoked()

        workerJob.cancel()
    }

    @Test
    fun givenLastCheckWasLongAgoAndSyncIsLive_whenScheduling_thenAttemptToFetchKeys() = runTest {
        val lastCheck = Clock.System.now() - 25.hours
        val (arrangement, mlsPublicKeysSyncWorker) = arrange {
            minIntervalBetweenRefills = 24.hours
            lastFetchInstantFlow = MutableStateFlow(lastCheck)
            withIncrementalSyncState(flowOf(IncrementalSyncStatus.Live))
        }

        val workerJob = launch {
            mlsPublicKeysSyncWorker.schedule()
        }
        advanceTimeBy(1.minutes)

        coVerify {
            arrangement.mlsPublicKeysRepository.fetchKeys()
        }.wasInvoked(exactly = once)

        workerJob.cancel()
    }

    @Test
    fun givenLastCheckWasLongAgoAndSyncIsNotLive_whenScheduling_thenAttemptToFetchKeysAfterSyncBecomesLive() = runTest {
        val lastCheck = Clock.System.now() - 25.hours
        val incrementalSyncStateFlow = MutableStateFlow<IncrementalSyncStatus>(IncrementalSyncStatus.Pending)
        val (arrangement, mlsPublicKeysSyncWorker) = arrange {
            minIntervalBetweenRefills = 24.hours
            lastFetchInstantFlow = MutableStateFlow(lastCheck)
            withIncrementalSyncState(incrementalSyncStateFlow)
        }

        val workerJob = launch {
            mlsPublicKeysSyncWorker.schedule()
        }
        advanceTimeBy(1.minutes)

        coVerify {
            arrangement.mlsPublicKeysRepository.fetchKeys()
        }.wasNotInvoked() // Sync is not live yet

        incrementalSyncStateFlow.value = IncrementalSyncStatus.Live // Change sync state to live
        advanceTimeBy(1.minutes)

        coVerify {
            arrangement.mlsPublicKeysRepository.fetchKeys()
        }.wasInvoked(exactly = once) // Sync is now live

        workerJob.cancel()
    }

    @Test
    fun givenLastCheckWasRecentAndSyncIsLive_whenSchedulingAndTimeElapses_thenAttemptToFetchKeys() = runTest {
        val lastCheck = Clock.System.now() - 1.hours
        val (arrangement, mlsPublicKeysSyncWorker) = arrange {
            minIntervalBetweenRefills = 24.hours
            lastFetchInstantFlow = MutableStateFlow(lastCheck)
            withIncrementalSyncState(flowOf(IncrementalSyncStatus.Live))
        }

        val workerJob = launch {
            mlsPublicKeysSyncWorker.schedule()
        }

        coVerify {
            arrangement.mlsPublicKeysRepository.fetchKeys()
        }.wasNotInvoked()

        // Advance time until it's time to refill
        advanceTimeBy(23.hours + 1.minutes)

        coVerify {
            arrangement.mlsPublicKeysRepository.fetchKeys()
        }.wasInvoked(exactly = once)

        workerJob.cancel()
    }

    @Test
    fun givenLastCheckWasLongAgoAndSyncIsLive_whenSchedulingAndSyncChangesToLiveAgainBeforeNextIntervalPasses_thenFetchKeys() = runTest {
        val lastCheck = Clock.System.now() - 25.hours
        val lastCheckStateFlow = MutableStateFlow(lastCheck)
        val incrementalSyncStateFlow = MutableStateFlow<IncrementalSyncStatus>(IncrementalSyncStatus.Live)
        val (arrangement, mlsPublicKeysSyncWorker) = arrange {
            minIntervalBetweenRefills = 24.hours
            lastFetchInstantFlow = lastCheckStateFlow
            withIncrementalSyncState(incrementalSyncStateFlow)
        }

        val workerJob = launch {
            mlsPublicKeysSyncWorker.schedule()
        }
        advanceTimeBy(1.minutes)

        coVerify {
            arrangement.mlsPublicKeysRepository.fetchKeys()
        }.wasInvoked(exactly = once)
        lastCheckStateFlow.value = Clock.System.now()

        incrementalSyncStateFlow.value = IncrementalSyncStatus.Pending // Change sync state to not live
        incrementalSyncStateFlow.value = IncrementalSyncStatus.Live // Change sync state back to live
        advanceTimeBy(23.hours) // This should not trigger a refill since the interval hasn't elapsed

        coVerify {
            arrangement.mlsPublicKeysRepository.fetchKeys()
        }.wasNotInvoked()

        workerJob.cancel()
    }

    @Test
    fun givenLastCheckWasLongAgoAndSyncIsLive_whenSchedulingAndSyncChangesToLiveAgainAndNextIntervalPasses_thenFetchKeysAgain() = runTest {
        val lastCheck = Clock.System.now() - 25.hours
        val lastCheckStateFlow = MutableStateFlow(lastCheck)
        val incrementalSyncStateFlow = MutableStateFlow<IncrementalSyncStatus>(IncrementalSyncStatus.Live)
        val (arrangement, mlsPublicKeysSyncWorker) = arrange {
            minIntervalBetweenRefills = 24.hours
            lastFetchInstantFlow = lastCheckStateFlow
            withIncrementalSyncState(incrementalSyncStateFlow)
        }

        val workerJob = launch {
            mlsPublicKeysSyncWorker.schedule()
        }
        advanceTimeBy(1.minutes)

        coVerify {
            arrangement.mlsPublicKeysRepository.fetchKeys()
        }.wasInvoked(exactly = once)
        lastCheckStateFlow.value = Clock.System.now()

        incrementalSyncStateFlow.value = IncrementalSyncStatus.Pending // Change sync state to not live
        incrementalSyncStateFlow.value = IncrementalSyncStatus.Live // Change sync state back to live
        advanceTimeBy(24.hours + 1.minutes) // This should  trigger a refill since the interval elapsed

        coVerify {
            arrangement.mlsPublicKeysRepository.fetchKeys()
        }.wasInvoked(exactly = once)

        workerJob.cancel()
    }

    @Test
    fun givenLastCheckWasLongAgoAndSyncIsLive_whenSchedulingAndNextIntervalPasses_thenFetchKeysAgainOnlyAfterSyncIsLiveAgain() = runTest {
        val lastCheck = Clock.System.now() - 25.hours
        val lastCheckStateFlow = MutableStateFlow(lastCheck)
        val incrementalSyncStateFlow = MutableStateFlow<IncrementalSyncStatus>(IncrementalSyncStatus.Live)
        val (arrangement, mlsPublicKeysSyncWorker) = arrange {
            minIntervalBetweenRefills = 24.hours
            lastFetchInstantFlow = lastCheckStateFlow
            withIncrementalSyncState(incrementalSyncStateFlow)
        }

        val workerJob = launch {
            mlsPublicKeysSyncWorker.schedule()
        }
        advanceTimeBy(1.minutes)

        coVerify {
            arrangement.mlsPublicKeysRepository.fetchKeys()
        }.wasInvoked(exactly = once)
        lastCheckStateFlow.value = Clock.System.now()

        incrementalSyncStateFlow.value = IncrementalSyncStatus.Pending // Change sync state to not live
        advanceTimeBy(24.hours + 1.minutes) // Next interval elapses, trigger refill when sync becomes live again

        coVerify {
            arrangement.mlsPublicKeysRepository.fetchKeys()
        }.wasNotInvoked() // Should not refill yet since sync is not live

        incrementalSyncStateFlow.value = IncrementalSyncStatus.Live // Change sync state back to live
        advanceTimeBy(1.minutes)

        coVerify {
            arrangement.mlsPublicKeysRepository.fetchKeys()
        }.wasInvoked(exactly = once) // Should refill now since sync is live again

        workerJob.cancel()
    }

    @Test
    fun givenSyncIsLive_whenExecutingImmediately_thenFetchKeys() = runTest {
        val incrementalSyncStateFlow = MutableStateFlow<IncrementalSyncStatus>(IncrementalSyncStatus.Live)
        val (arrangement, mlsPublicKeysSyncWorker) = arrange {
            withIncrementalSyncState(incrementalSyncStateFlow)
        }

        val workerJob = launch {
            mlsPublicKeysSyncWorker.executeImmediately()
        }
        advanceTimeBy(1.minutes)

        coVerify {
            arrangement.mlsPublicKeysRepository.fetchKeys()
        }.wasInvoked(exactly = once)

        workerJob.cancel()
    }

    @Test
    fun givenSyncIsNotLive_whenExecutingImmediately_thenFetchKeysAfterSyncBecomesLive() = runTest {
        val incrementalSyncStateFlow = MutableStateFlow<IncrementalSyncStatus>(IncrementalSyncStatus.Pending)
        val (arrangement, mlsPublicKeysSyncWorker) = arrange {
            withIncrementalSyncState(incrementalSyncStateFlow)
        }

        val workerJob = launch {
            mlsPublicKeysSyncWorker.executeImmediately()
        }
        advanceTimeBy(1.minutes)

        coVerify {
            arrangement.mlsPublicKeysRepository.fetchKeys()
        }.wasNotInvoked() // Sync is not live yet

        incrementalSyncStateFlow.value = IncrementalSyncStatus.Live // Change sync state to live
        advanceTimeBy(1.minutes)
        coVerify {
            arrangement.mlsPublicKeysRepository.fetchKeys()
        }.wasInvoked(exactly = once) // Sync is now live

        workerJob.cancel()
    }

    private class Arrangement(private val configure: suspend Arrangement.() -> Unit) :
        IncrementalSyncRepositoryArrangement by IncrementalSyncRepositoryArrangementImpl() {

        @Mock
        val mlsPublicKeysRepository = mock(MLSPublicKeysRepository::class)
        var minIntervalBetweenRefills: Duration = 1.days
        var lastFetchInstantFlow = MutableStateFlow(Instant.DISTANT_PAST)
        
        suspend fun arrange(): Pair<Arrangement, MLSPublicKeysSyncWorker> = run {
            coEvery {
                mlsPublicKeysRepository.fetchKeys()
            }.returns(Either.Right(MLSPublicKeys(emptyMap())))
            configure()
            this@Arrangement to MLSPublicKeysSyncWorkerImpl(
                incrementalSyncRepository = incrementalSyncRepository,
                mlsPublicKeysRepository = mlsPublicKeysRepository,
                minIntervalBetweenRefills = minIntervalBetweenRefills,
                lastFetchInstantFlow = lastFetchInstantFlow,
                kaliumLogger = kaliumLogger
            )
        }
    }

    private companion object {
        suspend fun arrange(configure: suspend Arrangement.() -> Unit) = Arrangement(configure).arrange()
    }
}
