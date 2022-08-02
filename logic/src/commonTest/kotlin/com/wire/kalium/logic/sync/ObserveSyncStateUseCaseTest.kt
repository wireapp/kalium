package com.wire.kalium.logic.sync

import app.cash.turbine.test
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.sync.InMemoryIncrementalSyncRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncRepository
import com.wire.kalium.logic.data.sync.SyncState
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ObserveSyncStateUseCaseTest {

    private lateinit var incrementalSyncRepository: IncrementalSyncRepository
    private lateinit var observeSyncState: ObserveSyncStateUseCase

    @BeforeTest
    fun setup() {
        incrementalSyncRepository = InMemoryIncrementalSyncRepository()
        observeSyncState = ObserveSyncStateUseCase(incrementalSyncRepository)
    }

    @Test
    fun givenSyncStateInitialStateAndNoUpdates_whenObservingSyncState_thenTheFlowEmitsOnlyInitialState() = runTest {
        val initialState = SyncState.GatheringPendingEvents
        incrementalSyncRepository.updateIncrementalSyncState { initialState }

        observeSyncState().test {
            assertEquals(initialState, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenSyncStateInitialStateAndUpdatesAfterCollecting_whenObservingSyncState_thenTheFlowEmitsAllValues() = runTest {
        val initialState = SyncState.GatheringPendingEvents
        incrementalSyncRepository.updateIncrementalSyncState { initialState }

        observeSyncState().test {
            assertEquals(initialState, awaitItem())

            val secondState = SyncState.Live
            incrementalSyncRepository.updateIncrementalSyncState { secondState }
            assertEquals(secondState, awaitItem())

            val thirdState = SyncState.Failed(CoreFailure.Unknown(null))
            incrementalSyncRepository.updateIncrementalSyncState { thirdState }
            assertEquals(thirdState, awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }
}
