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

package com.wire.kalium.logic.sync

import app.cash.turbine.test
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.sync.IncrementalSyncRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncStatus
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.data.sync.SlowSyncStatus
import com.wire.kalium.logic.data.sync.SlowSyncStep
import com.wire.kalium.logic.data.sync.SyncState
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import io.mockative.Mock
import io.mockative.every
import io.mockative.mock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration

class ObserveSyncStateUseCaseTest {

    @Test
    fun givenSlowSyncStatusEmitsFailedState_whenRunningUseCase_thenEmitFailedState() = runTest(TestKaliumDispatcher.default) {
        val (_, useCase) = Arrangement()
            .withSlowSyncFailureState()
            .withIncrementalSyncLiveState()
            .arrange()

        useCase().test {
            val item = awaitItem()
            assertEquals(SyncState.Failed(coreFailure, Duration.ZERO), item)
        }
    }

    @Test
    fun givenSlowSyncStatusEmitsOngoingState_whenRunningUseCase_thenEmitSlowSyncState() = runTest(TestKaliumDispatcher.default) {
        val (_, useCase) = Arrangement()
            .withSlowSyncOngoingState()
            .withIncrementalSyncLiveState()
            .arrange()

        useCase().test {
            val item = awaitItem()
            assertEquals(SyncState.SlowSync, item)
        }
    }

    @Test
    fun givenSlowSyncStatusEmitsPendingState_whenRunningUseCase_thenEmitWaitingState() = runTest(TestKaliumDispatcher.default) {
        val (_, useCase) = Arrangement()
            .withSlowSyncPendingState()
            .withIncrementalSyncLiveState()
            .arrange()

        useCase().test {
            val item = awaitItem()
            assertEquals(SyncState.Waiting, item)
        }
    }

    @Test
    fun givenIncrementalSyncStateEmitsLiveState_whenRunningUseCase_thenEmitLiveState() = runTest(TestKaliumDispatcher.default) {
        val (_, useCase) = Arrangement()
            .withSlowSyncCompletedState()
            .withIncrementalSyncLiveState()
            .arrange()

        useCase().test {
            val item = awaitItem()
            assertEquals(SyncState.Live, item)
        }
    }

    @Test
    fun givenIncrementalSyncStateEmitsFailedState_whenRunningUseCase_thenEmitFailedState() =
        runTest(TestKaliumDispatcher.default) {
            val (_, useCase) = Arrangement()
                .withSlowSyncCompletedState()
                .withIncrementalSyncFailedState()
                .arrange()

            useCase().test {
                val item = awaitItem()
                assertEquals(SyncState.Failed(coreFailure, Duration.ZERO), item)
            }
        }

    @Test
    fun givenIncrementalSyncStateEmitsFetchingPendingEventsState_whenRunningUseCase_thenEmitGatheringPendingEventsState() =
        runTest(TestKaliumDispatcher.default) {
            val (_, useCase) = Arrangement()
                .withSlowSyncCompletedState()
                .withIncrementalSyncFetchingPendingEventsState()
                .arrange()

            useCase().test {
                val item = awaitItem()
                assertEquals(SyncState.GatheringPendingEvents, item)
            }
        }

    @Test
    fun givenIncrementalSyncStateEmitsPendingState_whenRunningUseCase_thenEmitGatheringPendingEventsState() =
        runTest(TestKaliumDispatcher.default) {
            val (_, useCase) = Arrangement()
                .withSlowSyncCompletedState()
                .withIncrementalSyncPendingState()
                .arrange()

            useCase().test {
                val item = awaitItem()
                assertEquals(SyncState.GatheringPendingEvents, item)
            }
        }

    private class Arrangement {

        @Mock
        val slowSyncRepository: SlowSyncRepository = mock(SlowSyncRepository::class)

        @Mock
        val incrementalSyncRepository: IncrementalSyncRepository = mock(IncrementalSyncRepository::class)

        fun arrange() = this to ObserveSyncStateUseCaseImpl(
            slowSyncRepository = slowSyncRepository,
            incrementalSyncRepository = incrementalSyncRepository
        )

        fun withSlowSyncFailureState() = apply {
            every {
                slowSyncRepository.slowSyncStatus
            }.returns(slowSyncFailureFlow)
        }

        fun withSlowSyncOngoingState() = apply {
            every {
                slowSyncRepository.slowSyncStatus
            }.returns(MutableStateFlow(SlowSyncStatus.Ongoing(SlowSyncStep.CONTACTS)).asStateFlow())
        }

        fun withSlowSyncPendingState() = apply {
            every {
                slowSyncRepository.slowSyncStatus
            }.returns(MutableStateFlow(SlowSyncStatus.Pending).asStateFlow())
        }

        fun withSlowSyncCompletedState() = apply {
            every {
                slowSyncRepository.slowSyncStatus
            }.returns(MutableStateFlow(SlowSyncStatus.Complete).asStateFlow())
        }

        fun withIncrementalSyncLiveState() = apply {
            every {
                incrementalSyncRepository.incrementalSyncState
            }.returns(flowOf(IncrementalSyncStatus.Live))
        }

        fun withIncrementalSyncFailedState() = apply {
            every {
                incrementalSyncRepository.incrementalSyncState
            }.returns(incrementalSyncFailureFlow)
        }

        fun withIncrementalSyncFetchingPendingEventsState() = apply {
            every {
                incrementalSyncRepository.incrementalSyncState
            }.returns(flowOf(IncrementalSyncStatus.FetchingPendingEvents))
        }

        fun withIncrementalSyncPendingState() = apply {
            every {
                incrementalSyncRepository.incrementalSyncState
            }.returns(flowOf(IncrementalSyncStatus.Pending))
        }
    }

    companion object {
        val coreFailure = CoreFailure.Unknown(null)
        val slowSyncFailureFlow = MutableStateFlow(SlowSyncStatus.Failed(coreFailure, Duration.ZERO)).asStateFlow()
        val incrementalSyncFailureFlow = flowOf(IncrementalSyncStatus.Failed(coreFailure, Duration.ZERO))
    }
}
