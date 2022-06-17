package com.wire.kalium.logic.sync

import app.cash.turbine.test
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.sync.InMemorySyncRepository
import com.wire.kalium.logic.data.sync.SyncRepository
import com.wire.kalium.logic.data.sync.SyncState
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ObserveSyncStateUseCaseTest {

    private lateinit var syncRepository: SyncRepository
    private lateinit var observeSyncState: ObserveSyncStateUseCase

    @BeforeTest
    fun setup() {
        syncRepository = InMemorySyncRepository()
        observeSyncState = ObserveSyncStateUseCase(syncRepository)
    }

    @Test
    fun givenSyncStateInitialStateAndNoUpdates_whenObservingSyncState_thenTheFlowEmitsOnlyInitialState() = runTest {
        val initialState = SyncState.GatheringPendingEvents
        syncRepository.updateSyncState { initialState }

        observeSyncState().test {
            assertEquals(initialState, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenSyncStateInitialStateAndUpdatesAfterCollecting_whenObservingSyncState_thenTheFlowEmitsAllValues() = runTest {
        val initialState = SyncState.GatheringPendingEvents
        syncRepository.updateSyncState { initialState }

        observeSyncState().test {
            assertEquals(initialState, awaitItem())

            val secondState = SyncState.Live
            syncRepository.updateSyncState { secondState }
            assertEquals(secondState, awaitItem())

            val thirdState = SyncState.Failed(CoreFailure.Unknown(null))
            syncRepository.updateSyncState { thirdState }
            assertEquals(thirdState, awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }
}
