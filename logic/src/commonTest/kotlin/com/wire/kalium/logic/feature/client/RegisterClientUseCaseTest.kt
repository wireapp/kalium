package com.wire.kalium.logic.feature.client

import com.wire.kalium.cryptography.MLSClient
import com.wire.kalium.cryptography.PreKeyCrypto
import com.wire.kalium.cryptography.exceptions.ProteusException
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.ProteusFailure
import com.wire.kalium.logic.configuration.notification.NotificationTokenRepository
import com.wire.kalium.logic.data.client.ClientCapability
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.client.MLSClientProvider
import com.wire.kalium.logic.data.client.RegisterClientParam
import com.wire.kalium.logic.data.keypackage.KeyPackageRepository
import com.wire.kalium.logic.data.prekey.PreKeyRepository
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.test_util.TestNetworkException
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

@OptIn(ExperimentalCoroutinesApi::class)
class RegisterClientUseCaseTest {

    @Mock
    private val clientRepository = mock(classOf<ClientRepository>())

    @Mock
    private val preKeyRepository = mock(classOf<PreKeyRepository>())

    @Mock
    private val keyPackageRepository = mock(classOf<KeyPackageRepository>())

    @Mock
    private val notificationTokenRepository = mock(classOf<NotificationTokenRepository>())

    @Mock
    private val mlsClientProvider = mock(classOf<MLSClientProvider>())

    private lateinit var registerClient: RegisterClientUseCase

    @BeforeTest
    fun setup() {
        registerClient = RegisterClientUseCaseImpl(
            clientRepository,
            preKeyRepository,
            keyPackageRepository,
            mlsClientProvider,
            notificationTokenRepository
        )

        given(preKeyRepository)
            .suspendFunction(preKeyRepository::generateNewPreKeys)
            .whenInvokedWith(any(), any())
            .then { _, _ -> Either.Right(PRE_KEYS) }

        given(preKeyRepository)
            .function(preKeyRepository::generateNewLastKey)
            .whenInvoked()
            .then { Either.Right(LAST_KEY) }

    }

