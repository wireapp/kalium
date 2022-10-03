package com.wire.kalium.logic.data.mlspublickeys

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.network.api.base.authenticated.serverpublickey.MLSPublicKeyApi

interface MLSPublicKeysRepository {
    suspend fun fetchKeys(): Either<CoreFailure, List<MLSPublicKey>>
    suspend fun getKeys(): Either<CoreFailure, List<MLSPublicKey>>
}

class MLSPublicKeysRepositoryImpl(
    private val mlsPublicKeyApi: MLSPublicKeyApi,
    private val mapper: MLSPublicKeysMapper = MLSPublicKeysMapperImpl()
) : MLSPublicKeysRepository {

    var publicKeys: List<MLSPublicKey>? = null

    override suspend fun fetchKeys() =
        wrapApiRequest {
            mlsPublicKeyApi.getMLSPublicKeys()
        }.map {
            val keys = mapper.fromDTO(it)
            publicKeys = keys
            keys
        }

    override suspend fun getKeys(): Either<CoreFailure, List<MLSPublicKey>> {
        return publicKeys?.let { Either.Right(it) } ?: fetchKeys()
    }

}
