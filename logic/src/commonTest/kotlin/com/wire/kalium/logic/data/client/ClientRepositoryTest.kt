/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

package com.wire.kalium.logic.data.client

import app.cash.turbine.test
import com.wire.kalium.cryptography.PreKeyCrypto
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.client.remote.ClientRemoteRepository
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.PlainId
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.network.api.base.authenticated.client.ClientApi
import com.wire.kalium.network.api.base.authenticated.client.ClientResponse
import com.wire.kalium.network.api.base.authenticated.client.ClientTypeDTO
import com.wire.kalium.network.api.base.authenticated.client.DeviceTypeDTO
import com.wire.kalium.network.api.base.authenticated.client.SimpleClientResponse
import com.wire.kalium.network.api.base.model.ErrorResponse
import com.wire.kalium.network.api.base.model.PushTokenBody
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.persistence.client.ClientRegistrationStorage
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.dao.client.ClientDAO
import com.wire.kalium.persistence.dao.client.ClientTypeEntity
import com.wire.kalium.persistence.dao.client.DeviceTypeEntity
import com.wire.kalium.util.DelicateKaliumApi
import io.ktor.util.encodeBase64
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.configure
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import com.wire.kalium.network.api.base.model.UserId as UserIdDTO
import com.wire.kalium.persistence.dao.client.Client as ClientEntity

@ExperimentalCoroutinesApi
class ClientRepositoryTest {