    @Test
    fun givenRegistrationParams_whenRegistering_thenTheRepositoryShouldBeCalledWithCorrectParameters() = runTest {
        val params = REGISTER_PARAMETERS
        given(clientRepository)
            .suspendFunction(clientRepository::registerClient)
            .whenInvokedWith(anything())
            .then { Either.Left(TEST_FAILURE) }

        registerClient(RegisterClientUseCase.RegisterClientParam.ClientWithoutToken(TEST_PASSWORD, TEST_CAPABILITIES))

        verify(clientRepository)
            .suspendFunction(clientRepository::registerClient)
            .with(eq(params))
            .wasInvoked(once)

        verify(preKeyRepository)
            .suspendFunction(preKeyRepository::generateNewPreKeys)
            .with(any(), any())
            .wasInvoked(exactly = once)

        verify(preKeyRepository)
            .function(preKeyRepository::generateNewLastKey)
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenRepositoryRegistrationFailsDueToWrongPassword_whenRegistering_thenInvalidCredentialsErrorShouldBeReturned() = runTest {
        val wrongPasswordFailure = NetworkFailure.ServerMiscommunication(TestNetworkException.missingAuth)
        given(clientRepository)
            .suspendFunction(clientRepository::registerClient)
            .whenInvokedWith(anything())
            .then { Either.Left(wrongPasswordFailure) }

        val result = registerClient(RegisterClientUseCase.RegisterClientParam.ClientWithoutToken(TEST_PASSWORD, TEST_CAPABILITIES))

        assertIs<RegisterClientResult.Failure.InvalidCredentials>(result)

        verify(preKeyRepository)
            .suspendFunction(preKeyRepository::generateNewPreKeys)
            .with(any(), any())
            .wasInvoked(exactly = once)

        verify(preKeyRepository)
            .function(preKeyRepository::generateNewLastKey)
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenRepositoryRegistrationFailsDueToGenericError_whenRegistering_thenGenericErrorShouldBeReturned() = runTest {
        val genericFailure = TEST_FAILURE
        given(clientRepository)
            .suspendFunction(clientRepository::registerClient)
            .whenInvokedWith(anything())
            .then { Either.Left(genericFailure) }

        val result = registerClient(RegisterClientUseCase.RegisterClientParam.ClientWithoutToken(TEST_PASSWORD, TEST_CAPABILITIES))

        assertIs<RegisterClientResult.Failure.Generic>(result)
        assertSame(genericFailure, result.genericFailure)
    }

    @Test
    fun givenRepositoryRegistrationFailsDueToTooManyClientsRegistered_whenRegistering_thenTooManyClientsErrorShouldBeReturned() = runTest {
        val tooManyClientsFailure = NetworkFailure.ServerMiscommunication(TestNetworkException.tooManyClient)
        given(clientRepository)
            .suspendFunction(clientRepository::registerClient)
            .whenInvokedWith(anything())
            .then { Either.Left(tooManyClientsFailure) }

        val result = registerClient(RegisterClientUseCase.RegisterClientParam.ClientWithoutToken(TEST_PASSWORD, TEST_CAPABILITIES))

        assertIs<RegisterClientResult.Failure.TooManyClients>(result)
    }

    @Test
    fun givenRepositoryRegistrationFails_whenRegistering_thenNoPersistenceShouldBeDone() = runTest {
        given(clientRepository)
            .suspendFunction(clientRepository::registerClient)
            .whenInvokedWith(anything())
            .then { Either.Left(TEST_FAILURE) }

        registerClient(RegisterClientUseCase.RegisterClientParam.ClientWithoutToken(TEST_PASSWORD, TEST_CAPABILITIES))

        verify(clientRepository)
            .function(clientRepository::persistClientId)
            .with(anything())
            .wasNotInvoked()
    }

    @Test
    fun givenMLSClientRegistrationFails_whenRegistering_thenNoPersistenceShouldBeDone() = runTest {
        val registeredClient = CLIENT
        given(clientRepository)
            .suspendFunction(clientRepository::registerClient)
            .whenInvokedWith(anything())
            .then { Either.Right(registeredClient) }

        given(mlsClientProvider)
            .function(mlsClientProvider::getMLSClient)
            .whenInvokedWith(eq(CLIENT.clientId))
            .then { Either.Right(MLS_CLIENT) }

        given(MLS_CLIENT)
            .function(MLS_CLIENT::getPublicKey)
            .whenInvoked()
            .thenReturn(MLS_PUBLIC_KEY)

        given(clientRepository)
            .suspendFunction(clientRepository::registerMLSClient)
            .whenInvokedWith(eq(CLIENT.clientId), eq(MLS_PUBLIC_KEY))
            .thenReturn(Either.Left(TEST_FAILURE))

        registerClient(RegisterClientUseCase.RegisterClientParam.ClientWithoutToken(TEST_PASSWORD, TEST_CAPABILITIES))

        verify(clientRepository)
            .function(clientRepository::persistClientId)
            .with(anything())
            .wasNotInvoked()
    }

    @Test
    fun givenKeyPackageUploadFails_whenRegistering_thenNoPersistenceShouldBeDone() = runTest {
        val registeredClient = CLIENT
        given(clientRepository)
            .suspendFunction(clientRepository::registerClient)
            .whenInvokedWith(anything())
            .then { Either.Right(registeredClient) }

        given(mlsClientProvider)
            .function(mlsClientProvider::getMLSClient)
            .whenInvokedWith(eq(CLIENT.clientId))
            .then { Either.Right(MLS_CLIENT) }

        given(MLS_CLIENT)
            .function(MLS_CLIENT::getPublicKey)
            .whenInvoked()
            .thenReturn(MLS_PUBLIC_KEY)

        given(clientRepository)
            .suspendFunction(clientRepository::registerMLSClient)
            .whenInvokedWith(eq(CLIENT.clientId), eq(MLS_PUBLIC_KEY))
            .thenReturn(Either.Right(Unit))

        given(keyPackageRepository)
            .suspendFunction(keyPackageRepository::uploadNewKeyPackages)
            .whenInvokedWith(anything(), eq(100))
            .thenReturn(Either.Left(TEST_FAILURE))

        registerClient(RegisterClientUseCase.RegisterClientParam.ClientWithoutToken(TEST_PASSWORD, TEST_CAPABILITIES))

        verify(clientRepository)
            .function(clientRepository::persistClientId)
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

        given(mlsClientProvider)
            .function(mlsClientProvider::getMLSClient)
            .whenInvokedWith(eq(CLIENT.clientId))
            .then { Either.Right(MLS_CLIENT) }

        given(MLS_CLIENT)
            .function(MLS_CLIENT::getPublicKey)
            .whenInvoked()
            .thenReturn(MLS_PUBLIC_KEY)

        given(clientRepository)
            .suspendFunction(clientRepository::registerMLSClient)
            .whenInvokedWith(eq(CLIENT.clientId), eq(MLS_PUBLIC_KEY))
            .thenReturn(Either.Right(Unit))

        given(keyPackageRepository)
            .suspendFunction(keyPackageRepository::uploadNewKeyPackages)
            .whenInvokedWith(anything(), eq(100))
            .thenReturn(Either.Right(Unit))

        given(clientRepository)
            .function(clientRepository::persistClientId)
            .whenInvokedWith(anything())
            .then { Either.Right(Unit) }

        registerClient(RegisterClientUseCase.RegisterClientParam.ClientWithoutToken(TEST_PASSWORD, TEST_CAPABILITIES))

        verify(clientRepository)
            .function(clientRepository::persistClientId)
            .with(eq(registeredClient.clientId))
            .wasInvoked(once)
    }

    @Test
    fun givenRegisteringSucceedsAndPersistingClientIdFails_whenRegistering_thenTheFailureShouldBePropagated() = runTest {
        given(clientRepository)
            .suspendFunction(clientRepository::registerClient)
            .whenInvokedWith(anything())
            .then { Either.Right(CLIENT) }

        given(mlsClientProvider)
            .function(mlsClientProvider::getMLSClient)
            .whenInvokedWith(eq(CLIENT.clientId))
            .then { Either.Right(MLS_CLIENT) }

        given(MLS_CLIENT)
            .function(MLS_CLIENT::getPublicKey)
            .whenInvoked()
            .thenReturn(MLS_PUBLIC_KEY)

        given(clientRepository)
            .suspendFunction(clientRepository::registerMLSClient)
            .whenInvokedWith(eq(CLIENT.clientId), eq(MLS_PUBLIC_KEY))
            .thenReturn(Either.Right(Unit))

        given(keyPackageRepository)
            .suspendFunction(keyPackageRepository::uploadNewKeyPackages)
            .whenInvokedWith(anything(), eq(100))
            .thenReturn(Either.Right(Unit))

        val persistFailure = TEST_FAILURE
        given(clientRepository)
            .function(clientRepository::persistClientId)
            .whenInvokedWith(anything())
            .then { Either.Left(persistFailure) }

        val result = registerClient(RegisterClientUseCase.RegisterClientParam.ClientWithoutToken(TEST_PASSWORD, TEST_CAPABILITIES))

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

        given(mlsClientProvider)
            .function(mlsClientProvider::getMLSClient)
            .whenInvokedWith(eq(CLIENT.clientId))
            .then { Either.Right(MLS_CLIENT) }

        given(MLS_CLIENT)
            .function(MLS_CLIENT::getPublicKey)
            .whenInvoked()
            .thenReturn(MLS_PUBLIC_KEY)

        given(clientRepository)
            .suspendFunction(clientRepository::registerMLSClient)
            .whenInvokedWith(eq(CLIENT.clientId), eq(MLS_PUBLIC_KEY))
            .thenReturn(Either.Right(Unit))

        given(keyPackageRepository)
            .suspendFunction(keyPackageRepository::uploadNewKeyPackages)
            .whenInvokedWith(anything(), eq(100))
            .thenReturn(Either.Right(Unit))

        given(clientRepository)
            .function(clientRepository::persistClientId)
            .whenInvokedWith(anything())
            .then { Either.Right(Unit) }

        val result = registerClient(RegisterClientUseCase.RegisterClientParam.ClientWithoutToken(TEST_PASSWORD, TEST_CAPABILITIES))

        assertIs<RegisterClientResult.Success>(result)
        assertEquals(registeredClient, result.client)
    }

    @Test
    fun givenProteusClient_whenNewPreKeysThrowException_thenReturnProteusFailure() = runTest {
        val failure = ProteusFailure(ProteusException("why are we still here just to suffer", 55))
        given(preKeyRepository)
            .suspendFunction(preKeyRepository::generateNewPreKeys)
            .whenInvokedWith(any(), any())
            .then { _, _ -> Either.Left(failure) }

        val result = registerClient(RegisterClientUseCase.RegisterClientParam.ClientWithoutToken(TEST_PASSWORD, TEST_CAPABILITIES))

        assertIs<RegisterClientResult.Failure.Generic>(result)
        assertEquals(failure, result.genericFailure)
    }


    @Test
    fun givenProteusClient_whenNewLastPreKeyThrowException_thenReturnProteusFailure() = runTest {
        val failure = ProteusFailure(ProteusException("why are we still here just to suffer", 55))

        given(preKeyRepository)
            .function(preKeyRepository::generateNewLastKey)
            .whenInvoked()
            .then { Either.Left(failure) }

        val result = registerClient(RegisterClientUseCase.RegisterClientParam.ClientWithoutToken(TEST_PASSWORD, TEST_CAPABILITIES))

        assertIs<RegisterClientResult.Failure.Generic>(result)
        assertEquals(failure, result.genericFailure)
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
            capabilities = TEST_CAPABILITIES,
            deviceType = null,
            label = null,
            model = null
        )
        val CLIENT = TestClient.CLIENT

        @Mock
        val MLS_CLIENT = mock(classOf<MLSClient>())
        val MLS_PUBLIC_KEY = "public_key".encodeToByteArray()
        val TEST_FAILURE = NetworkFailure.ServerMiscommunication(KaliumException.GenericError(IOException("no internet")))
    }
}
