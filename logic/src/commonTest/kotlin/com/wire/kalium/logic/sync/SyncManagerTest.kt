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

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.sync.InMemoryIncrementalSyncRepository
import com.wire.kalium.logic.data.sync.SlowSyncRepositoryImpl
import com.wire.kalium.logic.data.sync.IncrementalSyncRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncStatus
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.data.sync.SlowSyncStatus
import com.wire.kalium.logic.data.sync.SlowSyncStep
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.persistence.TestUserDatabase
import com.wire.kalium.persistence.dao.UserIDEntity
import io.mockative.Mock
import io.mockative.mock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SyncManagerTest {

    @Test
    fun givenSlowSyncFailed_whenWaitingUntilLiveOrFailure_thenShouldReturnFailure() = runTest {
        val (arrangement, syncManager) = Arrangement().arrange()
        arrangement.slowSyncRepository.updateSlowSyncStatus(SlowSyncStatus.Failed(CoreFailure.MissingClientRegistration))
        arrangement.incrementalSyncRepository.updateIncrementalSyncState(IncrementalSyncStatus.Pending)

        val result = syncManager.waitUntilLiveOrFailure()

        result.shouldFail()
    }

    @Test
    fun givenIncrementalSyncFailedAndSlowSyncIsComplete_whenWaitingUntilLiveOrFailure_thenShouldReturnFailure() = runTest {
        val (arrangement, syncManager) = Arrangement().arrange()
        arrangement.slowSyncRepository.updateSlowSyncStatus(SlowSyncStatus.Complete)
        val failedState = IncrementalSyncStatus.Failed(CoreFailure.MissingClientRegistration)
        arrangement.incrementalSyncRepository.updateIncrementalSyncState(failedState)

        val result = syncManager.waitUntilLiveOrFailure()

        result.shouldFail()
    }

    @Test
    fun givenSlowSyncIsBeingPerformedAndFails_whenWaitingUntilLiveOrFailure_thenShouldWaitAndThenFail() = runTest {
        val (arrangement, syncManager) = Arrangement().arrange()
        arrangement.slowSyncRepository.updateSlowSyncStatus(SlowSyncStatus.Ongoing(SlowSyncStep.CONNECTIONS))
        arrangement.incrementalSyncRepository.updateIncrementalSyncState(IncrementalSyncStatus.Pending)

        val result = async {
            syncManager.waitUntilLiveOrFailure()
        }
        advanceUntilIdle()
        assertTrue { result.isActive }

        arrangement.slowSyncRepository.updateSlowSyncStatus(SlowSyncStatus.Failed(CoreFailure.MissingClientRegistration))
        advanceUntilIdle()
        result.await().shouldFail()
    }

    @Test
    fun givenSlowSyncIsBeingPerformedAndSucceedsButIncrementalFails_whenWaitingUntilLiveOrFailure_thenShouldWaitAndThenFail() = runTest {
        val (arrangement, syncManager) = Arrangement().arrange()
        arrangement.slowSyncRepository.updateSlowSyncStatus(SlowSyncStatus.Ongoing(SlowSyncStep.CONNECTIONS))
        arrangement.incrementalSyncRepository.updateIncrementalSyncState(IncrementalSyncStatus.Pending)

        val result = async {
            syncManager.waitUntilLiveOrFailure()
        }
        advanceUntilIdle()
        assertTrue { result.isActive }

        arrangement.slowSyncRepository.updateSlowSyncStatus(SlowSyncStatus.Complete)
        val failure = IncrementalSyncStatus.Failed(CoreFailure.MissingClientRegistration)
        arrangement.incrementalSyncRepository.updateIncrementalSyncState(failure)
        advanceUntilIdle()
        result.await().shouldFail()
    }

    @Test
    fun givenSlowSyncIsCompleteAndIncrementalSyncIsOngoing_whenWaitingUntilLiveOrFailure_thenShouldWaitUntilCompleteReturnSucceed() =
        runTest {
            val (arrangement, syncManager) = Arrangement().arrange()
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
            val (arrangement, syncManager) = Arrangement().arrange()
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

            val failure = IncrementalSyncStatus.Failed(CoreFailure.MissingClientRegistration)
            arrangement.incrementalSyncRepository.updateIncrementalSyncState(failure)
            advanceUntilIdle()
            assertTrue { result.isCompleted }

            result.await().shouldFail()
        }

    @Test
    fun givenSlowSyncFailed_whenWaitingUntilStartedOrFailure_thenShouldReturnFailure() = runTest {
        val (arrangement, syncManager) = Arrangement().arrange()
        arrangement.slowSyncRepository.updateSlowSyncStatus(SlowSyncStatus.Failed(CoreFailure.MissingClientRegistration))

        val result = syncManager.waitUntilStartedOrFailure()

        result.shouldFail()
    }

    @Test
    fun givenSlowSyncOngoing_whenWaitingUntilStartedOrFailure_thenShouldReturnSuccess() = runTest {
        val (arrangement, syncManager) = Arrangement().arrange()
        arrangement.slowSyncRepository.updateSlowSyncStatus(SlowSyncStatus.Ongoing(SlowSyncStep.CONNECTIONS))

        val result = syncManager.waitUntilStartedOrFailure()

        result.shouldSucceed()
    }

    @Test
    fun givenSlowSyncComplete_whenWaitingUntilStartedOrFailure_thenShouldReturnSuccess() = runTest {
        val (arrangement, syncManager) = Arrangement().arrange()
        arrangement.slowSyncRepository.updateSlowSyncStatus(SlowSyncStatus.Complete)

        val result = syncManager.waitUntilStartedOrFailure()

        result.shouldSucceed()
    }

    @Test
    fun givenSlowSyncRepositoryReturnsOngoingState_whenCallingIsSlowSyncOngoing_thenReturnTrue() = runTest {
        val (arrangement, syncManager) = Arrangement().arrange()
        arrangement.slowSyncRepository.updateSlowSyncStatus(SlowSyncStatus.Ongoing(SlowSyncStep.CONNECTIONS))

        val result = syncManager.isSlowSyncOngoing()

        assertTrue { result }
    }

    @Test
    fun givenSlowSyncRepositoryReturnsDifferentStateThanOngoing_whenCallingIsSlowSyncOngoing_thenReturnFalse() = runTest {
        val (arrangement, syncManager) = Arrangement().arrange()
        arrangement.slowSyncRepository.updateSlowSyncStatus(SlowSyncStatus.Pending)

        val result = syncManager.isSlowSyncOngoing()

        assertFalse { result }
    }

    @Test
    fun givenSlowSyncRepositoryReturnsCompleteState_whenCallingIsSlowSyncCompleted_thenReturnTrue() = runTest {
        val (arrangement, syncManager) = Arrangement().arrange()
        arrangement.slowSyncRepository.updateSlowSyncStatus(SlowSyncStatus.Complete)

        val result = syncManager.isSlowSyncCompleted()

        assertTrue { result }
    }

    @Test
    fun givenSlowSyncRepositoryReturnsDifferentStateThanComplete_whenCallingIsSlowSyncCompleted_thenReturnFalse() = runTest {
        val (arrangement, syncManager) = Arrangement().arrange()
        arrangement.slowSyncRepository.updateSlowSyncStatus(SlowSyncStatus.Pending)

        val result = syncManager.isSlowSyncCompleted()

        assertFalse { result }
    }

    @Suppress("unused")
    private class Arrangement {
        val database = TestUserDatabase(UserIDEntity("SELF_USER", "DOMAIN"))
        val slowSyncRepository: SlowSyncRepository = SlowSyncRepositoryImpl(database.builder.metadataDAO)

        @Mock
        val sessionRepository = mock(SessionRepository::class)

        val incrementalSyncRepository: IncrementalSyncRepository = InMemoryIncrementalSyncRepository()

        private val syncManager = SyncManagerImpl(
            slowSyncRepository, incrementalSyncRepository
        )

        fun arrange() = this to syncManager
    }
}
