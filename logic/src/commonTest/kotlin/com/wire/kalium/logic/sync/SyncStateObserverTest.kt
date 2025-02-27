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
import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.sync.InMemoryIncrementalSyncRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncStatus
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.data.sync.SlowSyncRepositoryImpl
import com.wire.kalium.logic.data.sync.SlowSyncStatus
import com.wire.kalium.logic.data.sync.SlowSyncStep
import com.wire.kalium.logic.data.sync.SyncState
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.persistence.TestUserDatabase
import com.wire.kalium.persistence.dao.UserIDEntity
import io.mockative.Mock
import io.mockative.mock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration

@OptIn(ExperimentalCoroutinesApi::class)
class SyncStateObserverTest {

    @Test
    fun givenSlowSyncFailed_whenWaitingUntilLiveOrFailure_thenShouldReturnFailure() = runTest {
        val (arrangement, syncManager) = Arrangement(backgroundScope).arrange()
        arrangement.slowSyncRepository.updateSlowSyncStatus(SlowSyncStatus.Failed(CoreFailure.MissingClientRegistration, Duration.ZERO))
        arrangement.incrementalSyncRepository.updateIncrementalSyncState(IncrementalSyncStatus.Pending)

        val result = syncManager.waitUntilLiveOrFailure()

        result.shouldFail()
    }

    @Test
    fun givenIncrementalSyncFailedAndSlowSyncIsComplete_whenWaitingUntilLiveOrFailure_thenShouldReturnFailure() = runTest {
        val (arrangement, syncManager) = Arrangement(backgroundScope).arrange()
        arrangement.slowSyncRepository.updateSlowSyncStatus(SlowSyncStatus.Complete)
        val failedState = IncrementalSyncStatus.Failed(CoreFailure.MissingClientRegistration, Duration.ZERO)
        arrangement.incrementalSyncRepository.updateIncrementalSyncState(failedState)

        val result = syncManager.waitUntilLiveOrFailure()

        result.shouldFail()
    }

    @Test
    fun givenSlowSyncIsBeingPerformedAndFails_whenWaitingUntilLiveOrFailure_thenShouldWaitAndThenFail() = runTest {
        val (arrangement, syncManager) = Arrangement(backgroundScope).arrange()
        arrangement.slowSyncRepository.updateSlowSyncStatus(SlowSyncStatus.Ongoing(SlowSyncStep.CONNECTIONS))
        arrangement.incrementalSyncRepository.updateIncrementalSyncState(IncrementalSyncStatus.Pending)

        val result = async {
            syncManager.waitUntilLiveOrFailure()
        }
        advanceUntilIdle()
        assertTrue { result.isActive }

        arrangement.slowSyncRepository.updateSlowSyncStatus(SlowSyncStatus.Failed(CoreFailure.MissingClientRegistration, Duration.ZERO))
        advanceUntilIdle()
        result.await().shouldFail()
    }

    @Test
    fun givenSlowSyncIsBeingPerformedAndSucceedsButIncrementalFails_whenWaitingUntilLiveOrFailure_thenShouldWaitAndThenFail() = runTest {
        val (arrangement, syncManager) = Arrangement(backgroundScope).arrange()
        arrangement.slowSyncRepository.updateSlowSyncStatus(SlowSyncStatus.Ongoing(SlowSyncStep.CONNECTIONS))
        arrangement.incrementalSyncRepository.updateIncrementalSyncState(IncrementalSyncStatus.Pending)

        val result = async {
            syncManager.waitUntilLiveOrFailure()
        }
        advanceUntilIdle()
        assertTrue { result.isActive }

        arrangement.slowSyncRepository.updateSlowSyncStatus(SlowSyncStatus.Complete)
        val failure = IncrementalSyncStatus.Failed(CoreFailure.MissingClientRegistration, Duration.ZERO)
        arrangement.incrementalSyncRepository.updateIncrementalSyncState(failure)
        advanceUntilIdle()
        result.await().shouldFail()
    }

    @Test
    fun givenSlowSyncIsCompleteAndIncrementalSyncIsOngoing_whenWaitingUntilLiveOrFailure_thenShouldWaitUntilCompleteReturnSucceed() =
        runTest {
            val (arrangement, syncManager) = Arrangement(backgroundScope).arrange()
            arrangement.slowSyncRepository.updateSlowSyncStatus(SlowSyncStatus.Complete)
            arrangement.incrementalSyncRepository.updateIncrementalSyncState(IncrementalSyncStatus.Pending)

            val result = async {
                syncManager.waitUntilLiveOrFailure()
            }
            advanceUntilIdle()
            assertTrue { result.isActive }

            arrangement.incrementalSyncRepository.updateIncrementalSyncState(IncrementalSyncStatus.FetchingPendingEvents)
            advanceUntilIdle()
            assertTrue { result.isActive }

            arrangement.incrementalSyncRepository.updateIncrementalSyncState(IncrementalSyncStatus.Live)
            advanceUntilIdle()
            assertTrue { result.isCompleted }

            result.await().shouldSucceed()
        }

