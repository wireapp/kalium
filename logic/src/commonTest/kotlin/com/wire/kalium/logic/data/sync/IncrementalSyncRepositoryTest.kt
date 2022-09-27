package com.wire.kalium.logic.data.sync

import com.wire.kalium.logic.NetworkFailure
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toCollection
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
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

    @Test
    fun givenStateIsUpdatedMultipleTimes_whenCollectingSyncState_thenAllUpdatesShouldBeCollected() = runTest {
        val updates = listOf(
            IncrementalSyncStatus.FetchingPendingEvents,
            IncrementalSyncStatus.Live,
            IncrementalSyncStatus.Pending,
            IncrementalSyncStatus.Failed(NetworkFailure.NoNetworkConnection(null)),
            IncrementalSyncStatus.FetchingPendingEvents
        )

        val collectedUpdates = mutableListOf<IncrementalSyncStatus>()
        // Start collecting. Collector is slower than emitter
        val collectionJob = launch {
            incrementalSyncRepository.incrementalSyncState
                // Account for initial value
                .take(updates.size + 1)
                .toCollection(collectedUpdates)
            // Remove initial value
            collectedUpdates.removeAt(0)
        }
        launch {
            updates.forEach {
                incrementalSyncRepository.updateIncrementalSyncState(it)
            }
        }
        advanceUntilIdle()
        collectionJob.join()

        assertContentEquals(updates, collectedUpdates)
    }

    @Test
    fun givenConnectionPolicyUpdatedMultipleTimes_whenCollectingConnectionPolicy_thenAllUpdatesShouldBeCollected() = runTest {
        val updates = listOf(
            ConnectionPolicy.DISCONNECT_AFTER_PENDING_EVENTS,
            ConnectionPolicy.KEEP_ALIVE,
            ConnectionPolicy.DISCONNECT_AFTER_PENDING_EVENTS,
            ConnectionPolicy.KEEP_ALIVE,
            ConnectionPolicy.DISCONNECT_AFTER_PENDING_EVENTS,
        )

        val collectedUpdates = mutableListOf<ConnectionPolicy>()
        // Start collecting. Collector is slower than emitter
        val collectionJob = launch {
            incrementalSyncRepository.connectionPolicyState
                // Account for initial value
                .take(updates.size + 1)
                .toCollection(collectedUpdates)
            // Remove initial value
            collectedUpdates.removeAt(0)
        }
        launch {
            updates.forEach {
                incrementalSyncRepository.setConnectionPolicy(it)
            }
        }
        advanceUntilIdle()
        collectionJob.join()

        assertContentEquals(updates, collectedUpdates)
    }

    @Test
    fun givenNoStateUpdate_whenCollectingSyncState_thenShouldEmitPendingByDefault() = runTest {
        val initialValue = IncrementalSyncStatus.Pending

        val state = incrementalSyncRepository.incrementalSyncState.first()

        assertEquals(initialValue, state)
    }

    @Test
    fun givenNoConnectionPolicyUpdate_whenCollectingConnectionPolicy_thenShouldEmitKeepAliveByDefault() = runTest {
        val initialValue = ConnectionPolicy.KEEP_ALIVE

        val state = incrementalSyncRepository.connectionPolicyState.first()

        assertEquals(initialValue, state)
    }

}
