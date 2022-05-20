package com.wire.kalium.logic.data.sync

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
    fun givenNoChanges_whenGettingTheCurrentSyncState_thenTheResultShouldBeWaiting() {
        //Given

        //When
        val result = syncRepository.syncState.value

        //Then
        assertEquals(SyncState.WAITING, result)
    }

    @Test
    fun givenStateIsUpdated_whenGettingTheCurrentSyncState_thenTheResultIsTheUpdatedState() {
        //Given
        val updatedState = SyncState.COMPLETED
        syncRepository.updateSyncState { updatedState }

        //When
        val result = syncRepository.syncState.value

        //Then
        assertEquals(updatedState, result)
    }

    @Test
    fun givenAState_whenUpdatingTheCurrentSyncState_thenTheCurrentStateIsAvailableInTheLambda() {
        //Given
        val currentState = SyncState.COMPLETED
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
