package com.wire.kalium.logic.data.prekey

import com.wire.kalium.cryptography.PreKeyCrypto
import com.wire.kalium.cryptography.ProteusClient
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.ProteusFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.logic.wrapCryptoRequest
import com.wire.kalium.logic.wrapStorageRequest
import com.wire.kalium.network.api.base.authenticated.prekey.PreKeyApi
import com.wire.kalium.persistence.dao.PrekeyDAO

interface PreKeyRepository {
    suspend fun preKeysOfClientsByQualifiedUsers(
        qualifiedIdsMap: Map<UserId, List<ClientId>>
    ): Either<NetworkFailure, List<QualifiedUserPreKeyInfo>>

    suspend fun generateNewPreKeys(firstKeyId: Int, keysCount: Int): Either<CoreFailure, List<PreKeyCrypto>>
    fun generateNewLastKey(): Either<ProteusFailure, PreKeyCrypto>
    suspend fun lastPreKeyId(): Either<StorageFailure, Int>
    suspend fun updateOTRLastPreKeyId(newId: Int): Either<StorageFailure, Unit>
    suspend fun forceInsertPrekeyId(newId: Int): Either<StorageFailure, Unit>
}

class PreKeyDataSource(
    private val preKeyApi: PreKeyApi,
    private val proteusClient: ProteusClient,
    private val prekeyDAO: PrekeyDAO,
    private val preKeyListMapper: PreKeyListMapper = MapperProvider.preKeyListMapper()
) : PreKeyRepository {
    override suspend fun preKeysOfClientsByQualifiedUsers(
        qualifiedIdsMap: Map<UserId, List<ClientId>>
    ): Either<NetworkFailure, List<QualifiedUserPreKeyInfo>> = wrapApiRequest {
        preKeyApi.getUsersPreKey(preKeyListMapper.toRemoteClientPreKeyInfoTo(qualifiedIdsMap))
    }.map { preKeyListMapper.fromRemoteQualifiedPreKeyInfoMap(it) }

    override suspend fun generateNewPreKeys(
        firstKeyId: Int,
        keysCount: Int
    ): Either<ProteusFailure, List<PreKeyCrypto>> =
        wrapCryptoRequest { proteusClient.newPreKeys(firstKeyId, keysCount) }

    override fun generateNewLastKey(): Either<ProteusFailure, PreKeyCrypto> = wrapCryptoRequest {
        proteusClient.newLastPreKey()
    }

    override suspend fun lastPreKeyId(): Either<StorageFailure, Int> = wrapStorageRequest {
        prekeyDAO.lastOTRPrekeyId()
    }

    override suspend fun updateOTRLastPreKeyId(newId: Int): Either<StorageFailure, Unit> = wrapStorageRequest {
        prekeyDAO.updateOTRLastPrekeyId(newId)
    }

    override suspend fun forceInsertPrekeyId(newId: Int): Either<StorageFailure, Unit> = wrapStorageRequest {
        prekeyDAO.forceInsertOTRLastPrekeyId(newId)
    }

}
