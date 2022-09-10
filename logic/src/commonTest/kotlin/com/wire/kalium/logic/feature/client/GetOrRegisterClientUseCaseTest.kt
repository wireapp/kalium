@file:OptIn(ConfigurationApi::class)

package com.wire.kalium.logic.feature.client

import com.wire.kalium.cryptography.ProteusClient
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.client.Client
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.client.ClientType
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.functional.Either
import io.mockative.ConfigurationApi
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.configure
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
class GetOrRegisterClientUseCaseTest {

    @Test
    fun givenValidClientIsPersisted_whenRegisteringAClient_thenDoNotRegisterNewAndReturnPersistedCLient() = runTest {
        val clientId = ClientId("clientId")
        val client = Client(clientId, ClientType.Permanent, "time", null, null, "label", "cookie", null, "model")
        val (arrangement, useCase) = Arrangement()
            .withRetainedClientIdResult(Either.Right(clientId))
            .withSelfClientsResult(Either.Right(listOf(client)))
            .withPersistClientResult(Either.Right(Unit))
            .arrange()
        val result = useCase.invoke(RegisterClientUseCase.RegisterClientParam("", listOf()))
        assertIs<RegisterClientResult.Success>(result)
        assertEquals(client, result.client)
        verify(arrangement.registerClientUseCase)
            .suspendFunction(arrangement.registerClientUseCase::invoke)
            .with(any())
            .wasNotInvoked()
    }

    @Test
    fun givenInvalidClientIsPersisted_whenRegisteringAClient_thenClearDataAndRegisterNewClient() = runTest {
        val clientId = ClientId("clientId")
        val client = Client(clientId, ClientType.Permanent, "time", null, null, "label", "cookie", null, "model")
        val (arrangement, useCase) = Arrangement()
            .withRetainedClientIdResult(Either.Right(clientId))
            .withSelfClientsResult(Either.Right(listOf()))
            .withRegisterClientResult(RegisterClientResult.Success(client))
            .arrange()
        val result = useCase.invoke(RegisterClientUseCase.RegisterClientParam("", listOf()))
        assertIs<RegisterClientResult.Success>(result)
        assertEquals(client, result.client)
        verify(arrangement.clearClientDataUseCase)
            .suspendFunction(arrangement.clearClientDataUseCase::invoke)
            .wasInvoked(exactly = once)
        verify(arrangement.registerClientUseCase)
            .suspendFunction(arrangement.registerClientUseCase::invoke)
            .with(any())
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenClientNotPersisted_whenRegisteringAClient_thenRegisterNewClient() = runTest {
        val clientId = ClientId("clientId")
        val client = Client(clientId, ClientType.Permanent, "time", null, null, "label", "cookie", null, "model")
        val (arrangement, useCase) = Arrangement()
            .withRetainedClientIdResult(Either.Left(CoreFailure.MissingClientRegistration))
            .withRegisterClientResult(RegisterClientResult.Success(client))
            .arrange()
        val result = useCase.invoke(RegisterClientUseCase.RegisterClientParam("", listOf()))
        assertIs<RegisterClientResult.Success>(result)
        assertEquals(client, result.client)
        verify(arrangement.registerClientUseCase)
            .suspendFunction(arrangement.registerClientUseCase::invoke)
            .with(any())
            .wasInvoked(exactly = once)
    }

    private class Arrangement {

        @Mock
        val clientRepository = mock(classOf<ClientRepository>())

        @Mock
        val registerClientUseCase = mock(classOf<RegisterClientUseCase>())

        @Mock
        val clearClientDataUseCase = configure(mock(classOf<ClearClientDataUseCase>())) { stubsUnitByDefault = true }

        @Mock
        val proteusClient = configure(mock(classOf<ProteusClient>())) { stubsUnitByDefault = true }

        val getOrRegisterClientUseCase: GetOrRegisterClientUseCase = GetOrRegisterClientUseCaseImpl(
            clientRepository, registerClientUseCase, clearClientDataUseCase, proteusClient,
        )

        fun withRetainedClientIdResult(result: Either<CoreFailure, ClientId>): Arrangement {
            given(clientRepository)
                .suspendFunction(clientRepository::retainedClientId)
                .whenInvoked()
                .thenReturn(result)
            return this
        }
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
        fun withRegisterClientResult(result: RegisterClientResult): Arrangement {
            given(registerClientUseCase)
                .suspendFunction(registerClientUseCase::invoke)
                .whenInvokedWith(any())
                .thenReturn(result)
            return this
        }

        fun arrange() = this to getOrRegisterClientUseCase
    }
}
