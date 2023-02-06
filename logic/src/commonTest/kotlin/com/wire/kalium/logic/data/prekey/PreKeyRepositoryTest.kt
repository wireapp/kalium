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
import com.wire.kalium.logic.feature.ProteusClientProvider
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.network.api.base.authenticated.prekey.DomainToUserIdToClientsToPreKeyMap
import com.wire.kalium.network.api.base.authenticated.prekey.PreKeyApi
import com.wire.kalium.network.api.base.authenticated.prekey.PreKeyDTO
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.persistence.dao.PrekeyDAO
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

    private class Arrangement {

        @Mock
        val preKeyApi: PreKeyApi = mock(PreKeyApi::class)

        @Mock
        val proteusClient: ProteusClient = mock(ProteusClient::class)

        @Mock
        val proteusClientProvider: ProteusClientProvider = mock(ProteusClientProvider::class)

        @Mock
        val prekeyDAO: PrekeyDAO = mock(PrekeyDAO::class)

        private val preKeyRepository = PreKeyDataSource(preKeyApi, proteusClientProvider, prekeyDAO)

        init {
            given(proteusClientProvider)
                .suspendFunction(proteusClientProvider::getOrCreate)
                .whenInvoked()
                .thenReturn(proteusClient)
        }

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
