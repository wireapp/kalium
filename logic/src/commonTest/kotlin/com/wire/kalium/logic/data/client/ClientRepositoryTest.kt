package com.wire.kalium.logic.data.client

import com.wire.kalium.cryptography.PreKeyCrypto
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.client.remote.ClientRemoteRepository
import com.wire.kalium.logic.data.id.PlainId
import com.wire.kalium.logic.data.user.UserMapper
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.network.api.ErrorResponse
import com.wire.kalium.network.api.user.client.ClientApi
import com.wire.kalium.network.api.user.pushToken.PushTokenBody
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.persistence.client.ClientRegistrationStorage
import com.wire.kalium.persistence.client.TokenStorage
import com.wire.kalium.persistence.dao.client.ClientDAO
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

class ClientRepositoryTest {

    @Mock
    val clientApi = mock(classOf<ClientApi>())

    @Mock
    private val clientRemoteRepository = mock(classOf<ClientRemoteRepository>())

    @Mock
    private val clientRegistrationStorage = configure(mock(classOf<ClientRegistrationStorage>())) {
        stubsUnitByDefault = true
    }

    @Mock
    private val tokenStorage = configure(mock(classOf<TokenStorage>())) {
        stubsUnitByDefault = true
    }

    @Mock
    private val clientDAO = mock(classOf<ClientDAO>())

    @Mock
    private val userMapper = mock(classOf<UserMapper>())

    private lateinit var clientRepository: ClientRepository

    @BeforeTest
    fun setup() {
        clientRepository = ClientDataSource(clientRemoteRepository, clientRegistrationStorage, clientDAO, userMapper)
    }

    @Test
    fun givenClientParams_whenRegisteringClient_thenParamsShouldBePassedCorrectly() = runTest {
        given(clientRemoteRepository)
            .suspendFunction(clientRemoteRepository::registerClient)
            .whenInvokedWith(any())
            .then { Either.Right(CLIENT_RESULT) }

        clientRepository.registerClient(REGISTER_CLIENT_PARAMS)

        verify(clientRemoteRepository)
            .suspendFunction(clientRemoteRepository::registerClient)
            .with(eq(REGISTER_CLIENT_PARAMS))
            .wasInvoked(once)
    }

