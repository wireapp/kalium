package com.wire.kalium.logic.data.prekey

import com.wire.kalium.cryptography.PreKeyCrypto
import com.wire.kalium.cryptography.ProteusClient
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.network.api.prekey.DomainToUserIdToClientsToPreKeyMap
import com.wire.kalium.network.api.prekey.PreKeyApi
import com.wire.kalium.network.api.prekey.PreKeyDTO
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.utils.NetworkResponse
import io.ktor.utils.io.errors.IOException
import io.mockative.Mock
import io.mockative.any
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PreKeyRepositoryTest {

    @Test
    fun givenGetUserPreKeySuccess_whenGetUserPreKey_thenSuccess() = runTest {
        val expected: DomainToUserIdToClientsToPreKeyMap =
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
            .function(arrange.proteusClient::newLastPreKey)
            .wasInvoked(exactly = once)
    }

    private class Arrangement {

        @Mock
        val preKeyApi: PreKeyApi = mock(PreKeyApi::class)

        @Mock
        val proteusClient: ProteusClient = mock(ProteusClient::class)

        private val preKeyRepository = PreKeyDataSource(preKeyApi, proteusClient)

        fun withGetRemoteUsersPreKeySuccess(preKeyMap: DomainToUserIdToClientsToPreKeyMap) = apply {
            given(preKeyApi)
                .suspendFunction(preKeyApi::getUsersPreKey)
                .whenInvokedWith(any())
                .then { NetworkResponse.Success(preKeyMap, emptyMap(), 200) }
        }

        fun withGetRemoteUsersPreKeyFail() = apply {
            given(preKeyApi)
                .suspendFunction(preKeyApi::getUsersPreKey)
                .whenInvokedWith(any())
                .then { NetworkResponse.Error(KaliumException.GenericError(IOException("offline"))) }
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

        fun arrange() = this to preKeyRepository
    }
}
