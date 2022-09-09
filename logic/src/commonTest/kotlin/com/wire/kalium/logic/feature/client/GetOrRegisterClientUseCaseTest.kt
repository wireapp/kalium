@file:OptIn(ConfigurationApi::class)

package com.wire.kalium.logic.feature.client

import com.wire.kalium.cryptography.ProteusClient
import com.wire.kalium.logic.data.client.Client
import com.wire.kalium.logic.data.client.ClientType
import com.wire.kalium.logic.data.conversation.ClientId
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
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
            .withObserveCurrentClientIdResult(flowOf(clientId))
            .withSelfClientsResult(SelfClientsResult.Success(listOf(client)))
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
            .withObserveCurrentClientIdResult(flowOf(clientId))
            .withSelfClientsResult(SelfClientsResult.Success(listOf()))
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
            .withObserveCurrentClientIdResult(flowOf(null))
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
        val observeCurrentClientIdUseCase = mock(classOf<ObserveCurrentClientIdUseCase>())

        @Mock
        val selfClientsUseCase = mock(classOf<SelfClientsUseCase>())

        @Mock
        val registerClientUseCase = mock(classOf<RegisterClientUseCase>())

        @Mock
        val clearClientDataUseCase = configure(mock(classOf<ClearClientDataUseCase>())) { stubsUnitByDefault = true }

        @Mock
        val proteusClient = configure(mock(classOf<ProteusClient>())) { stubsUnitByDefault = true }

        val getOrRegisterClientUseCase: GetOrRegisterClientUseCase = GetOrRegisterClientUseCaseImpl(
            observeCurrentClientIdUseCase, selfClientsUseCase, registerClientUseCase, clearClientDataUseCase, proteusClient,
        )

        fun withObserveCurrentClientIdResult(result: Flow<ClientId?>): Arrangement {
            given(observeCurrentClientIdUseCase)
                .suspendFunction(observeCurrentClientIdUseCase::invoke)
                .whenInvoked()
                .thenReturn(result)
            return this
        }
        fun withSelfClientsResult(result: SelfClientsResult): Arrangement {
            given(selfClientsUseCase)
                .suspendFunction(selfClientsUseCase::invoke)
                .whenInvoked()
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