    @Test
    fun givenSuccess_whenRegisteringMLSClient_thenSetHasRegisteredMLSClient() = runTest {
        val (arrangement, clientRepository) = Arrangement()
            .withRegisterMLSClient(Either.Right(Unit))
            .arrange()

        clientRepository.registerMLSClient(CLIENT_ID, MLS_PUBLIC_KEY)

        verify(arrangement.clientRemoteRepository)
            .suspendFunction(arrangement.clientRemoteRepository::registerMLSClient)
            .with(eq(CLIENT_ID), eq(MLS_PUBLIC_KEY.encodeBase64()))
            .wasInvoked(once)

        verify(arrangement.clientRegistrationStorage)
            .suspendFunction(arrangement.clientRegistrationStorage::setHasRegisteredMLSClient)
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenClientParams_whenRegisteringClient_thenParamsShouldBePassedCorrectly() = runTest {

        val (arrangement, clientRepository) = Arrangement()
            .withRegisterClient(Either.Right(CLIENT_RESULT))
            .arrange()

        clientRepository.registerClient(REGISTER_CLIENT_PARAMS)

        verify(arrangement.clientRemoteRepository)
            .suspendFunction(arrangement.clientRemoteRepository::registerClient)
            .with(eq(REGISTER_CLIENT_PARAMS))
            .wasInvoked(once)
    }

    @Test
    fun givingRemoteDataSourceFails_whenRegisteringClient_thenTheFailureShouldBePropagated() = runTest {
        val failure = Either.Left(TEST_FAILURE)

        val (_, clientRepository) = Arrangement()
            .withRegisterClient(failure)
            .arrange()

        val result = clientRepository.registerClient(REGISTER_CLIENT_PARAMS)

        result.shouldFail {
            assertSame(failure.value, it)
        }
    }

    @Test
    fun givenRemoteDataSourceSucceed_whenRegisteringClient_thenTheSuccessShouldBePropagated() = runTest {
        val clientResult = Either.Right(CLIENT_RESULT)

        val (_, clientRepository) = Arrangement()
            .withRegisterClient(clientResult)
            .arrange()

        val result = clientRepository.registerClient(REGISTER_CLIENT_PARAMS)

        result.shouldSucceed {
            assertSame(clientResult.value, it)
        }
    }

    @Test
    fun givenAClientId_whenPersistingClientId_thenTheStorageShouldBeCalledWithRightParameter() = runTest {
        val clientId = CLIENT_ID

        val (arrangement, clientRepository) = Arrangement()
            .arrange()

        clientRepository.persistClientId(clientId)

        verify(arrangement.clientRegistrationStorage)
            .suspendFunction(arrangement.clientRegistrationStorage::setRegisteredClientId)
            .with(eq(clientRepository))
    }

    @OptIn(DelicateKaliumApi::class)
    @Test
    fun givenAClientIdIsStored_whenGettingRegisteredClientId_thenTheStoredValueShouldBeReturned() = runTest {
        val clientId = CLIENT_ID

        val (_, clientRepository) = Arrangement()
            .witGgetRegisteredClientId(clientId.value)
            .arrange()

        val result = clientRepository.currentClientId()

        result.shouldSucceed {
            assertEquals(clientId, it)
        }
    }

    @OptIn(DelicateKaliumApi::class)
    @Test
    fun givenNoClientIdIsStored_whenGettingRegisteredClientId_thenShouldFailWithMissingRegistration() = runTest {

        val (_, clientRepository) = Arrangement()
            .witGgetRegisteredClientId(null)
            .arrange()

        val result = clientRepository.currentClientId()

        result.shouldFail {
            assertIs<CoreFailure.MissingClientRegistration>(it)
        }
    }

    @Test
    fun givenAClientId_whenPersistingRetainedClientId_thenTheStorageShouldBeCalledWithRightParameter() = runTest {
        val clientId = CLIENT_ID

        val (arrangement, clientRepository) = Arrangement()
            .arrange()

        clientRepository.persistRetainedClientId(clientId)

        verify(arrangement.clientRegistrationStorage)
            .suspendFunction(arrangement.clientRegistrationStorage::setRetainedClientId)
            .with(eq(clientRepository))
    }

    @Test
    fun givenClientIdAndAPassword_whenGettingDeletingClientFail_thenTheErrorIsPropagated() = runTest {
        val param = DeleteClientParam("password", CLIENT_ID)

        val expected = Either.Left(TEST_FAILURE)

        val (arrangement, clientRepository) = Arrangement()
            .withDeleteClientReportedly(expected)
            .arrange()

        val actual = clientRepository.deleteClient(param)

        actual.shouldFail { expected.value }

        verify(arrangement.clientRemoteRepository)
            .coroutine { arrangement.clientRemoteRepository.deleteClient(param) }
            .wasInvoked(exactly = once)
        verify(arrangement.clientDAO)
            .coroutine { arrangement.clientDAO.deleteClient(selfUserId.toDao(), param.clientId.value) }
            .wasNotInvoked()
    }

    @Test
    fun givenClientIdAndAPassword_whenGettingDeletingClientSuccess_thenTheSuccessIsPropagated() = runTest {
        val param = DeleteClientParam("password", CLIENT_ID)

        val (arrangement, clientRepository) = Arrangement()
            .withDeleteClientReportedly(Either.Right(Unit))
            .withDeleteClientLocally()
            .arrange()

        val actual = clientRepository.deleteClient(param)

        actual.shouldSucceed()

        verify(arrangement.clientRemoteRepository)
            .coroutine { clientRepository.deleteClient(param) }
            .wasInvoked(exactly = once)
        verify(arrangement.clientDAO)
            .coroutine { arrangement.clientDAO.deleteClient(selfUserId.toDao(), param.clientId.value) }
            .wasInvoked(exactly = once)
    }

    // selfListOfClients
    @Test
    fun whenSelfListOfClientsIsReturnSuccess_thenTheSuccessIsPropagated() = runTest {
        val result = NetworkResponse.Success(
            listOf(
                ClientResponse(
                    clientId = "client_id_1",
                    type = ClientTypeDTO.Permanent,
                    registrationTime = "1969-05-12T10:52:02.671Z",
                    deviceType = DeviceTypeDTO.Desktop,
                    label = null,
                    model = "Mac ox",
                    capabilities = null,
                    mlsPublicKeys = null,
                    cookie = null,
                    location = null
                ),
                ClientResponse(
                    clientId = "client_id_2",
                    type = ClientTypeDTO.Permanent,
                    registrationTime = "2021-05-12T10:52:02.671Z",
                    deviceType = DeviceTypeDTO.Phone,
                    label = null,
                    model = "iphone 15",
                    capabilities = null,
                    mlsPublicKeys = null,
                    cookie = null,
                    location = null
                )
            ),
            mapOf(),
            200
        )

        val expected = listOf(
            Client(
                id = PlainId(value = "client_id_1"),
                type = ClientType.Permanent,
                registrationTime = Instant.parse("1969-05-12T10:52:02.671Z"),
                deviceType = DeviceType.Desktop,
                label = null,
                model = "Mac ox",
                isVerified = false,
                isValid = true
            ),
            Client(
                id = PlainId(value = "client_id_2"),
                type = ClientType.Permanent,
                registrationTime = Instant.parse("2021-05-12T10:52:02.671Z"),
                deviceType = DeviceType.Phone,
                label = null,
                model = "iphone 15",
                isVerified = false,
                isValid = true
            ),
        )

        val (_, clientRepository) = Arrangement()
            .withFetchSelfUserClient(result)
            .arrange()

        clientRepository.selfListOfClients().shouldSucceed {
            assertEquals(expected, it)
        }
    }

    @Test
    fun whenSelfListOfClientsIsFail_thenTheErrorIsPropagated() = runTest {
        val expected: NetworkResponse.Error = NetworkResponse.Error(TEST_FAILURE.kaliumException)

        val (_, clientRepository) = Arrangement()
            .withFetchSelfUserClient(expected)
            .arrange()

        clientRepository.selfListOfClients().shouldFail {
            assertIs<NetworkFailure.ServerMiscommunication>(it)
            assertEquals(expected.kException, it.kaliumException)
        }
    }

    @Test
    fun givenClientStorageUpdatesTheClientId_whenObservingClientId_thenUpdatesShouldBePropagated() = runTest {
        // Given
        val values = listOf("first", "second")

        val (_, clientRepository) = Arrangement()
            .withObserveRegisteredClientId(values.asFlow())
            .arrange()

        // When
        clientRepository.observeCurrentClientId().test {

            // Then
            values.forEach {
                assertEquals(ClientId(it), awaitItem())
            }
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun givenUserId_whenObservingClientsList_thenDAOisCalled() = runTest {
        // Given
        val userId = UserIDEntity("user-id", "domain")
        val clientsList = listOf(
            ClientEntity(
                id = "client-id",
                clientType = ClientTypeEntity.Permanent,
                registrationDate = null,
                deviceType = DeviceTypeEntity.Desktop,
                label = null,
                model = null,
                isVerified = false,
                isValid = true,
                userId = userId,
            )
        )

        val expected = listOf(
            Client(
                id = ClientId("client-id"),
                type = ClientType.Permanent,
                registrationTime = null,
                deviceType = DeviceType.Desktop,
                label = null,
                model = null,
                isVerified = false,
                isValid = true
            )
        )

        val (_, clientRepository) = Arrangement()
            .withObserveClientsList(clientsList)
            .arrange()

        // When
        clientRepository.observeClientsByUserId(UserId("user-id", "domain")).test {
            awaitItem().also {
                it.shouldSucceed {
                    assertEquals(expected, it)
                }
            }
            cancelAndConsumeRemainingEvents()
        }
    }

    private companion object {
        val selfUserId = UserId("self-user-id", "domain")
        const val SECOND_FACTOR_CODE = "123456"
        val REGISTER_CLIENT_PARAMS = RegisterClientParam(
            password = "pass",
            preKeys = listOf(),
            lastKey = PreKeyCrypto(2, "2"),
            deviceType = null,
            label = null,
            capabilities = listOf(),
            clientType = null,
            model = null,
            cookieLabel = "cookieLabel",
            secondFactorVerificationCode = SECOND_FACTOR_CODE,
        )
        val MLS_PUBLIC_KEY = "public_key".encodeToByteArray()
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

    private class Arrangement {

        @Mock
        val clientApi = mock(classOf<ClientApi>())

        @Mock
        val clientRemoteRepository = mock(classOf<ClientRemoteRepository>())

        @Mock
        val clientRegistrationStorage = configure(mock(classOf<ClientRegistrationStorage>())) {
            stubsUnitByDefault = true
        }

        @Mock
        val clientDAO = mock(classOf<ClientDAO>())

        var clientRepository: ClientRepository =
            ClientDataSource(clientRemoteRepository, clientRegistrationStorage, clientDAO, selfUserId, clientApi)

        fun withObserveRegisteredClientId(values: Flow<String?>) = apply {
            given(clientRegistrationStorage)
                .suspendFunction(clientRegistrationStorage::observeRegisteredClientId)
                .whenInvoked()
                .thenReturn(values)
        }

        fun withFetchSelfUserClient(result: NetworkResponse<List<ClientResponse>>) = apply {
            given(clientApi)
                .suspendFunction(clientApi::fetchSelfUserClient)
                .whenInvoked()
                .thenReturn(result)
        }

        fun withObserveClientsList(result: List<ClientEntity>) = apply {
            given(clientDAO)
                .suspendFunction(clientDAO::observeClientsByUserId)
                .whenInvokedWith(any())
                .thenReturn(flowOf(result))
        }

        fun withSuccessfulResponse(expectedResponse: Map<UserIdDTO, List<SimpleClientResponse>>) = apply {
            given(clientApi)
                .suspendFunction(clientApi::listClientsOfUsers).whenInvokedWith(any()).then {
                    NetworkResponse.Success(expectedResponse, mapOf(), 200)
                }
        }

        fun withErrorResponse(kaliumException: KaliumException) = apply {
            given(clientApi)
                .suspendFunction(clientApi::listClientsOfUsers)
                .whenInvokedWith(any())
                .then {
                    NetworkResponse.Error(
                        kaliumException
                    )
                }
        }

        fun withDeleteClientReportedly(resultL: Either<NetworkFailure, Unit>) = apply {
            given(clientRemoteRepository)
                .suspendFunction(clientRemoteRepository::deleteClient)
                .whenInvokedWith(any())
                .thenReturn(resultL)
        }

        fun witGgetRegisteredClientId(result: String?) = apply {
            given(clientRegistrationStorage)
                .suspendFunction(clientRegistrationStorage::getRegisteredClientId)
                .whenInvoked()
                .thenReturn(result)
        }

        fun withRegisterClient(result: Either<NetworkFailure, Client>) = apply {
            given(clientRemoteRepository)
                .suspendFunction(clientRemoteRepository::registerClient)
                .whenInvokedWith(any())
                .thenReturn(result)
        }

        fun withRegisterMLSClient(result: Either<NetworkFailure, Unit>) = apply {
            given(clientRemoteRepository)
                .suspendFunction(clientRemoteRepository::registerMLSClient)
                .whenInvokedWith(any(), any())
                .thenReturn(result)
        }

        fun withDeleteClientLocally() = apply {
            given(clientDAO)
                .suspendFunction(clientDAO::deleteClient)
                .whenInvokedWith(any(), any())
                .thenReturn(Unit)
        }

        fun arrange() = this to clientRepository
    }
}
