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
package com.wire.kalium.logic.feature.featureConfig

import com.wire.kalium.logic.data.sync.IncrementalSyncStatus
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.util.arrangement.IncrementalSyncRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.IncrementalSyncRepositoryArrangementImpl
import io.mockative.Mock
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

class FeatureFlagSyncWorkerTest {

    @Test
    fun givenSyncIsLive_thenShouldCallFeatureConfigsUseCase() = runTest {
        val (arrangement, featureFlagSyncWorker) = arrange {
            withIncrementalSyncState(flowOf(IncrementalSyncStatus.Live))
        }
        val job = launch {
            featureFlagSyncWorker.execute()
        }

        advanceUntilIdle()
        coVerify {
            arrangement.syncFeatureConfigsUseCase.invoke()
        }.wasInvoked(exactly = once)
        job.cancel()
    }

    @Test
    fun givenSyncIsLiveTwiceInAShortInterval_thenShouldCallFeatureConfigsUseCaseOnlyOnce() = runTest {
        val minimumInterval = 5.minutes
        val stateChannel = Channel<IncrementalSyncStatus>(capacity = Channel.UNLIMITED)

        val (arrangement, featureFlagSyncWorker) = arrange {
            minimumIntervalBetweenPulls = minimumInterval
            withIncrementalSyncState(stateChannel.consumeAsFlow())
        }
        val job = launch {
            featureFlagSyncWorker.execute()
        }
        stateChannel.send(IncrementalSyncStatus.Live)
        stateChannel.send(IncrementalSyncStatus.Pending)
        advanceUntilIdle()
        stateChannel.send(IncrementalSyncStatus.Live)
        advanceUntilIdle() // Not enough to run twice
        coVerify {
            arrangement.syncFeatureConfigsUseCase.invoke()
        }.wasInvoked(exactly = once)

        job.cancel()
    }

    @Test
    fun givenSyncIsLiveAgainAfterMinInterval_thenShouldCallFeatureConfigsUseCaseTwice() = runTest {
        val minInterval = 5.minutes
        val now = Clock.System.now()
        val stateTimes = mapOf(
            now to IncrementalSyncStatus.Live,
            now + minInterval + 1.milliseconds to IncrementalSyncStatus.Pending,
            now + minInterval + 2.milliseconds to IncrementalSyncStatus.Live
        )
        val fakeClock = object : Clock {
            var callCount = 0
            override fun now(): Instant {
                return stateTimes.keys.toList()[callCount].also { callCount++ }
            }
        }
        val stateChannel = Channel<IncrementalSyncStatus>(capacity = Channel.UNLIMITED)
        val (arrangement, featureFlagSyncWorker) = arrange {
            minimumIntervalBetweenPulls = minInterval
            withIncrementalSyncState(stateChannel.consumeAsFlow())
            clock = fakeClock
        }
        stateChannel.send(stateTimes.values.toList()[0])
        val job = launch {
            featureFlagSyncWorker.execute()
        }
        advanceUntilIdle()

        coVerify {
            arrangement.syncFeatureConfigsUseCase.invoke()
        }.wasInvoked(exactly = once)
        stateChannel.send(stateTimes.values.toList()[1])
        advanceUntilIdle()

        stateChannel.send(stateTimes.values.toList()[2])
        advanceUntilIdle()

        coVerify {
            arrangement.syncFeatureConfigsUseCase.invoke()
        }.wasInvoked(exactly = once)
        job.cancel()
    }

    private class Arrangement(
        private val configure: Arrangement.() -> Unit
    ) : IncrementalSyncRepositoryArrangement by IncrementalSyncRepositoryArrangementImpl() {

        @Mock
        val syncFeatureConfigsUseCase: SyncFeatureConfigsUseCase = mock(SyncFeatureConfigsUseCase::class)

        var minimumIntervalBetweenPulls: Duration = 1.minutes

        var clock: Clock = Clock.System

        suspend fun arrange(): Pair<Arrangement, FeatureFlagSyncWorkerImpl> = run {
            coEvery {
                syncFeatureConfigsUseCase.invoke()
            }.returns(Either.Right(Unit))
            configure()
            this@Arrangement to FeatureFlagSyncWorkerImpl(
                incrementalSyncRepository = incrementalSyncRepository,
                syncFeatureConfigs = syncFeatureConfigsUseCase,
                minIntervalBetweenPulls = minimumIntervalBetweenPulls,
                clock = clock,
                kaliumLogger = kaliumLogger
            )
        }
    }

    private companion object {
        suspend fun arrange(configure: Arrangement.() -> Unit) = Arrangement(configure).arrange()
    }
}