    @Test
    fun givenSlowSyncIsCompleteAndIncrementalSyncIsOngoingButFails_whenWaitingUntilLiveOrFailure_thenShouldWaitUntilFailure() =
        runTest {
            val (arrangement, syncManager) = Arrangement(backgroundScope).arrange()
            arrangement.slowSyncRepository.updateSlowSyncStatus(SlowSyncStatus.Complete)
            arrangement.incrementalSyncRepository.updateIncrementalSyncState(IncrementalSyncStatus.Pending)

            val result = async {
                syncManager.waitUntilLiveOrFailure()
            }
            advanceUntilIdle()
            assertTrue { result.isActive }

            arrangement.incrementalSyncRepository.updateIncrementalSyncState(IncrementalSyncStatus.FetchingPendingEvents)
            advanceUntilIdle()
            assertTrue { result.isActive }

            val failure = IncrementalSyncStatus.Failed(CoreFailure.MissingClientRegistration, Duration.ZERO)
            arrangement.incrementalSyncRepository.updateIncrementalSyncState(failure)
            advanceUntilIdle()
            assertTrue { result.isCompleted }

            result.await().shouldFail()
        }

    @Test
    fun givenSlowSyncFailed_whenWaitingUntilStartedOrFailure_thenShouldReturnFailure() = runTest {
        val (arrangement, syncManager) = Arrangement(backgroundScope).arrange()
        arrangement.slowSyncRepository.updateSlowSyncStatus(SlowSyncStatus.Failed(CoreFailure.MissingClientRegistration, Duration.ZERO))

        val result = syncManager.waitUntilStartedOrFailure()

        result.shouldFail()
    }

    @Test
    fun givenSlowSyncOngoing_whenWaitingUntilStartedOrFailure_thenShouldReturnSuccess() = runTest {
        val (arrangement, syncManager) = Arrangement(backgroundScope).arrange()
        arrangement.slowSyncRepository.updateSlowSyncStatus(SlowSyncStatus.Ongoing(SlowSyncStep.CONNECTIONS))

        val result = syncManager.waitUntilStartedOrFailure()

        result.shouldSucceed()
    }

    @Test
    fun givenSlowSyncComplete_whenWaitingUntilStartedOrFailure_thenShouldReturnSuccess() = runTest {
        val (arrangement, syncManager) = Arrangement(backgroundScope).arrange()
        arrangement.slowSyncRepository.updateSlowSyncStatus(SlowSyncStatus.Complete)

        val result = syncManager.waitUntilStartedOrFailure()

        result.shouldSucceed()
    }

    @Test
    fun givenSlowSyncRepositoryReturnsOngoingState_whenCallingIsSlowSyncOngoing_thenReturnTrue() = runTest {
        val (arrangement, syncManager) = Arrangement(backgroundScope).arrange()
        arrangement.slowSyncRepository.updateSlowSyncStatus(SlowSyncStatus.Ongoing(SlowSyncStep.CONNECTIONS))

        val result = syncManager.isSlowSyncOngoing()

        assertTrue { result }
    }

    @Test
    fun givenSlowSyncRepositoryReturnsDifferentStateThanOngoing_whenCallingIsSlowSyncOngoing_thenReturnFalse() = runTest {
        val (arrangement, syncManager) = Arrangement(backgroundScope).arrange()
        arrangement.slowSyncRepository.updateSlowSyncStatus(SlowSyncStatus.Pending)

        val result = syncManager.isSlowSyncOngoing()

        assertFalse { result }
    }

    @Test
    fun givenSlowSyncRepositoryReturnsCompleteState_whenCallingIsSlowSyncCompleted_thenReturnTrue() = runTest {
        val (arrangement, syncManager) = Arrangement(backgroundScope).arrange()
        arrangement.slowSyncRepository.updateSlowSyncStatus(SlowSyncStatus.Complete)

        val result = syncManager.isSlowSyncCompleted()

        assertTrue { result }
    }

    @Test
    fun givenSlowSyncRepositoryReturnsDifferentStateThanComplete_whenCallingIsSlowSyncCompleted_thenReturnFalse() = runTest {
        val (arrangement, syncManager) = Arrangement(backgroundScope).arrange()
        arrangement.slowSyncRepository.updateSlowSyncStatus(SlowSyncStatus.Pending)

        advanceUntilIdle()

        val result = syncManager.isSlowSyncCompleted()

        assertFalse { result }
    }


    @Test
    fun givenSlowSyncStatusEmitsFailedState_whenRunningUseCase_thenEmitFailedState() = runTest(TestKaliumDispatcher.default) {
        val (_, observer) = Arrangement(backgroundScope)
            .withSlowSyncFailureState()
            .withIncrementalSyncLiveState()
            .arrange()

        observer.syncState.test {
            awaitItem() // Skip Waiting
            val item = awaitItem()
            assertEquals(SyncState.Failed(coreFailure, Duration.ZERO), item)
        }
    }

    @Test
    fun givenSlowSyncStatusEmitsOngoingState_whenRunningUseCase_thenEmitSlowSyncState() = runTest(TestKaliumDispatcher.default) {
        val (_, observer) = Arrangement(backgroundScope)
            .withSlowSyncOngoingState()
            .withIncrementalSyncLiveState()
            .arrange()

        observer.syncState.test {
            awaitItem() // Skip Waiting
            val item = awaitItem()
            assertEquals(SyncState.SlowSync, item)
        }
    }

