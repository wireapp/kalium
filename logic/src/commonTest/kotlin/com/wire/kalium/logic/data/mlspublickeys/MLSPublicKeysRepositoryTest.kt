/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
package com.wire.kalium.logic.data.mlspublickeys

import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.mls.CipherSuite
import com.wire.kalium.logic.data.mls.MLSPublicKeys
import com.wire.kalium.network.api.authenticated.serverpublickey.MLSPublicKeysDTO
import com.wire.kalium.network.api.base.authenticated.serverpublickey.MLSPublicKeyApi
import com.wire.kalium.network.api.model.ErrorResponse
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.utils.NetworkResponse
import io.ktor.util.decodeBase64Bytes
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.every
import io.mockative.mock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs

class MLSPublicKeysRepositoryTest {

    @Test
    fun givenNoKeysStored_whenGettingKeys_thenFetchAndReturnKeys() = runTest {
        // given
        val mlsPublicKeys = MLSPublicKeys(mapOf("keySignature" to "key"))
        val response = NetworkResponse.Success(MLSPublicKeysDTO(mlsPublicKeys.removal), mapOf(), 200)
        val (arrangement, repository) = Arrangement(initialPublicKeys = null)
            .withGetMLSPublicKeysApiReturning(response)
            .arrange()
        // when
        repository.getKeys()
        // then
        coVerify {
            arrangement.mlsPublicKeyApi.getMLSPublicKeys()
        }.wasInvoked(exactly = 1)
        assertIs<Either.Right<MLSPublicKeys>>(repository.getKeys()).let {
            assertEquals(mlsPublicKeys, it.value)
        }
    }

    @Test
    fun givenKeysAlreadyStored_whenGettingKeys_thenDoNotFetchAndReturnKeys() = runTest {
        // given
        val mlsPublicKeys = MLSPublicKeys(mapOf())
        val (arrangement, repository) = Arrangement(initialPublicKeys = mlsPublicKeys)
            .arrange()
        // when
        val result = repository.getKeys()
        // then
        coVerify {
            arrangement.mlsPublicKeyApi.getMLSPublicKeys()
        }.wasNotInvoked()
        assertIs<Either.Right<MLSPublicKeys>>(result).let {
            assertEquals(mlsPublicKeys, it.value)
        }
    }

    @Test
    fun givenNoKeysStoredAndFailedFetch_whenGettingKeys_thenReturnFailure() = runTest {
        // given
        val error = KaliumException.ServerError(ErrorResponse(500, "error_message", "error_label"))
        val response = NetworkResponse.Error(error)
        val (arrangement, repository) = Arrangement(initialPublicKeys = null)
            .withGetMLSPublicKeysApiReturning(response)
            .arrange()
        // when
        val result = repository.getKeys()
        // then
        coVerify {
            arrangement.mlsPublicKeyApi.getMLSPublicKeys()
        }.wasInvoked(exactly = 1)
        assertIs<Either.Left<NetworkFailure.ServerMiscommunication>>(result).let {
            assertEquals(error, it.value.kaliumException)
        }
    }

    @Test
    fun givenNoKeysStored_whenGettingKeyForCipherSuite_thenFetchAndReturnKey() = runTest {
        // given
        val cipherSuite = CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519
        val keySignature = MLSPublicKeyType.ED25519
        val key = "some-key"
        val response = NetworkResponse.Success(MLSPublicKeysDTO(mapOf(keySignature.value to key)), mapOf(), 200)
        val (arrangement, repository) = Arrangement(initialPublicKeys = null)
            .withGetMLSPublicKeysApiReturning(response)
            .withMapperFromCipherSuiteReturning(keySignature)
            .arrange()
        // when
        val result = repository.getKeyForCipherSuite(cipherSuite)
        // then
        coVerify {
            arrangement.mlsPublicKeyApi.getMLSPublicKeys()
        }.wasInvoked(exactly = 1)
        assertIs<Either.Right<ByteArray>>(result).let {
            assertContentEquals(key.decodeBase64Bytes(), it.value)
        }
    }

