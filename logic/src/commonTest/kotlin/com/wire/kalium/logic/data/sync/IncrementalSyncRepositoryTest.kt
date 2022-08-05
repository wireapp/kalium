package com.wire.kalium.logic.data.sync

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class IncrementalSyncRepositoryTest {

    private lateinit var incrementalSyncRepository: IncrementalSyncRepository

    @BeforeTest
    fun setup() {
        incrementalSyncRepository = InMemoryIncrementalSyncRepository()
    }

    @Test
    fun givenNoChanges_whenGettingTheCurrentSyncState_thenTheResultShouldBeWaiting() = runTest {
        // Given

        // When
        val result = incrementalSyncRepository.incrementalSyncState.first()

        // Then
        assertEquals(IncrementalSyncStatus.Pending, result)
    }

    @Test
    fun givenStateIsUpdated_whenGettingTheCurrentSyncState_thenTheResultIsTheUpdatedState() = runTest {
        // Given
        val updatedState = IncrementalSyncStatus.Live
        incrementalSyncRepository.updateIncrementalSyncState(updatedState)

        // When
        val result = incrementalSyncRepository.incrementalSyncState.first()

        // Then
        assertEquals(updatedState, result)
    }

}
