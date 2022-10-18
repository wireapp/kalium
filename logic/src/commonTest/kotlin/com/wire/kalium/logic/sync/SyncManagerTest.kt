package com.wire.kalium.logic.sync

import com.wire.kalium.logic.CoreFailure
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
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class SyncManagerTest {

    @Test
    fun givenSlowSyncIsPending_whenWaitingUntilLiveOrFailure_thenShouldReturnFailure() = runTest {
        val (arrangement, syncManager) = Arrangement().arrange()
        arrangement.slowSyncRepository.updateSlowSyncStatus(SlowSyncStatus.Pending)
        arrangement.incrementalSyncRepository.updateIncrementalSyncState(IncrementalSyncStatus.Pending)

        val result = syncManager.waitUntilLiveOrFailure()

        result.shouldFail()
    }

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

    @Suppress("unused")
    private class Arrangement {
        val database = TestUserDatabase(UserIDEntity("SELF_USER", "DOMAIN"))
        val slowSyncRepository: SlowSyncRepository = SlowSyncRepositoryImpl(database.builder.metadataDAO)

        val incrementalSyncRepository: IncrementalSyncRepository = InMemoryIncrementalSyncRepository()

        private val syncManager = SyncManagerImpl(
            slowSyncRepository, incrementalSyncRepository
        )

        fun arrange() = this to syncManager
    }
}
