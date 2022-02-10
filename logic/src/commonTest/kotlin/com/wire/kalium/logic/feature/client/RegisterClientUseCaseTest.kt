package com.wire.kalium.logic.feature.client

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.client.RegisterClientParam
import com.wire.kalium.logic.data.prekey.PreKey
import com.wire.kalium.logic.failure.ClientFailure
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.anything
import io.mockative.classOf
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

class RegisterClientUseCaseTest {

    @Mock
    private val clientRepository = mock(classOf<ClientRepository>())

    private lateinit var registerClient: RegisterClientUseCase

    @BeforeTest
    fun setup() {
        registerClient = RegisterClientUseCaseImpl(clientRepository)
    }

    @Test
    fun givenRegistrationParams_whenRegistering_thenTheRepositoryShouldBeCalledWithCorrectParameters() = runTest {
        val params = REGISTER_PARAMETERS
        given(clientRepository)
            .suspendFunction(clientRepository::registerClient)
            .whenInvokedWith(anything())
            .then { Either.Left(CoreFailure.ServerMiscommunication) }

        registerClient(params)

        verify(clientRepository)
            .suspendFunction(clientRepository::registerClient)
            .with(eq(params))
            .wasInvoked(once)
    }

    @Test
    fun givenRepositoryRegistrationFailsDueToWrongPassword_whenRegistering_thenInvalidCredentialsErrorShouldBeReturned() = runTest {
        val wrongPasswordFailure = ClientFailure.WrongPassword
        given(clientRepository)
            .suspendFunction(clientRepository::registerClient)
            .whenInvokedWith(anything())
            .then { Either.Left(wrongPasswordFailure) }

        val result = registerClient(REGISTER_PARAMETERS)

        assertIs<RegisterClientResult.Failure.InvalidCredentials>(result)
    }

    @Test
    fun givenRepositoryRegistrationFailsDueToGenericError_whenRegistering_thenGenericErrorShouldBeReturned() = runTest {
        val genericFailure = CoreFailure.NoNetworkConnection
        given(clientRepository)
            .suspendFunction(clientRepository::registerClient)
            .whenInvokedWith(anything())
            .then { Either.Left(genericFailure) }

        val result = registerClient(REGISTER_PARAMETERS)

        assertIs<RegisterClientResult.Failure.Generic>(result)
        assertSame(genericFailure, result.genericFailure)
    }

    @Test
    fun givenRepositoryRegistrationFailsDueToTooManyClientsRegistered_whenRegistering_thenTooManyClientsErrorShouldBeReturned() = runTest {
        val tooManyClientsFailure = ClientFailure.TooManyClients
        given(clientRepository)
            .suspendFunction(clientRepository::registerClient)
            .whenInvokedWith(anything())
            .then { Either.Left(tooManyClientsFailure) }

        val result = registerClient(REGISTER_PARAMETERS)

        assertIs<RegisterClientResult.Failure.TooManyClients>(result)
    }

    @Test
    fun givenRepositoryRegistrationFails_whenRegistering_thenNoPersistenceShouldBeDone() = runTest {
        given(clientRepository)
            .suspendFunction(clientRepository::registerClient)
            .whenInvokedWith(anything())
            .then { Either.Left(CoreFailure.ServerMiscommunication) }

        registerClient(REGISTER_PARAMETERS)

        verify(clientRepository)
            .suspendFunction(clientRepository::persistClientId)
            .with(anything())
            .wasNotInvoked()
    }

    @Test
    fun givenRegisteringSucceeds_whenRegistering_thenThePersistenceShouldBeCalledWithCorrectId() = runTest {
        val registeredClient = CLIENT
        given(clientRepository)
            .suspendFunction(clientRepository::registerClient)
            .whenInvokedWith(anything())
            .then { Either.Right(registeredClient) }

        given(clientRepository)
            .suspendFunction(clientRepository::persistClientId)
            .whenInvokedWith(anything())
            .then { Either.Right(Unit) }

        registerClient(REGISTER_PARAMETERS)

        verify(clientRepository)
            .suspendFunction(clientRepository::persistClientId)
            .with(eq(registeredClient.clientId))
            .wasInvoked(once)
    }

    @Test
    fun givenRegisteringSucceedsAndPersistingClientIdFails_whenRegistering_thenTheFailureShouldBePropagated() = runTest {
        given(clientRepository)
            .suspendFunction(clientRepository::registerClient)
            .whenInvokedWith(anything())
            .then { Either.Right(CLIENT) }

        val persistFailure = CoreFailure.ServerMiscommunication
        given(clientRepository)
            .suspendFunction(clientRepository::persistClientId)
            .whenInvokedWith(anything())
            .then { Either.Left(persistFailure) }

        val result = registerClient(REGISTER_PARAMETERS)

        assertIs<RegisterClientResult.Failure.Generic>(result)
        assertEquals(persistFailure, result.genericFailure)
    }

    @Test
    fun givenRegisteringSucceedsAndPersistingClientIdSucceeds_whenRegistering_thenSuccessShouldBePropagated() = runTest {
        val registeredClient = CLIENT
        given(clientRepository)
            .suspendFunction(clientRepository::registerClient)
            .whenInvokedWith(anything())
            .then { Either.Right(CLIENT) }

        given(clientRepository)
            .suspendFunction(clientRepository::persistClientId)
            .whenInvokedWith(anything())
            .then { Either.Right(Unit) }

        val result = registerClient(REGISTER_PARAMETERS)

        assertIs<RegisterClientResult.Success>(result)
        assertEquals(registeredClient, result.client)
    }

    private companion object {
        val REGISTER_PARAMETERS = RegisterClientParam("pass", listOf(), PreKey(2, "42"), null)
        val CLIENT = TestClient.CLIENT
    }
}
