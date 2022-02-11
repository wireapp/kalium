package com.wire.kalium.logic.data.client

import com.wire.kalium.cryptography.PreKey
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.client.remote.ClientRemoteDataSource
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.persistence.client.ClientRegistrationStorage
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.configure
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

class ClientRepositoryImplTest {

    @Mock
    private val clientRemoteDataSource = mock(classOf<ClientRemoteDataSource>())

    @Mock
    private val clientRegistrationStorage = configure(mock(classOf<ClientRegistrationStorage>())) {
        stubsUnitByDefault = true
    }

    private lateinit var clientRepository: ClientRepositoryImpl

    @BeforeTest
    fun setup() {
        clientRepository = ClientRepositoryImpl(clientRemoteDataSource, clientRegistrationStorage)
    }

    @Test
    fun givenClientParams_whenRegisteringClient_thenParamsShouldBePassedCorrectly() = runTest {
        given(clientRemoteDataSource)
            .suspendFunction(clientRemoteDataSource::registerClient)
            .whenInvokedWith(any())
            .then { Either.Right(CLIENT_RESULT) }

        clientRepository.registerClient(REGISTER_CLIENT_PARAMS)

        verify(clientRemoteDataSource)
            .suspendFunction(clientRemoteDataSource::registerClient)
            .with(eq(REGISTER_CLIENT_PARAMS))
            .wasInvoked(once)
    }

    @Test
    fun givingRemoteDataSourceFails_whenRegisteringClient_thenTheFailureShouldBePropagated() = runTest {
        val failure = Either.Left(CoreFailure.NoNetworkConnection)
        given(clientRemoteDataSource)
            .suspendFunction(clientRemoteDataSource::registerClient)
            .whenInvokedWith(any())
            .then { failure }

        val result = clientRepository.registerClient(REGISTER_CLIENT_PARAMS)

        result.shouldFail {
            assertSame(failure.value, it)
        }
    }

    @Test
    fun givenRemoteDataSourceSucceed_whenRegisteringClient_thenTheSuccessShouldBePropagated() = runTest {
        val clientResult = Either.Right(CLIENT_RESULT)
        given(clientRemoteDataSource)
            .suspendFunction(clientRemoteDataSource::registerClient)
            .whenInvokedWith(any())
            .then { clientResult }

        val result = clientRepository.registerClient(REGISTER_CLIENT_PARAMS)

        result.shouldSucceed {
            assertSame(clientResult.value, it)
        }
    }

    @Test
    fun givenAClientId_whenPersistingClientId_thenTheStorageShouldBeCalledWithRightParameter() = runTest {
        val clientId = CLIENT_ID

        clientRepository.persistClientId(clientId)

        verify(clientRegistrationStorage)
            .setter(clientRegistrationStorage::registeredClientId)
            .with(eq(clientRepository))
    }

    @Test
    fun givenAClientIdIsStored_whenGettingRegisteredClientId_thenTheStoredValueShouldBeReturned() = runTest {
        val clientId = CLIENT_ID
        given(clientRegistrationStorage)
            .getter(clientRegistrationStorage::registeredClientId)
            .whenInvoked()
            .then { clientId.value }

        val result = clientRepository.currentClientId()

        result.shouldSucceed {
            assertEquals(clientId, it)
        }
    }

    @Test
    fun givenNoClientIdIsStored_whenGettingRegisteredClientId_thenShouldFailWithMissingRegistration() = runTest {
        given(clientRegistrationStorage)
            .getter(clientRegistrationStorage::registeredClientId)
            .whenInvoked()
            .then { null }

        val result = clientRepository.currentClientId()

        result.shouldFail {
            assertIs<CoreFailure.MissingClientRegistration>(it)
        }
    }

    private companion object {
        val REGISTER_CLIENT_PARAMS = RegisterClientParam("pass", listOf(), PreKey(2, "2"), listOf())
        val CLIENT_ID = TestClient.CLIENT_ID
        val CLIENT_RESULT = TestClient.CLIENT
    }
}
