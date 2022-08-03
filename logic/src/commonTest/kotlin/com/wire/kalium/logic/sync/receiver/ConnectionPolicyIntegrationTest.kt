package com.wire.kalium.logic.sync.receiver

import app.cash.turbine.test
import com.wire.kalium.logic.data.sync.ConnectionPolicy
import com.wire.kalium.logic.data.sync.InMemorySyncRepository
import com.wire.kalium.logic.data.sync.SyncRepository
import com.wire.kalium.logic.sync.SetConnectionPolicyUseCase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ConnectionPolicyIntegrationTest {

    private val syncRepository: SyncRepository = InMemorySyncRepository()

    private val setConnectionPolicyUseCase = SetConnectionPolicyUseCase(syncRepository)

    @Test
    fun givenSetConnectionPolicyUseCaseWasNotCalled_whenObservingConnectionPolicy_thenTheDefaultValueIsKEEP_ALIVE() = runTest {
        // Empty Given

        // When
        syncRepository.connectionPolicyState.test {
            // Then
            assertEquals(ConnectionPolicy.KEEP_ALIVE, awaitItem())
        }
    }

    @Test
    fun givenSetConnectionPolicyIsCalled_whenObservingConnectionPolicy_thenTheValueIsUpdated() = runTest {
        // Given
        setConnectionPolicyUseCase(ConnectionPolicy.DISCONNECT_AFTER_PENDING_EVENTS)

        // When
        syncRepository.connectionPolicyState.test {
            // Then
            assertEquals(ConnectionPolicy.DISCONNECT_AFTER_PENDING_EVENTS, awaitItem())

            setConnectionPolicyUseCase(ConnectionPolicy.KEEP_ALIVE)
            assertEquals(ConnectionPolicy.KEEP_ALIVE, awaitItem())

            setConnectionPolicyUseCase(ConnectionPolicy.DISCONNECT_AFTER_PENDING_EVENTS)
            assertEquals(ConnectionPolicy.DISCONNECT_AFTER_PENDING_EVENTS, awaitItem())
        }
    }

}
