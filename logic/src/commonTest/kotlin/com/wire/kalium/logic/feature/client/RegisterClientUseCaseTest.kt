package com.wire.kalium.logic.feature.client

import com.wire.kalium.cryptography.MLSClient
import com.wire.kalium.cryptography.PreKeyCrypto
import com.wire.kalium.cryptography.exceptions.ProteusException
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.ProteusFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.client.Client
import com.wire.kalium.logic.data.client.ClientCapability
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.client.MLSClientProvider
import com.wire.kalium.logic.data.client.RegisterClientParam
import com.wire.kalium.logic.data.keypackage.KeyPackageLimitsProvider
import com.wire.kalium.logic.data.keypackage.KeyPackageRepository
import com.wire.kalium.logic.data.prekey.PreKeyRepository
import com.wire.kalium.logic.featureFlags.FeatureSupport
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

@OptIn(ExperimentalCoroutinesApi::class)
class RegisterClientUseCaseTest {

    @Test
    fun givenRegistrationParams_whenRegistering_thenTheRepositoryShouldBeCalledWithCorrectParameters() = runTest {
        val params = REGISTER_PARAMETERS

        val (arrangement, registerClient) = Arrangement()
            .withRegisterClient(Either.Left(TEST_FAILURE))
            .arrange()

        registerClient(RegisterClientUseCase.RegisterClientParam(TEST_PASSWORD, TEST_CAPABILITIES))

        verify(arrangement.clientRepository)
            .suspendFunction(arrangement.clientRepository::registerClient)
            .with(eq(params))
            .wasInvoked(once)

        verify(arrangement.preKeyRepository)
            .suspendFunction(arrangement.preKeyRepository::generateNewPreKeys)
            .with(any(), any())
            .wasInvoked(exactly = once)

        verify(arrangement.preKeyRepository)
            .suspendFunction(arrangement.preKeyRepository::generateNewLastKey)
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenRepositoryRegistrationFailsDueMissingPassword_whenRegistering_thenPasswordAuthRequiredErrorShouldBeReturned() = runTest {
        val missingPasswordFailure = NetworkFailure.ServerMiscommunication(TestNetworkException.missingAuth)

        val (arrangement, registerClient) = Arrangement()
            .withRegisterClient(Either.Left(missingPasswordFailure))
            .arrange()

        val result = registerClient(RegisterClientUseCase.RegisterClientParam(TEST_PASSWORD, TEST_CAPABILITIES))

        assertIs<RegisterClientResult.Failure.PasswordAuthRequired>(result)

        verify(arrangement.preKeyRepository)
            .suspendFunction(arrangement.preKeyRepository::generateNewPreKeys)
            .with(any(), any())
            .wasInvoked(exactly = once)

        verify(arrangement.preKeyRepository)
            .suspendFunction(arrangement.preKeyRepository::generateNewLastKey)
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenRepositoryRegistrationFailsDueInvalidPassword_whenRegistering_thenInvalidCredentialsErrorShouldBeReturned() = runTest {
        val wrongPasswordFailure = NetworkFailure.ServerMiscommunication(TestNetworkException.invalidCredentials)

        val (arrangement, registerClient) = Arrangement()
            .withRegisterClient(Either.Left(wrongPasswordFailure))
            .arrange()

        val result = registerClient(RegisterClientUseCase.RegisterClientParam(TEST_PASSWORD, TEST_CAPABILITIES))

        assertIs<RegisterClientResult.Failure.InvalidCredentials>(result)

        verify(arrangement.preKeyRepository)
            .suspendFunction(arrangement.preKeyRepository::generateNewPreKeys)
            .with(any(), any())
            .wasInvoked(exactly = once)

        verify(arrangement.preKeyRepository)
            .suspendFunction(arrangement.preKeyRepository::generateNewLastKey)
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenRepositoryRegistrationFailsDueToGenericError_whenRegistering_thenGenericErrorShouldBeReturned() = runTest {
        val genericFailure = TEST_FAILURE

        val (arrangement, registerClient) = Arrangement()
            .withRegisterClient(Either.Left(genericFailure))
            .arrange()

        val result = registerClient(RegisterClientUseCase.RegisterClientParam(TEST_PASSWORD, TEST_CAPABILITIES))

        assertIs<RegisterClientResult.Failure.Generic>(result)
        assertSame(genericFailure, result.genericFailure)
    }

    @Test
    fun givenRepositoryRegistrationFailsDueToTooManyClientsRegistered_whenRegistering_thenTooManyClientsErrorShouldBeReturned() = runTest {
        val tooManyClientsFailure = NetworkFailure.ServerMiscommunication(TestNetworkException.tooManyClient)

        val (arrangement, registerClient) = Arrangement()
            .withRegisterClient(Either.Left(tooManyClientsFailure))
            .arrange()

        val result = registerClient(RegisterClientUseCase.RegisterClientParam(TEST_PASSWORD, TEST_CAPABILITIES))

        assertIs<RegisterClientResult.Failure.TooManyClients>(result)
    }

    @Test
    fun givenRepositoryRegistrationFails_whenRegistering_thenNoPersistenceShouldBeDone() = runTest {

        val (arrangement, registerClient) = Arrangement()
            .withRegisterClient(Either.Left(TEST_FAILURE))
            .arrange()

        registerClient(RegisterClientUseCase.RegisterClientParam(TEST_PASSWORD, TEST_CAPABILITIES))

        verify(arrangement.clientRepository)
            .suspendFunction(arrangement.clientRepository::persistClientId)
            .with(anything())
            .wasNotInvoked()
    }

    @Test
    fun givenMLSClientRegistrationFails_whenRegistering_thenNoPersistenceShouldBeDone() = runTest {
        val registeredClient = CLIENT

        val (arrangement, registerClient) = Arrangement()
            .withRegisterClient(Either.Right(registeredClient))
            .withMLSClient(Either.Right(MLS_CLIENT))
            .withGetMLSPublicKey(MLS_PUBLIC_KEY)
            .withRegisterMLSClient(Either.Left(TEST_FAILURE))
            .arrange()

        registerClient(RegisterClientUseCase.RegisterClientParam(TEST_PASSWORD, TEST_CAPABILITIES))

        verify(arrangement.clientRepository)
            .suspendFunction(arrangement.clientRepository::persistClientId)
            .with(anything())
            .wasNotInvoked()
    }

    @Test
    fun givenKeyPackageUploadFails_whenRegistering_thenNoPersistenceShouldBeDone() = runTest {
        val registeredClient = CLIENT

        val (arrangement, registerClient) = Arrangement()
            .withRegisterClient(Either.Right(registeredClient))
            .withMLSClient(Either.Right(MLS_CLIENT))
            .withGetMLSPublicKey(MLS_PUBLIC_KEY)
            .withRegisterMLSClient(Either.Right(Unit))
            .withUploadNewKeyPackages(Either.Left(TEST_FAILURE))
            .arrange()

        registerClient(RegisterClientUseCase.RegisterClientParam(TEST_PASSWORD, TEST_CAPABILITIES))

        verify(arrangement.clientRepository)
            .suspendFunction(arrangement.clientRepository::persistClientId)
            .with(anything())
            .wasNotInvoked()
    }

    @Test
    fun givenRegisteringSucceeds_whenRegistering_thenThePersistenceShouldBeCalledWithCorrectId() = runTest {
        val registeredClient = CLIENT

        val (arrangement, registerClient) = Arrangement()
            .withRegisterClient(Either.Right(registeredClient))
            .withMLSClient(Either.Right(MLS_CLIENT))
            .withGetMLSPublicKey(MLS_PUBLIC_KEY)
            .withRegisterMLSClient(Either.Right(Unit))
            .withUploadNewKeyPackages(Either.Right(Unit))
            .withPersistClientId(Either.Right(Unit))
            .withUpdateOTRLastPreKeyId(Either.Right(Unit))
            .arrange()

        registerClient(RegisterClientUseCase.RegisterClientParam(TEST_PASSWORD, TEST_CAPABILITIES))

        verify(arrangement.clientRepository)
            .suspendFunction(arrangement.clientRepository::persistClientId)
            .with(eq(registeredClient.id))
            .wasInvoked(once)
    }

    @Test
    fun givenRegisteringSucceedsAndPersistingClientIdFails_whenRegistering_thenTheFailureShouldBePropagated() = runTest {
        val persistFailure = TEST_FAILURE

        val (arrangement, registerClient) = Arrangement()
            .withRegisterClient(Either.Right(CLIENT))
            .withMLSClient(Either.Right(MLS_CLIENT))
            .withGetMLSPublicKey(MLS_PUBLIC_KEY)
            .withRegisterMLSClient(Either.Right(Unit))
            .withUploadNewKeyPackages(Either.Right(Unit))
            .withPersistClientId(Either.Left(persistFailure))
            .arrange()

        val result = registerClient(RegisterClientUseCase.RegisterClientParam(TEST_PASSWORD, TEST_CAPABILITIES))

        assertIs<RegisterClientResult.Failure.Generic>(result)
        assertEquals(persistFailure, result.genericFailure)
    }

    @Test
    fun givenRegisteringSucceedsAndPersistingClientIdSucceeds_whenRegistering_thenSuccessShouldBePropagated() = runTest {
        val registeredClient = CLIENT

        val (arrangement, registerClient) = Arrangement()
            .withRegisterClient(Either.Right(registeredClient))
            .withMLSClient(Either.Right(MLS_CLIENT))
            .withGetMLSPublicKey(MLS_PUBLIC_KEY)
            .withRegisterMLSClient(Either.Right(Unit))
            .withUploadNewKeyPackages(Either.Right(Unit))
            .withPersistClientId(Either.Right(Unit))
            .withUpdateOTRLastPreKeyId(Either.Right(Unit))
            .arrange()

        val result = registerClient(RegisterClientUseCase.RegisterClientParam(TEST_PASSWORD, TEST_CAPABILITIES))

        assertIs<RegisterClientResult.Success>(result)
        assertEquals(registeredClient, result.client)
    }

    @Test
    fun givenProteusClient_whenNewPreKeysThrowException_thenReturnProteusFailure() = runTest {
        val failure = ProteusFailure(ProteusException("why are we still here just to suffer", 55))

        val (arrangement, registerClient) = Arrangement()
            .withGenerateNewPreKeys(Either.Left(failure))
            .arrange()

        val result = registerClient(RegisterClientUseCase.RegisterClientParam(TEST_PASSWORD, TEST_CAPABILITIES))

        assertIs<RegisterClientResult.Failure.Generic>(result)
        assertEquals(failure, result.genericFailure)
    }

    @Test
    fun givenProteusClient_whenNewLastPreKeyThrowException_thenReturnProteusFailure() = runTest {
        val failure = ProteusFailure(ProteusException("why are we still here just to suffer", 55))

        val (arrangement, registerClient) = Arrangement()
            .withGenerateNewLastKey(Either.Left(failure))
            .arrange()

        val result = registerClient(RegisterClientUseCase.RegisterClientParam(TEST_PASSWORD, TEST_CAPABILITIES))

        assertIs<RegisterClientResult.Failure.Generic>(result)
        assertEquals(failure, result.genericFailure)
    }

    @Test
    fun givenRepositoryRegistrationFailsDueBadRequest_whenRegistering_thenInvalidCredentialsErrorShouldBeReturned() = runTest {
        val badRequestFailure = NetworkFailure.ServerMiscommunication(TestNetworkException.badRequest)

        val (arrangement, registerClient) = Arrangement()
            .withRegisterClient(Either.Left(badRequestFailure))
            .arrange()

        val result = registerClient(RegisterClientUseCase.RegisterClientParam(TEST_PASSWORD, TEST_CAPABILITIES))

        assertIs<RegisterClientResult.Failure.InvalidCredentials>(result)

        verify(arrangement.preKeyRepository)
            .suspendFunction(arrangement.preKeyRepository::generateNewPreKeys)
            .with(any(), any())
            .wasInvoked(exactly = once)

        verify(arrangement.preKeyRepository)
            .suspendFunction(arrangement.preKeyRepository::generateNewLastKey)
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenMLsSupportIsDisabled_whenRegistering_thenMLSClientIsNotRegistered() = runTest {
        val registeredClient = CLIENT

        val (arrangement, registerClient) = Arrangement()
            .withIsMLSSupported(false)
            .withRegisterClient(Either.Right(registeredClient))
            .withPersistClientId(Either.Right(Unit))
            .withUpdateOTRLastPreKeyId(Either.Right(Unit))
            .arrange()

        val result = registerClient(RegisterClientUseCase.RegisterClientParam(TEST_PASSWORD, TEST_CAPABILITIES))

        verify(arrangement.clientRepository)
            .suspendFunction(arrangement.clientRepository::registerMLSClient)
            .with(any(), any())
            .wasNotInvoked()

        assertIs<RegisterClientResult.Success>(result)
        assertEquals(registeredClient, result.client)
    }

    private companion object {
        const val KEY_PACKAGE_LIMIT = 100
        const val TEST_PASSWORD = "password"
        val TEST_CAPABILITIES: List<ClientCapability> = listOf(
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
            model = null,
            clientType = null
        )
        val CLIENT = TestClient.CLIENT

        @Mock
        val MLS_CLIENT = mock(classOf<MLSClient>())
        val MLS_PUBLIC_KEY = "public_key".encodeToByteArray()
        val TEST_FAILURE = NetworkFailure.ServerMiscommunication(KaliumException.GenericError(IOException("no internet")))
    }

    private class Arrangement {

        @Mock
        val featureSupport = mock(classOf<FeatureSupport>())

        @Mock
        val clientRepository = mock(classOf<ClientRepository>())

        @Mock
        val preKeyRepository = mock(classOf<PreKeyRepository>())

        @Mock
        val keyPackageRepository = mock(classOf<KeyPackageRepository>())

        @Mock
        val mlsClientProvider = mock(classOf<MLSClientProvider>())

        @Mock
        val keyPackageLimitsProvider = mock(classOf<KeyPackageLimitsProvider>())

        private val registerClient: RegisterClientUseCase = RegisterClientUseCaseImpl(
            featureSupport,
            clientRepository,
            preKeyRepository,
            keyPackageRepository,
            keyPackageLimitsProvider,
            mlsClientProvider
        )

        init {
            given(keyPackageLimitsProvider)
                .function(keyPackageLimitsProvider::refillAmount)
                .whenInvoked()
                .thenReturn(KEY_PACKAGE_LIMIT)

            given(preKeyRepository)
                .suspendFunction(preKeyRepository::generateNewPreKeys)
                .whenInvokedWith(any(), any())
                .then { _, _ -> Either.Right(PRE_KEYS) }

            given(preKeyRepository)
                .suspendFunction(preKeyRepository::generateNewLastKey)
                .whenInvoked()
                .then { Either.Right(LAST_KEY) }

            given(featureSupport)
                .invocation { featureSupport.isMLSSupported }
                .thenReturn(true)
        }

        fun withRegisterClient(result: Either<NetworkFailure, Client>) = apply {
            given(clientRepository)
                .suspendFunction(clientRepository::registerClient)
                .whenInvokedWith(anything())
                .then { result }
        }

        fun withMLSClient(result: Either<CoreFailure, MLSClient>) = apply {
            given(mlsClientProvider)
                .suspendFunction(mlsClientProvider::getMLSClient)
                .whenInvokedWith(eq(CLIENT.id))
                .then { result }
        }

        fun withGetMLSPublicKey(result: ByteArray) = apply {
            given(MLS_CLIENT)
                .function(MLS_CLIENT::getPublicKey)
                .whenInvoked()
                .thenReturn(result)
        }

        fun withRegisterMLSClient(result: Either<CoreFailure, Unit>) = apply {
            given(clientRepository)
                .suspendFunction(clientRepository::registerMLSClient)
                .whenInvokedWith(eq(CLIENT.id), eq(MLS_PUBLIC_KEY))
                .thenReturn(result)
        }

        fun withUploadNewKeyPackages(result: Either<CoreFailure, Unit>) = apply {
            given(keyPackageRepository)
                .suspendFunction(keyPackageRepository::uploadNewKeyPackages)
                .whenInvokedWith(anything(), eq(100))
                .thenReturn(result)
        }

        fun withPersistClientId(result: Either<CoreFailure, Unit>) = apply {
            given(clientRepository)
                .suspendFunction(clientRepository::persistClientId)
                .whenInvokedWith(anything())
                .then { result }
        }

        fun withGenerateNewPreKeys(result: Either<CoreFailure, List<PreKeyCrypto>>) = apply {
            given(preKeyRepository)
                .suspendFunction(preKeyRepository::generateNewPreKeys)
                .whenInvokedWith(any(), any())
                .then { _, _ -> result }
        }

        fun withGenerateNewLastKey(result: Either<ProteusFailure, PreKeyCrypto>) = apply {
            given(preKeyRepository)
                .suspendFunction(preKeyRepository::generateNewLastKey)
                .whenInvoked()
                .then { result }
        }

        fun withIsMLSSupported(result: Boolean) = apply {
            given(featureSupport)
                .invocation { featureSupport.isMLSSupported }
                .thenReturn(result)
        }

        fun withUpdateOTRLastPreKeyId(result: Either<StorageFailure, Unit>) = apply {
            given(preKeyRepository)
                .suspendFunction(preKeyRepository::updateOTRLastPreKeyId)
                .whenInvokedWith(any())
                .then { result }
        }

        /*

         */
        fun arrange() = this to registerClient
    }
}
