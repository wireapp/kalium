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

package com.wire.kalium.logic.data.mlspublickeys

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.MLSFailure
import com.wire.kalium.common.error.wrapApiRequest
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.flatMapLeft
import com.wire.kalium.common.functional.left
import com.wire.kalium.common.functional.map
import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.common.functional.right
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.data.mls.CipherSuite
import com.wire.kalium.logic.data.mls.MLSPublicKeys
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.network.api.base.authenticated.serverpublickey.MLSPublicKeyApi
import io.ktor.util.decodeBase64Bytes
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

fun MLSPublicKeys?.getRemovalKey(
    cipherSuite: CipherSuite,
    mlsPublicKeysMapper: MLSPublicKeysMapper = MapperProvider.mlsPublicKeyMapper()
): Either<CoreFailure, ByteArray> {
    val key = this?.removal?.let { removalKeys ->
        val keySignature = mlsPublicKeysMapper.fromCipherSuite(cipherSuite)
        removalKeys[keySignature.value]
    } ?: return Either.Left(MLSFailure.Generic(NoKeyFoundException(cipherSuite.toString())))
    return key.decodeBase64Bytes().right()
}

class NoKeyFoundException(cipherSuite: String) : IllegalStateException("No key found for cipher suite $cipherSuite")

interface MLSPublicKeysRepository {
    suspend fun fetchKeys(): Either<CoreFailure, MLSPublicKeys>
    suspend fun getKeys(): Either<CoreFailure, MLSPublicKeys>
    suspend fun getKeyForCipherSuite(cipherSuite: CipherSuite): Either<CoreFailure, ByteArray>
}

class MLSPublicKeysRepositoryImpl(
    private val mlsPublicKeyApi: MLSPublicKeyApi,
    private val mlsPublicKeysMapper: MLSPublicKeysMapper = MapperProvider.mlsPublicKeyMapper(),
    initialPublicKeys: MLSPublicKeys? = null, // for testing purposes
) : MLSPublicKeysRepository {

    private val mutex = Mutex()
    private var publicKeys: MLSPublicKeys? = initialPublicKeys

    override suspend fun fetchKeys(): Either<CoreFailure, MLSPublicKeys> = mutex.withLock {
        executeLockedFetchKeysRequest()
    }

    private suspend fun executeLockedFetchKeysRequest() = wrapApiRequest {
        mlsPublicKeyApi.getMLSPublicKeys()
    }.map {
        MLSPublicKeys(removal = it.removal)
    }.onSuccess {
        publicKeys = it
    }

    override suspend fun getKeys(): Either<CoreFailure, MLSPublicKeys> = mutex.withLock {
        publicKeys?.right() ?: executeLockedFetchKeysRequest()
    }

    override suspend fun getKeyForCipherSuite(cipherSuite: CipherSuite): Either<CoreFailure, ByteArray> = mutex.withLock {
        publicKeys.getRemovalKey(cipherSuite, mlsPublicKeysMapper)
            .flatMapLeft {
                if (it is MLSFailure.Generic && it.rootCause is NoKeyFoundException) {
                    kaliumLogger.i("$TAG: No key found for cipher suite $cipherSuite, trying to fetch keys again")
                    executeLockedFetchKeysRequest()
                        .flatMap { newKeys ->
                            newKeys.getRemovalKey(cipherSuite, mlsPublicKeysMapper)
                                .onFailure {
                                    if (it is MLSFailure.Generic && it.rootCause is NoKeyFoundException) {
                                        kaliumLogger.e("$TAG: No key found after fetching again for cipher suite $cipherSuite")
                                    } else {
                                        kaliumLogger.e("$TAG: Failed to get key after fetching again for cipher suite $cipherSuite")
                                    }
                                }
                        }
                } else {
                    kaliumLogger.e("$TAG: Failed to get key for cipher suite $cipherSuite")
                    it.left()
                }
            }
    }

    companion object {
        private const val TAG = "MLSPublicKeysRepository"
    }
}
