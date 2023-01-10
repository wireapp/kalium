
package com.wire.kalium.logic.feature.client

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
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class VerifyExistingClientUseCaseTest {

    @Test
    fun givenRegisteredClientId_whenInvoking_thenReturnSuccess() = runTest {
        val clientId = ClientId("clientId")
        val client = Client(clientId, ClientType.Permanent, "time", null, null, "label", "cookie", null, "model", emptyMap())
        val (_, useCase) = Arrangement()
            .withSelfClientsResult(Either.Right(listOf(client)))
            .arrange()
        val result = useCase.invoke(clientId)
        assertIs<VerifyExistingClientResult.Success>(result)
        assertEquals(client, result.client)
    }

    @Test
    fun givenNotRegisteredClientId_whenInvoking_thenReturnClientNotRegisteredFailure() = runTest {
        val clientId = ClientId("clientId")
        val (arrangement, useCase) = Arrangement()
            .withSelfClientsResult(Either.Right(listOf()))
            .arrange()
        val result = useCase.invoke(clientId)
        assertIs<VerifyExistingClientResult.Failure.ClientNotRegistered>(result)
        verify(arrangement.clientRepository)
            .suspendFunction(arrangement.clientRepository::persistClientId)
            .with(any())
            .wasNotInvoked()
    }

    private class Arrangement {

        @Mock
        val clientRepository = mock(classOf<ClientRepository>())

        val verifyExistingClientUseCase: VerifyExistingClientUseCase = VerifyExistingClientUseCaseImpl(clientRepository)

        fun withSelfClientsResult(result: Either<NetworkFailure, List<Client>>): Arrangement {
            given(clientRepository)
                .suspendFunction(clientRepository::selfListOfClients)
                .whenInvoked()
                .thenReturn(result)
            return this
        }

        fun arrange() = this to verifyExistingClientUseCase
    }
}