    @Test
    fun givenKeysAlreadyStoredWithKeyForGivenCipherSuite_whenGettingKeyForCipherSuite_thenDoNotFetchKeysAndReturnKey() = runTest {
        // given
        val cipherSuite = CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519
        val keySignature = MLSPublicKeyType.ED25519
        val key = "some-key"
        val mlsPublicKeys = MLSPublicKeys(mapOf(keySignature.value to key))
        val (arrangement, repository) = Arrangement(initialPublicKeys = mlsPublicKeys)
            .withMapperFromCipherSuiteReturning(keySignature)
            .withMapperFromCipherSuiteReturning(keySignature)
            .arrange()
        // when
        val result = repository.getKeyForCipherSuite(cipherSuite)
        // then
        coVerify {
            arrangement.mlsPublicKeyApi.getMLSPublicKeys()
        }.wasNotInvoked()
        assertIs<Either.Right<ByteArray>>(result).let {
            assertContentEquals(key.decodeBase64Bytes(), it.value)
        }
    }

    @Test
    fun givenKeysAlreadyStoredWithoutKeyForGivenCipherSuite_whenGettingKeyForCipherSuite_thenFetchKeysAgainAndReturnKey() = runTest {
        // given
        val cipherSuite = CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519
        val keySignature = MLSPublicKeyType.ED25519
        val key = "some-key"
        val mlsPublicKeysWithoutKey = MLSPublicKeys(mapOf())
        val mlsPublicKeysWithKey = MLSPublicKeys(mapOf(keySignature.value to key))
        val response = NetworkResponse.Success(MLSPublicKeysDTO(mlsPublicKeysWithKey.removal), mapOf(), 200)
        val (arrangement, repository) = Arrangement(initialPublicKeys = mlsPublicKeysWithoutKey)
            .withGetMLSPublicKeysApiReturning(response)
            .withMapperFromCipherSuiteReturning(keySignature)
            .arrange()
        // when
        val result = repository.getKeyForCipherSuite(cipherSuite)
        // then
        coVerify {
            arrangement.mlsPublicKeyApi.getMLSPublicKeys()
        }.wasInvoked(exactly = 1)
        assertIs<Either.Right<ByteArray>>(result).let {
            assertContentEquals(key.decodeBase64Bytes(), it.value)
        }
    }

    @Test
    fun givenKeysNotStoredAndFailedFetching_whenGettingKeyForCipherSuite_thenReturnFailure() = runTest {
        // given
        val cipherSuite = CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519
        val keySignature = MLSPublicKeyType.ED25519
        val error = KaliumException.ServerError(ErrorResponse(500, "error_message", "error_label"))
        val response = NetworkResponse.Error(error)
        val (arrangement, repository) = Arrangement(initialPublicKeys = null)
            .withGetMLSPublicKeysApiReturning(response)
            .withMapperFromCipherSuiteReturning(keySignature)
            .arrange()
        // when
        val result = repository.getKeyForCipherSuite(cipherSuite)
        // then
        coVerify {
            arrangement.mlsPublicKeyApi.getMLSPublicKeys()
        }.wasInvoked(exactly = 1)
        assertIs<Either.Left<NetworkFailure.ServerMiscommunication>>(result).let {
            assertEquals(error, it.value.kaliumException)
        }
    }

    inner class Arrangement(private val initialPublicKeys: MLSPublicKeys? = null) {
        internal val mlsPublicKeyApi: MLSPublicKeyApi = mock(MLSPublicKeyApi::class)
        internal val mlsPublicKeysMapper: MLSPublicKeysMapper = mock(MLSPublicKeysMapper::class)

        internal suspend fun withGetMLSPublicKeysApiReturning(result: NetworkResponse<MLSPublicKeysDTO>) = apply {
            coEvery {
                mlsPublicKeyApi.getMLSPublicKeys()
            }.returns(result)
        }

        internal fun withMapperFromCipherSuiteReturning(result: MLSPublicKeyType) = apply {
            every {
                mlsPublicKeysMapper.fromCipherSuite(any())
            }.returns(result)
        }

        internal fun arrange() = this to MLSPublicKeysRepositoryImpl(mlsPublicKeyApi, mlsPublicKeysMapper, initialPublicKeys)
    }
}
