package com.wire.kalium.logic.data.sync

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SyncRepositoryTest {

    private lateinit var syncRepository: SyncRepository

    @BeforeTest
    fun setup() {
        syncRepository = InMemorySyncRepository()
    }

    @Test
    fun givenNoChanges_whenGettingTheCurrentSyncState_thenTheResultShouldBeWaiting() = runTest {
        //Given

        //When
        val result = syncRepository.syncState.first()

        //Then
        assertEquals(SyncState.WAITING, result)
    }

    @Test
    fun givenStateIsUpdated_whenGettingTheCurrentSyncState_thenTheResultIsTheUpdatedState() = runTest {
        //Given
        val updatedState = SyncState.LIVE
        syncRepository.updateSyncState { updatedState }

        //When
        val result = syncRepository.syncState.first()

        //Then
        assertEquals(updatedState, result)
    }

    @Test
    fun givenAState_whenUpdatingTheCurrentSyncState_thenTheCurrentStateIsAvailableInTheLambda() = runTest {
        //Given
        val currentState = SyncState.LIVE
        syncRepository.updateSyncState { currentState }

        //When
        var capturedState: SyncState? = null
        val result = syncRepository.updateSyncState {
            capturedState = it
            SyncState.WAITING
        }

        //Then
        assertEquals(currentState, capturedState)
    }
}
