/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
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

    @Mock
    val sessionRepository = mock(classOf<SessionRepository>())

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
    fun givenConnectionPolicyIsUpdatedWithRepeatedValue_whenCollectingPolicy_thenShouldNotCollectRepeatedValues() = runTest {

        given(sessionRepository)
            .suspendFunction(sessionRepository::getAllValidAccountPersistentWebSocketStatus).whenInvoked()
            .thenReturn(Either.Right(flowOf(listOf())))

        incrementalSyncRepository.connectionPolicyState.test {
            awaitItem() // Ignore initial value

            val firstUpdate = ConnectionPolicy.DISCONNECT_AFTER_PENDING_EVENTS
            incrementalSyncRepository.setConnectionPolicy(firstUpdate)
            assertEquals(firstUpdate, awaitItem())

            // Repeat update
            incrementalSyncRepository.setConnectionPolicy(firstUpdate)

            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenConnectionPolicyUpdatedMultipleTimes_whenCollectingConnectionPolicy_thenAllUpdatesShouldBeCollected() = runTest {
        given(sessionRepository)
            .suspendFunction(sessionRepository::getAllValidAccountPersistentWebSocketStatus).whenInvoked()
            .thenReturn(Either.Right(flowOf(listOf())))

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
