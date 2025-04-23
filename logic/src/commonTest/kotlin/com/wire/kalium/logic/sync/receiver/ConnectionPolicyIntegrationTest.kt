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

package com.wire.kalium.logic.sync.receiver

import app.cash.turbine.test
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.sync.ConnectionPolicy
import com.wire.kalium.logic.data.sync.InMemoryIncrementalSyncRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncRepository
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.sync.SetConnectionPolicyUseCase
import io.mockative.coEvery
import io.mockative.mock
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ConnectionPolicyIntegrationTest {

    val sessionRepository = mock(SessionRepository::class)

    private val incrementalSyncRepository: IncrementalSyncRepository = InMemoryIncrementalSyncRepository()

    private val setConnectionPolicyUseCase = SetConnectionPolicyUseCase(incrementalSyncRepository)

    @Test
    fun givenSetConnectionPolicyUseCaseWasNotCalled_whenObservingConnectionPolicy_thenTheDefaultValueIsKEEP_ALIVE() = runTest {
        // Empty Given

        // When
        incrementalSyncRepository.connectionPolicyState.test {
            // Then
            assertEquals(ConnectionPolicy.KEEP_ALIVE, awaitItem())
        }
    }

    @Test
    fun givenSetConnectionPolicyIsCalled_whenObservingConnectionPolicy_thenTheValueIsUpdated() = runTest {
        // Given
        coEvery {
            sessionRepository.getAllValidAccountPersistentWebSocketStatus()
        }.returns(Either.Right(flowOf(listOf())))

        setConnectionPolicyUseCase(ConnectionPolicy.DISCONNECT_AFTER_PENDING_EVENTS)

        // When
        incrementalSyncRepository.connectionPolicyState.test {
            // Then
            assertEquals(ConnectionPolicy.DISCONNECT_AFTER_PENDING_EVENTS, awaitItem())

            setConnectionPolicyUseCase(ConnectionPolicy.KEEP_ALIVE)
            assertEquals(ConnectionPolicy.KEEP_ALIVE, awaitItem())

            setConnectionPolicyUseCase(ConnectionPolicy.DISCONNECT_AFTER_PENDING_EVENTS)
            assertEquals(ConnectionPolicy.DISCONNECT_AFTER_PENDING_EVENTS, awaitItem())
        }
    }
}
