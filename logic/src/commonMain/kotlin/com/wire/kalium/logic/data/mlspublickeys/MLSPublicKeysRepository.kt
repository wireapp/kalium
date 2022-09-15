package com.wire.kalium.logic.data.mlspublickeys

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.logic.wrapStorageRequest
import com.wire.kalium.network.api.serverypublickey.MLSPublicKeyApi
import com.wire.kalium.persistence.daokaliumdb.MLSPublicKeysDAO


interface MLSPublicKeysRepository {
    suspend fun fetchMLSPublicKeysAndStore(serverConfigId: String): Either<CoreFailure, List<MLSPublicKey>>
    suspend fun storeKeys(keys: List<MLSPublicKey>, serverConfigId: String): Either<StorageFailure, Unit>
    suspend fun storeKey(key: MLSPublicKey, serverConfigId: String): Either<StorageFailure, Unit>
    suspend fun getKeys(): Either<StorageFailure, List<MLSPublicKey>>
    suspend fun getKeys(cipherSuite: Conversation.CipherSuite): Either<StorageFailure, List<MLSPublicKey>>
}

class MLSPublicKeysRepositoryImpl(
    private val mlsPublicKeyApi: MLSPublicKeyApi,
    private val mlsPublicKeysDAO: MLSPublicKeysDAO,
    private val userId: UserId,
    private val mapper: MLSPublicKeysMapper = MLSPublicKeysMapperImpl(),
    private val idMapper: IdMapper = MapperProvider.idMapper()
) : MLSPublicKeysRepository {
    override suspend fun fetchMLSPublicKeysAndStore(serverConfigId: String) =
        wrapApiRequest {
            mlsPublicKeyApi.getMLSPublicKeys()
        }.map {
            // TODO: getserver key here
            val publicKeys = mapper.fromDTO(it)
            storeKeys(publicKeys, serverConfigId)
            publicKeys

        }

    override suspend fun storeKeys(keys: List<MLSPublicKey>, serverConfigId: String) =
        wrapStorageRequest {
            keys.forEach {
                storeKey(it, serverConfigId)
            }
        }

    override suspend fun storeKey(key: MLSPublicKey, serverConfigId: String) =
        wrapStorageRequest {
            mlsPublicKeysDAO.insert(mapper.toEntity(key), serverConfigId)
        }

    override suspend fun getKeys() = wrapStorageRequest {
        mlsPublicKeysDAO.getKeys(idMapper.toDaoModel(userId)).map(mapper::fromEntity)
    }


    override suspend fun getKeys(cipherSuite: Conversation.CipherSuite) =
        wrapStorageRequest {
            getKeys().fold({
                emptyList()
            }, {
                it.filter { cipherSuite == cipherSuite }
            })
        }

}