    @Test
    fun givenSlowSyncStatusEmitsPendingState_whenRunningUseCase_thenEmitWaitingState() = runTest(TestKaliumDispatcher.default) {
        val (_, observer) = Arrangement(backgroundScope)
            .withSlowSyncPendingState()
            .withIncrementalSyncLiveState()
            .arrange()

        observer.syncState.test {
            val item = awaitItem()
            assertEquals(SyncState.Waiting, item)
        }
    }

    @Test
    fun givenIncrementalSyncStateEmitsLiveState_whenRunningUseCase_thenEmitLiveState() = runTest(TestKaliumDispatcher.default) {
        val (_, observer) = Arrangement(backgroundScope)
            .withSlowSyncCompletedState()
            .withIncrementalSyncLiveState()
            .arrange()

        observer.syncState.test {
            awaitItem() // Skip Waiting
            val item = awaitItem()
            assertEquals(SyncState.Live, item)
        }
    }

    @Test
    fun givenIncrementalSyncStateEmitsFailedState_whenRunningUseCase_thenEmitFailedState() =
        runTest(TestKaliumDispatcher.default) {
            val (_, observer) = Arrangement(backgroundScope)
                .withSlowSyncCompletedState()
                .withIncrementalSyncFailedState()
                .arrange()

            observer.syncState.test {
                awaitItem() // Skip Waiting
                val item = awaitItem()
                assertEquals(SyncState.Failed(coreFailure, Duration.ZERO), item)
            }
        }

    @Test
    fun givenIncrementalSyncStateEmitsFetchingPendingEventsState_whenRunningUseCase_thenEmitGatheringPendingEventsState() =
        runTest(TestKaliumDispatcher.default) {
            val (_, observer) = Arrangement(backgroundScope)
                .withSlowSyncCompletedState()
                .withIncrementalSyncFetchingPendingEventsState()
                .arrange()

            observer.syncState.test {
                awaitItem() // Skip Waiting
                val item = awaitItem()
                assertEquals(SyncState.GatheringPendingEvents, item)
            }
        }

    @Test
    fun givenIncrementalSyncStateEmitsPendingState_whenRunningUseCase_thenEmitGatheringPendingEventsState() =
        runTest(TestKaliumDispatcher.default) {
            val (_, observer) = Arrangement(backgroundScope)
                .withSlowSyncCompletedState()
                .withIncrementalSyncPendingState()
                .arrange()

            observer.syncState.test {
                awaitItem() // Skip Waiting
                val item = awaitItem()
                assertEquals(SyncState.GatheringPendingEvents, item)
            }
        }

    @Suppress("unused")
    private class Arrangement(private val scope: CoroutineScope) {
        val database = TestUserDatabase(UserIDEntity("SELF_USER", "DOMAIN"))
        val slowSyncRepository: SlowSyncRepository = SlowSyncRepositoryImpl(database.builder.metadataDAO)

        @Mock
        val sessionRepository = mock(SessionRepository::class)

        val incrementalSyncRepository: IncrementalSyncRepository = InMemoryIncrementalSyncRepository()

        fun arrange(): Pair<Arrangement, SyncStateObserver> = this to SyncStateObserverImpl(
            slowSyncRepository, incrementalSyncRepository, scope
        )

        fun withSlowSyncFailureState() = apply {
            slowSyncRepository.updateSlowSyncStatus(slowSyncFailureStatus)
        }

        fun withSlowSyncOngoingState() = apply {
            slowSyncRepository.updateSlowSyncStatus(SlowSyncStatus.Ongoing(SlowSyncStep.CONTACTS))
        }

        fun withSlowSyncPendingState() = apply {
            slowSyncRepository.updateSlowSyncStatus(SlowSyncStatus.Pending)
        }

        fun withSlowSyncCompletedState() = apply {
            slowSyncRepository.updateSlowSyncStatus(SlowSyncStatus.Complete)
        }

        suspend fun withIncrementalSyncLiveState() = apply {
            incrementalSyncRepository.updateIncrementalSyncState(IncrementalSyncStatus.Live)
        }

        suspend fun withIncrementalSyncFailedState() = apply {
            incrementalSyncRepository.updateIncrementalSyncState(incrementalSyncFailureStatus)
        }

        suspend fun withIncrementalSyncFetchingPendingEventsState() = apply {
            incrementalSyncRepository.updateIncrementalSyncState(IncrementalSyncStatus.FetchingPendingEvents)
        }

        suspend fun withIncrementalSyncPendingState() = apply {
            incrementalSyncRepository.updateIncrementalSyncState(IncrementalSyncStatus.Pending)
        }
    }

    private companion object {
        val coreFailure = CoreFailure.Unknown(null)
        val slowSyncFailureStatus = SlowSyncStatus.Failed(coreFailure, Duration.ZERO)
        val incrementalSyncFailureStatus = IncrementalSyncStatus.Failed(coreFailure, Duration.ZERO)
    }
}
