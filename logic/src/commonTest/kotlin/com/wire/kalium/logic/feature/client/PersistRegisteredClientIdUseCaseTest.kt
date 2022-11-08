
package com.wire.kalium.logic.feature.client

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.client.Client
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.client.ClientType
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class PersistRegisteredClientIdUseCaseTest {

    @Test
    fun givenRegisteredClientId_whenPersisting_thenReturnSuccess() = runTest {
        val clientId = ClientId("clientId")
        val client = Client(clientId, ClientType.Permanent, "time", null, null, "label", "cookie", null, "model")
        val (arrangement, useCase) = Arrangement()
            .withSelfClientsResult(Either.Right(listOf(client)))
            .withPersistClientResult(Either.Right(Unit))
            .arrange()
        val result = useCase.invoke(clientId)
        assertIs<PersistRegisteredClientIdResult.Success>(result)
        assertEquals(client, result.client)
        verify(arrangement.clientRepository)
            .suspendFunction(arrangement.clientRepository::persistClientId)
            .with(any())
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenNotRegisteredClientId_whenPersisting_thenReturnClientNotRegisteredFailure() = runTest {
        val clientId = ClientId("clientId")
        val (arrangement, useCase) = Arrangement()
            .withSelfClientsResult(Either.Right(listOf()))
            .arrange()
        val result = useCase.invoke(clientId)
        assertIs<PersistRegisteredClientIdResult.Failure.ClientNotRegistered>(result)
        verify(arrangement.clientRepository)
            .suspendFunction(arrangement.clientRepository::persistClientId)
            .with(any())
            .wasNotInvoked()
    }

    private class Arrangement {

        @Mock
        val clientRepository = mock(classOf<ClientRepository>())

        val persistRegisteredClientIdUseCase: PersistRegisteredClientIdUseCase = PersistRegisteredClientIdUseCaseImpl(clientRepository)

        fun withSelfClientsResult(result: Either<NetworkFailure, List<Client>>): Arrangement {
            given(clientRepository)
                .suspendFunction(clientRepository::selfListOfClients)
                .whenInvoked()
                .thenReturn(result)
            return this
        }
        fun withPersistClientResult(result: Either<CoreFailure, Unit>): Arrangement {
            given(clientRepository)
                .suspendFunction(clientRepository::persistClientId)
                .whenInvokedWith(any())
                .thenReturn(result)
            return this
        }

        fun arrange() = this to persistRegisteredClientIdUseCase
    }
}
