package com.wire.kalium.logic.sync.receiver

import app.cash.turbine.test
import com.wire.kalium.logic.data.sync.ConnectionPolicy
import com.wire.kalium.logic.data.sync.InMemoryIncrementalSyncRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncRepository
import com.wire.kalium.logic.sync.SetConnectionPolicyUseCase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ConnectionPolicyIntegrationTest {

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
