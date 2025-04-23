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

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.MLSFailure
import com.wire.kalium.logic.data.mls.CipherSuite
import com.wire.kalium.logic.data.mls.MLSPublicKeys
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.functional.right
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.network.api.base.authenticated.serverpublickey.MLSPublicKeyApi
import io.ktor.util.decodeBase64Bytes
import io.mockative.Mockable

fun MLSPublicKeys.getRemovalKey(cipherSuite: CipherSuite): Either<CoreFailure, ByteArray> {
    val mlsPublicKeysMapper: MLSPublicKeysMapper = MapperProvider.mlsPublicKeyMapper()
    val keySignature = mlsPublicKeysMapper.fromCipherSuite(cipherSuite)
    val key = this.removal?.let { removalKeys ->
        removalKeys[keySignature.value]
    } ?: return Either.Left(MLSFailure.Generic(IllegalStateException("No key found for cipher suite $cipherSuite")))
    return key.decodeBase64Bytes().right()
}

@Mockable
interface MLSPublicKeysRepository {
    suspend fun fetchKeys(): Either<CoreFailure, MLSPublicKeys>
    suspend fun getKeys(): Either<CoreFailure, MLSPublicKeys>
    suspend fun getKeyForCipherSuite(cipherSuite: CipherSuite): Either<CoreFailure, ByteArray>
}

class MLSPublicKeysRepositoryImpl(
    private val mlsPublicKeyApi: MLSPublicKeyApi,
) : MLSPublicKeysRepository {

    // TODO: make it thread safe
    var publicKeys: MLSPublicKeys? = null

    override suspend fun fetchKeys() =
        wrapApiRequest {
            mlsPublicKeyApi.getMLSPublicKeys()
        }.map {
            MLSPublicKeys(removal = it.removal)
        }

    override suspend fun getKeys(): Either<CoreFailure, MLSPublicKeys> {
        return publicKeys?.let { Either.Right(it) } ?: fetchKeys()
    }

    override suspend fun getKeyForCipherSuite(cipherSuite: CipherSuite): Either<CoreFailure, ByteArray> {
        return getKeys().flatMap { serverPublicKeys ->
            serverPublicKeys.getRemovalKey(cipherSuite)
        }
    }
}
