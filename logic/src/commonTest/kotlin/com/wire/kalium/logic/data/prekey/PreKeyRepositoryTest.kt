/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
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

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.error.ProteusFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.cryptography.CryptoClientId
import com.wire.kalium.cryptography.CryptoSessionId
import com.wire.kalium.cryptography.CryptoUserID
import com.wire.kalium.cryptography.PreKeyCrypto
import com.wire.kalium.cryptography.ProteusClient
import com.wire.kalium.cryptography.exceptions.ProteusException
import com.wire.kalium.logic.data.client.ProteusClientProvider
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.Recipient
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.test_util.TestNetworkException
import com.wire.kalium.logic.util.arrangement.provider.ProteusCoreCryptoContextArrangement
import com.wire.kalium.logic.util.arrangement.provider.ProteusCoreCryptoContextArrangementImpl
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.network.api.authenticated.prekey.PreKeyDTO
import com.wire.kalium.network.api.base.authenticated.prekey.DomainToUserIdToClientsToPreKeyMap
import com.wire.kalium.network.api.base.authenticated.prekey.ListPrekeysResponse
import com.wire.kalium.network.api.base.authenticated.prekey.PreKeyApi
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.persistence.dao.MetadataDAO
import com.wire.kalium.persistence.dao.PrekeyDAO
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.dao.client.ClientDAO
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import io.ktor.http.HttpStatusCode
import kotlinx.io.IOException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs

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
            .arrange {}

        preKeyRepository.preKeysOfClientsByQualifiedUsers(mapOf()).also {
            assertIs<Either.Right<List<QualifiedUserPreKeyInfo>>>(it)
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrange.preKeyApi.getUsersPreKey(any())
        }
    }

    @Test
    fun givenValidCrypto_whenGeneratingNewPreyKeys_thenSuccess() = runTest {
        val expected = listOf(PreKeyCrypto(44, "key"))
        val (arrange, preKeyRepository) = Arrangement()
            .withGenerateNewPreKeysSuccess(1, 1, expected)
            .arrange {}

        preKeyRepository.generateNewPreKeys(1, 1).also {
            assertIs<Either.Right<List<PreKeyCrypto>>>(it)
            assertEquals(expected, it.value)
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrange.proteusClient.newPreKeys(any(), any())
        }
    }

    @Test
    fun givenValidCrypto_whenGeneratingLastPreyKeys_thenSuccess() = runTest {
        val expected = PreKeyCrypto(44, "key")
        val (arrange, preKeyRepository) = Arrangement()
            .withGenerateLastPreKeysSuccess(expected)
            .arrange {
                withNewLastResortPreKeyReturning(expected)
            }

        preKeyRepository.generateNewLastResortKey().also {
            assertIs<Either.Right<PreKeyCrypto>>(it)
            assertEquals(expected, it.value)
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrange.proteusClient.newLastResortPreKey()
        }
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
        val expectedSessionId = CryptoSessionId(
            CryptoUserID(TEST_USER_ID_1.value, TEST_USER_ID_1.domain),
            CryptoClientId(TEST_CLIENT_ID_1.value)
        )

        val expectedInvalid: Map<UserId, List<ClientId>> =
            mapOf(TEST_USER_ID_1 to listOf(ClientId("invalidClient")))

        val (arrangement, preKeyRepository) = Arrangement()
            .withDoesSessionExist(false)
            .withPreKeysOfClientsByQualifiedUsersSuccess(userPreKeysResult)
            .arrange {}

        preKeyRepository.establishSessions(arrangement.proteusContext, expectedInvalid)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.proteusContext.createSession(prekeyCrypto, expectedSessionId)
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.clientDAO.tryMarkInvalid(
                listOf(UserIDEntity(TEST_USER_ID_1.value, TEST_USER_ID_1.domain) to listOf("invalidClient"))
            )
        }
    }

    @Test
    fun givenCreatingSessionsThrows_whenPreparingSessions_thenItShouldFail() = runTest {
        val exception = ProteusException("PANIC!!!11!eleven!", ProteusException.Code.PANIC, 15)

        val preKey = PreKeyDTO(42, "encodedData")
        val userPreKeysResult =
            mapOf(TEST_USER_ID_1.domain to mapOf(TEST_USER_ID_1.value to mapOf(TEST_CLIENT_ID_1.value to preKey)))

        val (arrangement, prekeyRepository) = Arrangement()
            .withGetRemoteUsersPreKeySuccess(userPreKeysResult)
            .withDoesSessionExist(false)
            .withCreateSession(exception)
            .arrange {}

        prekeyRepository.establishSessions(arrangement.proteusContext, mapOf(TEST_USER_ID_1 to listOf(TEST_CLIENT_ID_1)))
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
            .arrange {}

        sessionEstablisher.establishSessions(arrangement.proteusContext, mapOf(TEST_RECIPIENT_1.id to TEST_RECIPIENT_1.clients))

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.preKeyApi.getUsersPreKey(
                mapOf(
                    TEST_USER_ID_1.domain to mapOf(
                        TEST_USER_ID_1.value to listOf(TEST_CLIENT_ID_1.value)
                    )
                )
            )
        }
    }

    @Test
    fun givenFetchingPreKeysFails_whenPreparingSessions_thenFailureShouldBePropagated() = runTest {
        val failure = NETWORK_ERROR

        val (arrangement, sessionEstablisher) = Arrangement()
            .withDoesSessionExist(false)
            .withGetRemoteUsersPreKeyFail(NetworkResponse.Error(failure.kaliumException))
            .arrange {}

        sessionEstablisher.establishSessions(arrangement.proteusContext, mapOf(TEST_USER_ID_1 to listOf(TEST_CLIENT_ID_1)))
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
            .arrange {}

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
            .withCurrentClientIdReturning(Either.Right(TEST_CLIENT_ID_1))
            .withGetClientAvailablePrekeysReturning(result)
            .arrange {}

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
            .arrange {}

        preKeyRepository.fetchRemotelyAvailablePrekeys()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.preKeyApi.getClientAvailablePrekeys(TEST_CLIENT_ID_1.value)
        }
    }

    @Test
    fun givenCurrentClientIdFails_whenUploadingPrekeys_thenShouldPropagateFailure() = runTest {
        val failure = CoreFailure.Unknown(null)
        val (_, preKeyRepository) = Arrangement()
            .withCurrentClientIdReturning(Either.Left(failure))
            .arrange {}

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
            .arrange {}

        preKeyRepository.uploadNewPrekeyBatch(preKeys)
            .shouldSucceed()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.preKeyApi.uploadNewPrekeys(TEST_CLIENT_ID_1.value, preKeys.map { PreKeyDTO(it.id, it.pkb) })
        }
    }

    @Test
    fun givenSuccess_whenUploadingMorePrekeys_thenShouldPropagateSuccess() = runTest {
        val result = NetworkResponse.Success(Unit, mapOf(), HttpStatusCode.OK.value)
        val (_, preKeyRepository) = Arrangement()
            .withUploadPrekeysReturning(result)
            .withCurrentClientIdReturning(Either.Right(TEST_CLIENT_ID_1))
            .arrange {}

        preKeyRepository.uploadNewPrekeyBatch(listOf())
            .shouldSucceed()
    }

    private companion object {
        val TEST_USER_ID_1 = TestUser.USER_ID
        val TEST_CLIENT_ID_1 = TestClient.CLIENT_ID
        val TEST_RECIPIENT_1 = Recipient(TestUser.USER_ID, listOf(TestClient.CLIENT_ID))
        val NETWORK_ERROR = NetworkFailure.ServerMiscommunication(TestNetworkException.generic)
    }

    private class Arrangement : ProteusCoreCryptoContextArrangement by ProteusCoreCryptoContextArrangementImpl() {
        val preKeyApi: PreKeyApi = mock(mode = MockMode.autoUnit)
        val proteusClient: ProteusClient = mock(mode = MockMode.autoUnit)
        val currentClientIdProvider: CurrentClientIdProvider = mock(mode = MockMode.autoUnit)
        val proteusClientProvider: ProteusClientProvider = mock(mode = MockMode.autoUnit)
        val prekeyDAO: PrekeyDAO = mock(mode = MockMode.autoUnit)
        val metadataDAO: MetadataDAO = mock(mode = MockMode.autoUnit)
        val clientDAO: ClientDAO = mock(mode = MockMode.autoUnit)

        private val preKeyRepository: PreKeyDataSource =
            PreKeyDataSource(
                preKeyApi = preKeyApi,
                proteusClientProvider = proteusClientProvider,
                provideCurrentClientId = currentClientIdProvider,
                prekeyDAO = prekeyDAO,
                clientDAO = clientDAO,
                metadataDAO = metadataDAO,
            )

        fun withGetRemoteUsersPreKeySuccess(preKeyMap: DomainToUserIdToClientsToPreKeyMap) = apply {
            everySuspend { preKeyApi.getUsersPreKey(any()) } returns
                NetworkResponse.Success(
                    ListPrekeysResponse(qualifiedUserClientPrekeys = preKeyMap),
                    emptyMap(),
                    200
                )
        }

        fun withGetRemoteUsersPreKeyFail(error: NetworkResponse.Error? = null) = apply {
            if (error == null) {
                everySuspend {
                    preKeyApi.getUsersPreKey(any())
                } returns NetworkResponse.Error(KaliumException.GenericError(IOException("offline")))
            } else {
                everySuspend {
                    preKeyApi.getUsersPreKey(any())
                } returns error
            }
        }

        fun withGenerateNewPreKeysSuccess(from: Int, count: Int, expected: List<PreKeyCrypto>) = apply {
            everySuspend {
                proteusClient.newPreKeys(from, count)
            } returns expected
        }

        fun withGenerateLastPreKeysSuccess(expected: PreKeyCrypto) = apply {
            everySuspend {
                proteusClient.newLastResortPreKey()
            } returns expected
        }

        fun withMarkInvalid() = apply {
            everySuspend {
                clientDAO.tryMarkInvalid(any())
            } returns Unit
        }

        fun withDoesSessionExist(result: Boolean) = apply {
            everySuspend {
                proteusContext.doesSessionExist(any())
            } returns result
        }

        fun withPreKeysOfClientsByQualifiedUsersSuccess(
            preKeyMap: NetworkResponse<ListPrekeysResponse>
        ) = apply {
            everySuspend {
                preKeyApi.getUsersPreKey(any())
            } returns preKeyMap
        }

        fun withCreateSession(throwable: Throwable) = apply {
            everySuspend {
                proteusContext.createSession(any(), any())
            } throws throwable
        }

        fun withGetClientAvailablePrekeysReturning(result: NetworkResponse<List<Int>>) = apply {
            everySuspend {
                preKeyApi.getClientAvailablePrekeys(any())
            } returns result
        }

        fun withUploadPrekeysReturning(result: NetworkResponse<Unit>) = apply {
            everySuspend {
                preKeyApi.uploadNewPrekeys(any(), any())
            } returns result
        }

        fun withCurrentClientIdReturning(result: Either<CoreFailure, ClientId>) = apply {
            everySuspend {
                currentClientIdProvider.invoke()
            } returns result
        }


        fun arrange(block: suspend Arrangement.() -> Unit) = let {
            everySuspend {
                proteusClientProvider.getOrCreate()
            } returns proteusClient
            runBlocking { block() }
            this to preKeyRepository
        }
    }
}
