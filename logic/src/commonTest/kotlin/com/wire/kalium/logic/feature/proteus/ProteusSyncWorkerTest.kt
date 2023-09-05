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
package com.wire.kalium.logic.feature.proteus

import com.wire.kalium.logic.data.sync.IncrementalSyncStatus
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.util.arrangement.IncrementalSyncRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.IncrementalSyncRepositoryArrangementImpl
import com.wire.kalium.logic.util.arrangement.PreKeyRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.PreKeyRepositoryArrangementImpl
import io.mockative.Mock
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

class ProteusSyncWorkerTest {

    @Test
    fun givenLastCheckWasRecentAndSyncIsLive_thenShouldNotAttemptToRefillPreKeys() = runTest {
        val now = Clock.System.now() - 23.hours
        val (arrangement, proteusSyncWorker) = arrange {
            minIntervalBetweenRefills = 24.hours
            withObserveLastPreKeyUploadInstantReturning(flowOf(now))
            withIncrementalSyncState(flowOf(IncrementalSyncStatus.Live))
            withSetLastPreKeyUploadInstantReturning(Either.Right(Unit))
        }

        launch {
            proteusSyncWorker.execute()
        }

        verify(arrangement.proteusPreKeyRefiller)
            .suspendFunction(arrangement.proteusPreKeyRefiller::refillIfNeeded)
            .wasNotInvoked()
    }

    @Test
    fun givenLastCheckWasLongAgoAndSyncIsLive_thenShouldAttemptToRefillPreKeys() = runTest {
        val now = Clock.System.now() - 24.hours
        val (arrangement, proteusSyncWorker) = arrange {
            minIntervalBetweenRefills = 23.hours
            withObserveLastPreKeyUploadInstantReturning(flowOf(now))
            withIncrementalSyncState(flowOf(IncrementalSyncStatus.Live))
            withSetLastPreKeyUploadInstantReturning(Either.Right(Unit))
        }

        proteusSyncWorker.execute()

        verify(arrangement.proteusPreKeyRefiller)
            .suspendFunction(arrangement.proteusPreKeyRefiller::refillIfNeeded)
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenLastCheckWasRecentAndSyncIsLive_whenTimeElapses_thenShouldAttemptToRefillPreKeys() = runTest {
        val now = Clock.System.now() - 23.hours
        val (arrangement, proteusSyncWorker) = arrange {
            minIntervalBetweenRefills = 24.hours
            withObserveLastPreKeyUploadInstantReturning(flowOf(now))
            withIncrementalSyncState(flowOf(IncrementalSyncStatus.Live))
            withSetLastPreKeyUploadInstantReturning(Either.Right(Unit))
        }

        launch {
            proteusSyncWorker.execute()
        }

        verify(arrangement.proteusPreKeyRefiller)
            .suspendFunction(arrangement.proteusPreKeyRefiller::refillIfNeeded)
            .wasNotInvoked()

        // Advance time until it's time to refill
        advanceTimeBy(2.hours)

        verify(arrangement.proteusPreKeyRefiller)
            .suspendFunction(arrangement.proteusPreKeyRefiller::refillIfNeeded)
            .wasInvoked(exactly = once)
    }

    private class Arrangement(private val configure: Arrangement.() -> Unit) :
        IncrementalSyncRepositoryArrangement by IncrementalSyncRepositoryArrangementImpl(),
        PreKeyRepositoryArrangement by PreKeyRepositoryArrangementImpl() {

        @Mock
        val proteusPreKeyRefiller = mock(ProteusPreKeyRefiller::class)
        var minIntervalBetweenRefills: Duration = 1.days

        init {
            given(proteusPreKeyRefiller)
                .suspendFunction(proteusPreKeyRefiller::refillIfNeeded)
                .whenInvoked()
                .thenReturn(Either.Right(Unit))
        }

        fun arrange(): Pair<Arrangement, ProteusSyncWorker> = run {
            configure()
            this@Arrangement to ProteusSyncWorkerImpl(
                incrementalSyncRepository = incrementalSyncRepository,
                proteusPreKeyRefiller = proteusPreKeyRefiller,
                preKeyRepository = preKeyRepository,
                minInterValBetweenRefills = minIntervalBetweenRefills
            )
        }
    }

    private companion object {
        fun arrange(configure: Arrangement.() -> Unit) = Arrangement(configure).arrange()
    }
}
