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
import com.wire.kalium.network.api.model.GenericAPIErrorResponse
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.utils.NetworkResponse
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest

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
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.mlsPublicKeyApi.getMLSPublicKeys()
        }
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
        verifySuspend(VerifyMode.exactly(0)) {
            arrangement.mlsPublicKeyApi.getMLSPublicKeys()
        }
        assertIs<Either.Right<MLSPublicKeys>>(result).let {
            assertEquals(mlsPublicKeys, it.value)
        }
    }

    @Test
    fun givenNoKeysStoredAndFailedFetch_whenGettingKeys_thenReturnFailure() = runTest {
        // given
        val error = KaliumException.ServerError(GenericAPIErrorResponse(500, "error_message", "error_label"))
        val response = NetworkResponse.Error(error)
        val (arrangement, repository) = Arrangement(initialPublicKeys = null)
            .withGetMLSPublicKeysApiReturning(response)
            .arrange()
        // when
        val result = repository.getKeys()
        // then
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.mlsPublicKeyApi.getMLSPublicKeys()
        }
        assertIs<Either.Left<NetworkFailure.ServerMiscommunication>>(result).let {
            assertEquals(error, it.value.kaliumException)
        }
    }

    @Test
    fun givenNoKeysStored_whenGettingKeyForCipherSuite_thenFetchAndReturnKey() = runTest {
        // given
        val cipherSuite = CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519
        val keySignature = MLSPublicKeyType.ED25519
        val key = Base64.encode("some-key".encodeToByteArray())
        val response = NetworkResponse.Success(MLSPublicKeysDTO(mapOf(keySignature.value to key)), mapOf(), 200)
        val (arrangement, repository) = Arrangement(initialPublicKeys = null)
            .withGetMLSPublicKeysApiReturning(response)
            .withMapperFromCipherSuiteReturning(keySignature)
            .arrange()
        // when
        val result = repository.getKeyForCipherSuite(cipherSuite)
        // then
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.mlsPublicKeyApi.getMLSPublicKeys()
        }
        assertIs<Either.Right<ByteArray>>(result).let {
            assertContentEquals(Base64.decode(key), it.value)
        }
    }

    @Test
    fun givenKeysAlreadyStoredWithKeyForGivenCipherSuite_whenGettingKeyForCipherSuite_thenDoNotFetchKeysAndReturnKey() = runTest {
        // given
        val cipherSuite = CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519
        val keySignature = MLSPublicKeyType.ED25519
        val key = Base64.encode("some-key".encodeToByteArray())
        val mlsPublicKeys = MLSPublicKeys(mapOf(keySignature.value to key))
        val (arrangement, repository) = Arrangement(initialPublicKeys = mlsPublicKeys)
            .withMapperFromCipherSuiteReturning(keySignature)
            .withMapperFromCipherSuiteReturning(keySignature)
            .arrange()
        // when
        val result = repository.getKeyForCipherSuite(cipherSuite)
        // then
        verifySuspend(VerifyMode.exactly(0)) {
            arrangement.mlsPublicKeyApi.getMLSPublicKeys()
        }
        assertIs<Either.Right<ByteArray>>(result).let {
            assertContentEquals(Base64.decode(key), it.value)
        }
    }

    @Test
    fun givenKeysAlreadyStoredWithoutKeyForGivenCipherSuite_whenGettingKeyForCipherSuite_thenFetchKeysAgainAndReturnKey() = runTest {
        // given
        val cipherSuite = CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519
        val keySignature = MLSPublicKeyType.ED25519
        val key = Base64.encode("some-key".encodeToByteArray())
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
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.mlsPublicKeyApi.getMLSPublicKeys()
        }
        assertIs<Either.Right<ByteArray>>(result).let {
            assertContentEquals(Base64.decode(key), it.value)
        }
    }

    @Test
    fun givenKeysNotStoredAndFailedFetching_whenGettingKeyForCipherSuite_thenReturnFailure() = runTest {
        // given
        val cipherSuite = CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519
        val keySignature = MLSPublicKeyType.ED25519
        val error = KaliumException.ServerError(GenericAPIErrorResponse(500, "error_message", "error_label"))
        val response = NetworkResponse.Error(error)
        val (arrangement, repository) = Arrangement(initialPublicKeys = null)
            .withGetMLSPublicKeysApiReturning(response)
            .withMapperFromCipherSuiteReturning(keySignature)
            .arrange()
        // when
        val result = repository.getKeyForCipherSuite(cipherSuite)
        // then
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.mlsPublicKeyApi.getMLSPublicKeys()
        }
        assertIs<Either.Left<NetworkFailure.ServerMiscommunication>>(result).let {
            assertEquals(error, it.value.kaliumException)
        }
    }

    inner class Arrangement(private val initialPublicKeys: MLSPublicKeys? = null) {
        internal val mlsPublicKeyApi = mock<MLSPublicKeyApi>(mode = MockMode.autoUnit)
        internal val mlsPublicKeysMapper = mock<MLSPublicKeysMapper>(mode = MockMode.autoUnit)

        internal suspend fun withGetMLSPublicKeysApiReturning(result: NetworkResponse<MLSPublicKeysDTO>) = apply {
            everySuspend {
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