    @Test
    fun givingRemoteDataSourceFails_whenRegisteringClient_thenTheFailureShouldBePropagated() = runTest {
        val failure = Either.Left(TEST_FAILURE)
        given(clientRemoteRepository)
            .suspendFunction(clientRemoteRepository::registerClient)
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
        given(clientRemoteRepository)
            .suspendFunction(clientRemoteRepository::registerClient)
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

    // clientInfo
    @Test
    fun givenClientId_whenGettingClientInformationSuccess_thenTheSuccessIsReturned() = runTest {
        val clientId = CLIENT_ID
        val expected = Either.Right(CLIENT_RESULT)
        given(clientRemoteRepository)
            .coroutine { clientRemoteRepository.fetchClientInfo(clientId) }
            .then { expected }

        val actual = clientRepository.clientInfo(clientId)

        actual.shouldSucceed { expected.value }
        verify(clientRemoteRepository)
            .coroutine { clientRemoteRepository.fetchClientInfo(clientId) }
            .wasInvoked(exactly = once)
    }

    // clientInfo
    @Test
    fun givenClientId_whenGettingClientInformationFail_thenTheErrorIsPropagated() = runTest {
        val clientId = CLIENT_ID
        val expected = Either.Left(TEST_FAILURE)
        given(clientRemoteRepository)
            .coroutine { clientRemoteRepository.fetchClientInfo(clientId) }
            .then { expected }

        val actual = clientRepository.clientInfo(clientId)

        actual.shouldFail { expected.value }

        verify(clientRemoteRepository)
            .coroutine { clientRemoteRepository.fetchClientInfo(clientId) }
            .wasInvoked(exactly = once)
    }

    // delete client
    @Test
    fun givenClientIdAndAPassword_whenGettingDeletingClientFail_thenTheErrorIsPropagated() = runTest {
        val param = DeleteClientParam("password", CLIENT_ID)

        val expected = Either.Left(TEST_FAILURE)

        given(clientRemoteRepository)
            .coroutine { clientRemoteRepository.deleteClient(param) }
            .then { expected }

        val actual = clientRepository.deleteClient(param)

        actual.shouldFail { expected.value }

        verify(clientRemoteRepository)
            .coroutine { clientRemoteRepository.deleteClient(param) }
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenClientIdAndAPassword_whenGettingDeletingClientSuccess_thenTheSuccessIsPropagated() = runTest {
        val param = DeleteClientParam("password", CLIENT_ID)

        val expected = Either.Right(Unit)

        given(clientRemoteRepository)
            .coroutine { clientRemoteRepository.deleteClient(param) }
            .then { expected }

        val actual = clientRepository.deleteClient(param)

        actual.shouldSucceed { expected.value }

        verify(clientRemoteRepository)
            .coroutine { clientRepository.deleteClient(param) }
            .wasInvoked(exactly = once)
    }


    // selfListOfClients
    @Test
    fun whenSelfListOfClientsIsReturnSuccess_thenTheSuccessIsPropagated() = runTest {
        val expected = Either.Right(
            listOf(
                Client(
                    clientId = PlainId(value = "client_id_1"),
                    type = ClientType.Permanent,
                    registrationTime = "31.08.1966",
                    location = null,
                    deviceType = DeviceType.Desktop,
                    label = null,
                    cookie = null,
                    capabilities = null,
                    model = "Mac ox"
                ),
                Client(
                    clientId = PlainId(value = "client_id_1"),
                    type = ClientType.Permanent,
                    registrationTime = "01.06.2022",
                    location = null,
                    deviceType = DeviceType.Phone,
                    label = null,
                    cookie = null,
                    capabilities = null,
                    model = "iphone 15"
                ),
            )
        )

        given(clientRemoteRepository)
            .coroutine { clientRepository.selfListOfClients() }
            .then { expected }

        val actual = clientRepository.selfListOfClients()
        actual.shouldSucceed { expected.value }
        verify(clientRemoteRepository)
            .coroutine { clientRepository.selfListOfClients() }
            .wasInvoked(exactly = once)
    }


    @Test
    fun whenSelfListOfClientsIsFail_thenTheErrorIsPropagated() = runTest {
        val expected: Either.Left<NetworkFailure> = Either.Left(TEST_FAILURE)

        given(clientRemoteRepository).coroutine { clientRepository.selfListOfClients() }.then { expected }

        val actual = clientRepository.selfListOfClients()
        actual.shouldFail { expected.value }
        verify(clientRemoteRepository).coroutine { clientRepository.selfListOfClients() }.wasInvoked(exactly = once)
    }


    @Test
    fun givenValidParams_whenPushToken_thenShouldSucceed() = runTest {
        given(clientRemoteRepository).coroutine {
            registerToken(pushTokenRequestBody) }
            .then { Either.Right(Unit)}

        given(clientApi)
            .suspendFunction(clientApi::registerToken)
            .whenInvokedWith(any())
            .thenReturn(
                NetworkResponse.Success(
                    Unit,
                    mapOf(),
                    201
                )
            )

        val actual = clientRemoteRepository.registerToken(pushTokenRequestBody)

        actual.shouldSucceed {
            assertEquals(Unit, it)
        }

        verify(clientRemoteRepository).suspendFunction(clientRemoteRepository::registerToken)
            .with(any())
            .wasInvoked(exactly = once)
    }


    private companion object {
        val REGISTER_CLIENT_PARAMS = RegisterClientParam(
            "pass", listOf(), PreKeyCrypto(2, "2"), null, null, listOf(), null
        )
        val CLIENT_ID = TestClient.CLIENT_ID
        val CLIENT_RESULT = TestClient.CLIENT
        val TEST_FAILURE = NetworkFailure.ServerMiscommunication(
            KaliumException.InvalidRequestError(
                ErrorResponse(
                    420, "forbidden", "forbidden"
                )
            )
        )
        val pushTokenRequestBody = PushTokenBody(
            senderId = "7239",
            client = "cliId", token = "7239", transport = "GCM"
        )

    }
}

