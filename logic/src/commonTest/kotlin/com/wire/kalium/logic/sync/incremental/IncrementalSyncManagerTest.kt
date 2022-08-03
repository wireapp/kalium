package com.wire.kalium.logic.sync.incremental

import com.wire.kalium.logic.data.sync.InMemorySlowSyncRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncRepository
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.data.sync.SlowSyncStatus
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import io.mockative.Mock
import io.mockative.classOf
import io.mockative.configure
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class IncrementalSyncManagerTest {

    @Test
    fun givenSlowSyncIsComplete_whenStartingIncrementalManager_thenShouldStartWorker() = runTest(TestKaliumDispatcher.default) {
        val (arrangement, _) = Arrangement()
            .arrange()
        arrangement.slowSyncRepository.updateSlowSyncStatus(SlowSyncStatus.Complete)

        advanceUntilIdle()

        verify(arrangement.incrementalSyncWorker)
            .suspendFunction(arrangement.incrementalSyncWorker::performIncrementalSync)
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenSlowSyncIsNotComplete_whenStartingIncrementalManager_thenShouldNotStartWorker() = runTest(TestKaliumDispatcher.default) {
        val (arrangement, _) = Arrangement()
            .arrange()
        arrangement.slowSyncRepository.updateSlowSyncStatus(SlowSyncStatus.Pending)

        advanceUntilIdle()

        verify(arrangement.incrementalSyncWorker)
            .suspendFunction(arrangement.incrementalSyncWorker::performIncrementalSync)
            .wasNotInvoked()
    }

    private class Arrangement {

        val slowSyncRepository: SlowSyncRepository = InMemorySlowSyncRepository()

        @Mock
        val incrementalSyncWorker = configure(mock(classOf<IncrementalSyncWorker>())) { stubsUnitByDefault = true }

        @Mock
        val incrementalSyncRepository = mock(classOf<IncrementalSyncRepository>())

        private val incrementalSyncManager = IncrementalSyncManager(
            slowSyncRepository, incrementalSyncWorker, incrementalSyncRepository, TestKaliumDispatcher
        )

        fun arrange() = this to incrementalSyncManager

    }
}
