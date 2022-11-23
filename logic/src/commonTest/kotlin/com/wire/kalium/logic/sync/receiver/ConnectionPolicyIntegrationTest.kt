package com.wire.kalium.logic.sync.receiver

import app.cash.turbine.test
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.sync.ConnectionPolicy
import com.wire.kalium.logic.data.sync.InMemoryIncrementalSyncRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.auth.PersistentWebSocketStatus
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.sync.SetConnectionPolicyUseCase
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ConnectionPolicyIntegrationTest {

    @Mock
    val sessionRepository = mock(classOf<SessionRepository>())

    private val incrementalSyncRepository: IncrementalSyncRepository = InMemoryIncrementalSyncRepository(sessionRepository)

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

        given(sessionRepository)
            .suspendFunction(sessionRepository::getAllValidAccountPersistentWebSocketStatus).whenInvoked().then {
                flowOf(listOf())
            }


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
