package com.wire.kalium.logic.feature.client

import com.wire.kalium.cryptography.PreKeyCrypto
import com.wire.kalium.cryptography.ProteusClient
import com.wire.kalium.cryptography.exceptions.ProteusException
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.client.ClientCapability
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.client.RegisterClientParam
import com.wire.kalium.logic.failure.ClientFailure
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.network.exceptions.KaliumException
import io.ktor.utils.io.errors.IOException
import io.mockative.Mock
import io.mockative.any
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

    @Mock
    private val proteusClient = mock(classOf<ProteusClient>())

    private lateinit var registerClient: RegisterClientUseCase

    @BeforeTest
    fun setup() {
        registerClient = RegisterClientUseCaseImpl(clientRepository, proteusClient)

        given(proteusClient)
            .suspendFunction(proteusClient::newPreKeys)
            .whenInvokedWith(any(), any())
            .then { _, _ -> PRE_KEYS }

        given(proteusClient)
            .function(proteusClient::newLastPreKey)
            .whenInvoked()
            .then { LAST_KEY }

    }

    @Test
    fun givenRegistrationParams_whenRegistering_thenTheRepositoryShouldBeCalledWithCorrectParameters() = runTest {
        val params = REGISTER_PARAMETERS
        given(clientRepository)
            .suspendFunction(clientRepository::registerClient)
            .whenInvokedWith(anything())
            .then { Either.Left(TEST_FAILURE) }

        registerClient(TEST_PASSWORD, TEST_CAPABILITIES)

        verify(clientRepository)
            .suspendFunction(clientRepository::registerClient)
            .with(eq(params))
            .wasInvoked(once)

        verify(proteusClient)
            .suspendFunction(proteusClient::newPreKeys)
            .with(any(), any())
            .wasInvoked(exactly = once)

        verify(proteusClient)
            .function(proteusClient::newLastPreKey)
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenRepositoryRegistrationFailsDueToWrongPassword_whenRegistering_thenInvalidCredentialsErrorShouldBeReturned() = runTest {
        val wrongPasswordFailure = ClientFailure.WrongPassword
        given(clientRepository)
            .suspendFunction(clientRepository::registerClient)
            .whenInvokedWith(anything())
            .then { Either.Left(wrongPasswordFailure) }

        val result = registerClient(TEST_PASSWORD, TEST_CAPABILITIES)

        assertIs<RegisterClientResult.Failure.InvalidCredentials>(result)

        verify(proteusClient)
            .suspendFunction(proteusClient::newPreKeys)
            .with(any(), any())
            .wasInvoked(exactly = once)

        verify(proteusClient)
            .function(proteusClient::newLastPreKey)
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenRepositoryRegistrationFailsDueToGenericError_whenRegistering_thenGenericErrorShouldBeReturned() = runTest {
        val genericFailure = TEST_FAILURE
        given(clientRepository)
            .suspendFunction(clientRepository::registerClient)
            .whenInvokedWith(anything())
            .then { Either.Left(genericFailure) }

        val result = registerClient(TEST_PASSWORD, TEST_CAPABILITIES)

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

        val result = registerClient(TEST_PASSWORD, TEST_CAPABILITIES)

        assertIs<RegisterClientResult.Failure.TooManyClients>(result)
    }

    @Test
    fun givenRepositoryRegistrationFails_whenRegistering_thenNoPersistenceShouldBeDone() = runTest {
        given(clientRepository)
            .suspendFunction(clientRepository::registerClient)
            .whenInvokedWith(anything())
            .then { Either.Left(TEST_FAILURE) }

        registerClient(TEST_PASSWORD, TEST_CAPABILITIES)

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

        registerClient(TEST_PASSWORD, TEST_CAPABILITIES)

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

        val persistFailure = TEST_FAILURE
        given(clientRepository)
            .suspendFunction(clientRepository::persistClientId)
            .whenInvokedWith(anything())
            .then { Either.Left(persistFailure) }

        val result = registerClient(TEST_PASSWORD, TEST_CAPABILITIES)

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

        val result = registerClient(TEST_PASSWORD, TEST_CAPABILITIES)

        assertIs<RegisterClientResult.Success>(result)
        assertEquals(registeredClient, result.client)
    }

    @Test
    fun givenProteusClient_whenNewPreKeysThrowException_thenReturnProteusFailure() = runTest {
        val exception = ProteusException("why are we still here just to suffer", 55)
        given(proteusClient)
            .suspendFunction(proteusClient::newPreKeys)
            .whenInvokedWith(any(), any())
            .thenThrow(exception)

        val result = registerClient(TEST_PASSWORD, TEST_CAPABILITIES)

        assertIs<RegisterClientResult.Failure.ProteusFailure>(result)
        assertEquals(exception, result.e)
    }


    @Test
    fun givenProteusClient_whenNewLastPreKeyThrowException_thenReturnProteusFailure() = runTest {
        val exception = ProteusException("why are we still here just to suffer", 55)

        given(proteusClient)
            .function(proteusClient::newLastPreKey)
            .whenInvoked()
            .thenThrow(exception)

        val result = registerClient(TEST_PASSWORD, TEST_CAPABILITIES)

        assertIs<RegisterClientResult.Failure.ProteusFailure>(result)
        assertEquals(exception, result.e)
    }


    private companion object {
        const val TEST_PASSWORD = "password"
        val TEST_CAPABILITIES: List<ClientCapability>? = listOf(
            ClientCapability.LegalHoldImplicitConsent
        )

        val PRE_KEYS = listOf(PreKeyCrypto(id = 1, encodedData = "1"), PreKeyCrypto(id = 2, encodedData = "2"))
        val LAST_KEY = PreKeyCrypto(id = 99, encodedData = "99")
        val REGISTER_PARAMETERS = RegisterClientParam(
            password = TEST_PASSWORD,
            preKeys = PRE_KEYS,
            lastKey = LAST_KEY,
            capabilities = TEST_CAPABILITIES
        )
        val CLIENT = TestClient.CLIENT

        val TEST_FAILURE = NetworkFailure.NoNetworkConnection(KaliumException.NetworkUnavailableError(IOException("no internet")))
    }
}
