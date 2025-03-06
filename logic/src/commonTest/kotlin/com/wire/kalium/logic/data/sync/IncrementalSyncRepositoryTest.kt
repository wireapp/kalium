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

package com.wire.kalium.logic.data.sync

import app.cash.turbine.test
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.common.functional.Either
import io.mockative.Mock
import io.mockative.coEvery
import io.mockative.mock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toCollection
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class IncrementalSyncRepositoryTest {

    private lateinit var incrementalSyncRepository: IncrementalSyncRepository

    @Mock
    val sessionRepository = mock(SessionRepository::class)

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
            IncrementalSyncStatus.Failed(NetworkFailure.NoNetworkConnection(null), Duration.ZERO),
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
    fun givenStateIsUpdatedWithRepeatedValue_whenCollectingSyncState_thenShouldNotCollectRepeatedValues() = runTest {
        incrementalSyncRepository.incrementalSyncState.test {
            awaitItem() // Ignore initial value

            val firstUpdate = IncrementalSyncStatus.FetchingPendingEvents
            incrementalSyncRepository.updateIncrementalSyncState(firstUpdate)
            assertEquals(firstUpdate, awaitItem())

            // Repeat update
            incrementalSyncRepository.updateIncrementalSyncState(firstUpdate)

            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenNoStateUpdate_whenCollectingSyncState_thenShouldEmitPendingByDefault() = runTest {
        val initialValue = IncrementalSyncStatus.Pending

        val state = incrementalSyncRepository.incrementalSyncState.first()

        assertEquals(initialValue, state)
    }

    @Test
    fun givenASlowStateCollector_whenStateIsUpdatedManyTimes_thenUpdateEmissionShouldNotBeBlockedByOverflownBuffer() = runTest {
        val updateCount = 10_000
        withContext(Dispatchers.Default) {
            val slowCollectionJob = launch {
                incrementalSyncRepository.incrementalSyncState.collect {
                    delay(1.days)
                }
            }
            val updateStateJob = launch {
                repeat(updateCount) {
                    incrementalSyncRepository.updateIncrementalSyncState(IncrementalSyncStatus.Live)
                }
            }
            withTimeout(10.seconds) {
                // The update job shouldn't take more than a few milliseconds.
                // If it ever waits for more than 10 seconds, something is definitely wrong,
                // and it's probably blocked by the slow collection, which is the whole point of this test NOT happen.
                // So we fail the test if it takes too long (10 seconds).
                updateStateJob.join()
            }
            // Cancel the slow collector as we already know we're able to emit all updates without being blocked.
            slowCollectionJob.cancel()
        }
    }
}
