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

package com.wire.kalium.logic.data.prekey

import com.wire.kalium.cryptography.PreKeyCrypto
import com.wire.kalium.cryptography.ProteusClient
import com.wire.kalium.cryptography.createSessions
import com.wire.kalium.cryptography.exceptions.ProteusException
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.ProteusFailure
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.Recipient
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.CurrentClientIdProvider
import com.wire.kalium.logic.feature.ProteusClientProvider
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.test_util.TestNetworkException
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.network.api.base.authenticated.prekey.DomainToUserIdToClientsToPreKeyMap
import com.wire.kalium.network.api.base.authenticated.prekey.ListPrekeysResponse
import com.wire.kalium.network.api.base.authenticated.prekey.PreKeyApi
import com.wire.kalium.network.api.base.authenticated.prekey.PreKeyDTO
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.persistence.dao.PrekeyDAO
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.dao.client.ClientDAO
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.errors.IOException
import io.mockative.Mock
import io.mockative.any
import io.mockative.anything
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest

class PreKeyRepositoryTest {

    @Test
    fun givenGetUserPreKeySuccess_whenGetUserPreKey_thenSuccess() = runTest {
        val expected: Map<String, Map<String, Map<String, PreKeyDTO?>>> =
            mapOf(
                "domain_1" to
                        mapOf(
                            "userId_1" to
                                    mapOf(
                                        "clientId" to PreKeyDTO(44, "key"),
                                        "clientId_2" to PreKeyDTO(45, "key_2")
                                    )
                        ),
                "domain_2" to
                        mapOf(
                            "userId_2" to
                                    mapOf(
                                        "clientId_3" to PreKeyDTO(46, "key_3")
                                    )
                        )
            )
        val (arrange, preKeyRepository) = Arrangement()
            .withGetRemoteUsersPreKeySuccess(expected)
            .arrange()

        preKeyRepository.preKeysOfClientsByQualifiedUsers(mapOf()).also {
            assertIs<Either.Right<List<QualifiedUserPreKeyInfo>>>(it)
        }

        verify(arrange.preKeyApi)
            .suspendFunction(arrange.preKeyApi::getUsersPreKey)
            .with(any())
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenValidCrypto_whenGeneratingNewPreyKeys_thenSuccess() = runTest {
        val expected = listOf(PreKeyCrypto(44, "key"))
        val (arrange, preKeyRepository) = Arrangement()
            .withGenerateNewPreKeysSuccess(1, 1, expected)
            .arrange()

        preKeyRepository.generateNewPreKeys(1, 1).also {
            assertIs<Either.Right<List<PreKeyCrypto>>>(it)
            assertEquals(expected, it.value)
        }

        verify(arrange.proteusClient)
            .suspendFunction(arrange.proteusClient::newPreKeys)
            .with(any(), any())
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenValidCrypto_whenGeneratingLastPreyKeys_thenSuccess() = runTest {
        val expected = PreKeyCrypto(44, "key")
        val (arrange, preKeyRepository) = Arrangement()
            .withGenerateLastPreKeysSuccess(expected)
            .arrange()

        preKeyRepository.generateNewLastKey().also {
            assertIs<Either.Right<PreKeyCrypto>>(it)
            assertEquals(expected, it.value)
        }

        verify(arrange.proteusClient)
            .suspendFunction(arrange.proteusClient::newLastPreKey)
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenFetchingPreKeysWithNullClients_whenPreparingSessions_thenTryToInvalidateInvalidSessions() = runTest {
        val preKey = PreKeyDTO(42, "encodedData")
        val prekeyCrypto = PreKeyCrypto(preKey.id, preKey.key)

        val userPreKeysResult = NetworkResponse.Success(

            ListPrekeysResponse(
                qualifiedUserClientPrekeys = mapOf(
                    TEST_USER_ID_1.domain to mapOf(
                        TEST_USER_ID_1.value to mapOf(
                            TEST_CLIENT_ID_1.value to preKey,
                            "invalidClient" to null,
                        )
                    )
                )
            ),
            emptyMap(),
            200
        )
        val expectedValid: Map<String, Map<String, Map<String, PreKeyCrypto>>> =
            mapOf(TEST_USER_ID_1.domain to mapOf(TEST_USER_ID_1.value to mapOf(TEST_CLIENT_ID_1.value to prekeyCrypto)))

        val expectedInvalid: Map<UserId, List<ClientId>> =
            mapOf(TEST_USER_ID_1 to listOf(ClientId("invalidClient")))

        val (arrangement, preKeyRepository) = Arrangement()
            .withDoesSessionExist(false)
            .withPreKeysOfClientsByQualifiedUsersSuccess(userPreKeysResult)
            .arrange()

        preKeyRepository.establishSessions(expectedInvalid)

        verify(arrangement.proteusClient)
            .coroutine { arrangement.proteusClient.createSessions(expectedValid) }
            .wasInvoked(exactly = once)

        verify(arrangement.clientDAO)
            .coroutine {
                arrangement.clientDAO.tryMarkInvalid(
                    listOf(UserIDEntity(TEST_USER_ID_1.value, TEST_USER_ID_1.domain) to listOf("invalidClient"))
                )
            }
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenCreatingSessionsThrows_whenPreparingSessions_thenItShouldFail() = runTest {
        val exception = ProteusException("PANIC!!!11!eleven!", ProteusException.Code.PANIC)

        val preKey = PreKeyDTO(42, "encodedData")
        val userPreKeysResult =
            mapOf(TEST_USER_ID_1.domain to mapOf(TEST_USER_ID_1.value to mapOf(TEST_CLIENT_ID_1.value to preKey)))

        val (_, prekeyRepository) = Arrangement()
            .withGetRemoteUsersPreKeySuccess(userPreKeysResult)
            .withDoesSessionExist(false)
            .withCreateSession(exception)
            .arrange()

        prekeyRepository.establishSessions(mapOf(TEST_USER_ID_1 to listOf(TEST_CLIENT_ID_1)))
            .shouldFail {
                assertIs<ProteusFailure>(it)
                assertEquals(exception, it.proteusException)
            }
    }

    @Test
    fun givenASessionIsNotEstablished_whenPreparingSessions_thenPreKeysShouldBeFetched() = runTest {

        val (arrangement, sessionEstablisher) = Arrangement()
            .withDoesSessionExist(false)
            .withGetRemoteUsersPreKeySuccess(
                mapOf(
                    TEST_USER_ID_1.domain to mapOf(
                        TEST_USER_ID_1.value to mapOf(
                            TEST_CLIENT_ID_1.value to PreKeyDTO(
                                42,
                                "encodedData"
                            )
                        )
                    )
                )
            )
            .arrange()

        sessionEstablisher.establishSessions(mapOf(TEST_RECIPIENT_1.id to TEST_RECIPIENT_1.clients))

        verify(arrangement.preKeyApi)
            .suspendFunction(arrangement.preKeyApi::getUsersPreKey)
            .with(
                eq(
                    mapOf(
                        TEST_USER_ID_1.domain to mapOf(
                            TEST_USER_ID_1.value to listOf(TEST_CLIENT_ID_1.value)
                        )
                    )
                )
            )
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenFetchingPreKeysFails_whenPreparingSessions_thenFailureShouldBePropagated() = runTest {
        val failure = NETWORK_ERROR

        val (_, sessionEstablisher) = Arrangement()
            .withDoesSessionExist(false)
            .withGetRemoteUsersPreKeyFail(NetworkResponse.Error(failure.kaliumException))
            .arrange()

        sessionEstablisher.establishSessions(mapOf(TEST_USER_ID_1 to listOf(TEST_CLIENT_ID_1)))
            .shouldFail {
                assertIs<NetworkFailure.ServerMiscommunication>(it)
                assertEquals(failure.kaliumException, it.kaliumException)
            }
    }

    @Test
    fun givenCurrentClientIdFails_whenFetchingRemotePrekeys_thenShouldPropagateFailure() = runTest {
        val failure = CoreFailure.Unknown(null)
        val (_, preKeyRepository) = Arrangement()
            .withCurrentClientIdReturning(Either.Left(failure))
            .arrange()

        preKeyRepository.fetchRemotelyAvailablePrekeys()
            .shouldFail {
                assertIs<CoreFailure.Unknown>(it)
                assertEquals(failure, it)
            }
    }

    @Test
    fun givenSuccess_whenFetchingRemotePrekeys_thenShouldPropagateSuccess() = runTest {
        val availablePreKeysIds = listOf(1, 3, 6)
        val result = NetworkResponse.Success(availablePreKeysIds, mapOf(), HttpStatusCode.OK.value)
        val (_, preKeyRepository) = Arrangement()
            .withGetClientAvailablePrekeysReturning(result)
            .arrange()

        preKeyRepository.fetchRemotelyAvailablePrekeys()
            .shouldSucceed { preKeys ->
                assertContentEquals(availablePreKeysIds, preKeys)
            }
    }

    @Test
    fun givenCurrentClientId_whenFetchingRemotePrekeys_thenShouldCallAPIWithCorrectParameters() = runTest {
        val (arrangement, preKeyRepository) = Arrangement()
            .withGetClientAvailablePrekeysReturning(NetworkResponse.Success(listOf(), mapOf(), HttpStatusCode.OK.value))
            .withCurrentClientIdReturning(Either.Right(TEST_CLIENT_ID_1))
            .arrange()

        preKeyRepository.fetchRemotelyAvailablePrekeys()

        verify(arrangement.preKeyApi)
            .suspendFunction(arrangement.preKeyApi::getClientAvailablePrekeys)
            .with(eq(TEST_CLIENT_ID_1.value))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenCurrentClientIdFails_whenUploadingPrekeys_thenShouldPropagateFailure() = runTest {
        val failure = CoreFailure.Unknown(null)
        val (_, preKeyRepository) = Arrangement()
            .withCurrentClientIdReturning(Either.Left(failure))
            .arrange()

        preKeyRepository.uploadNewPrekeyBatch(listOf())
            .shouldFail {
                assertIs<CoreFailure.Unknown>(it)
                assertEquals(failure, it)
            }
    }

    @Test
    fun givenPreKeysAndCurrentClientId_whenUploadingMorePrekeys_thenShouldCallAPIWithCorrectArguments() = runTest {
        val preKeys = listOf(PreKeyCrypto(1, "encodedData"))
        val (arrangement, preKeyRepository) = Arrangement()
            .withUploadPrekeysReturning(NetworkResponse.Success(Unit, mapOf(), HttpStatusCode.OK.value))
            .withCurrentClientIdReturning(Either.Right(TEST_CLIENT_ID_1))
            .arrange()

        preKeyRepository.uploadNewPrekeyBatch(preKeys)
            .shouldSucceed()

        verify(arrangement.preKeyApi)
            .suspendFunction(arrangement.preKeyApi::uploadNewPrekeys)
            .with(eq(TEST_CLIENT_ID_1.value), eq(preKeys.map { PreKeyDTO(it.id, it.encodedData) }))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenSuccess_whenUploadingMorePrekeys_thenShouldPropagateSuccess() = runTest {
        val result = NetworkResponse.Success(Unit, mapOf(), HttpStatusCode.OK.value)
        val (_, preKeyRepository) = Arrangement()
            .withUploadPrekeysReturning(result)
            .arrange()

        preKeyRepository.uploadNewPrekeyBatch(listOf())
            .shouldSucceed()
    }

    private companion object {
        val TEST_USER_ID_1 = TestUser.USER_ID
        val TEST_CLIENT_ID_1 = TestClient.CLIENT_ID
        val TEST_RECIPIENT_1 = Recipient(TestUser.USER_ID, listOf(TestClient.CLIENT_ID))
        val NETWORK_ERROR = NetworkFailure.ServerMiscommunication(TestNetworkException.generic)
    }

    private class Arrangement {

        @Mock
        val preKeyApi: PreKeyApi = mock(PreKeyApi::class)

        @Mock
        val proteusClient: ProteusClient = mock(ProteusClient::class)

        @Mock
        val currentClientIdProvider: CurrentClientIdProvider = mock(CurrentClientIdProvider::class)

        @Mock
        val proteusClientProvider: ProteusClientProvider = mock(ProteusClientProvider::class)

        @Mock
        val prekeyDAO: PrekeyDAO = mock(PrekeyDAO::class)

        @Mock
        val clientDAO: ClientDAO = mock(ClientDAO::class)

        private val preKeyRepository: PreKeyDataSource =
            PreKeyDataSource(
                preKeyApi = preKeyApi,
                proteusClientProvider = proteusClientProvider,
                provideCurrentClientId = currentClientIdProvider,
                prekeyDAO = prekeyDAO,
                clientDAO = clientDAO
            )

        init {
            given(proteusClientProvider)
                .suspendFunction(proteusClientProvider::getOrCreate)
                .whenInvoked()
                .thenReturn(proteusClient)

            given(proteusClientProvider)
                .suspendFunction(proteusClientProvider::getOrError)
                .whenInvoked()
                .thenReturn(Either.Right(proteusClient))

            withCurrentClientIdReturning(Either.Right(TEST_CLIENT_ID_1))
        }

        fun withGetRemoteUsersPreKeySuccess(preKeyMap: DomainToUserIdToClientsToPreKeyMap) = apply {
            given(preKeyApi)
                .suspendFunction(preKeyApi::getUsersPreKey)
                .whenInvokedWith(any())
                .then {
                    NetworkResponse.Success(
                        ListPrekeysResponse(qualifiedUserClientPrekeys = preKeyMap),
                        emptyMap(),
                        200
                    )
                }
        }

        fun withGetRemoteUsersPreKeyFail(error: NetworkResponse.Error? = null) = apply {
            if (error == null) {
                given(preKeyApi)
                    .suspendFunction(preKeyApi::getUsersPreKey)
                    .whenInvokedWith(any())
                    .then { NetworkResponse.Error(KaliumException.GenericError(IOException("offline"))) }
            } else {
                given(preKeyApi)
                    .suspendFunction(preKeyApi::getUsersPreKey)
                    .whenInvokedWith(any())
                    .then { error }
            }
        }

        suspend fun withGenerateNewPreKeysSuccess(from: Int, count: Int, expected: List<PreKeyCrypto>) = apply {
            given(proteusClient)
                .coroutine { proteusClient.newPreKeys(from, count) }
                .then { expected }
        }

        suspend fun withGenerateLastPreKeysSuccess(expected: PreKeyCrypto) = apply {
            given(proteusClient)
                .coroutine { proteusClient.newLastPreKey() }
                .then { expected }
        }

        fun withMarkInvalid() = apply {
            given(clientDAO)
                .suspendFunction(clientDAO::tryMarkInvalid)
                .whenInvokedWith(any())
                .thenReturn(Unit)
        }

        fun withDoesSessionExist(result: Boolean) = apply {
            given(proteusClient)
                .suspendFunction(proteusClient::doesSessionExist)
                .whenInvokedWith(any())
                .then { result }
        }

        fun withPreKeysOfClientsByQualifiedUsersSuccess(
            preKeyMap: NetworkResponse<ListPrekeysResponse>
        ) = apply {
            given(preKeyApi)
                .suspendFunction(preKeyApi::getUsersPreKey)
                .whenInvokedWith(any())
                .then { preKeyMap }
        }

        fun withCreateSession(throwable: Throwable) = apply {
            given(proteusClient)
                .suspendFunction(proteusClient::createSession)
                .whenInvokedWith(anything(), anything())
                .thenThrow(throwable)
        }

        fun withGetClientAvailablePrekeysReturning(result: NetworkResponse<List<Int>>) = apply {
            given(preKeyApi)
                .suspendFunction(preKeyApi::getClientAvailablePrekeys)
                .whenInvokedWith(any())
                .thenReturn(result)
        }

        fun withUploadPrekeysReturning(result: NetworkResponse<Unit>) = apply {
            given(preKeyApi)
                .suspendFunction(preKeyApi::uploadNewPrekeys)
                .whenInvokedWith(any(), any())
                .thenReturn(result)
        }

        fun withCurrentClientIdReturning(result: Either<CoreFailure, ClientId>) = apply {
            given(currentClientIdProvider)
                .suspendFunction(currentClientIdProvider::invoke)
                .whenInvoked()
                .thenReturn(result)
        }

        fun arrange() = this to preKeyRepository
    }
}
