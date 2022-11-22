package com.wire.kalium.logic.data.prekey

import com.wire.kalium.cryptography.PreKeyCrypto
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.ProteusFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.feature.ProteusClientProvider
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.logic.wrapCryptoRequest
import com.wire.kalium.logic.wrapStorageRequest
import com.wire.kalium.network.api.base.authenticated.prekey.PreKeyApi
import com.wire.kalium.network.api.base.authenticated.prekey.PreKeyDTO
import com.wire.kalium.persistence.dao.PrekeyDAO

interface PreKeyRepository {
    suspend fun preKeysOfClientsByQualifiedUsers(
        qualifiedIdsMap: Map<UserId, List<ClientId>>
    ): Either<NetworkFailure,  Map<String, Map<String, Map<String, PreKeyDTO?>>>>

    suspend fun generateNewPreKeys(firstKeyId: Int, keysCount: Int): Either<CoreFailure, List<PreKeyCrypto>>
    suspend fun generateNewLastKey(): Either<ProteusFailure, PreKeyCrypto>
    suspend fun lastPreKeyId(): Either<StorageFailure, Int>
    suspend fun updateOTRLastPreKeyId(newId: Int): Either<StorageFailure, Unit>
    suspend fun forceInsertPrekeyId(newId: Int): Either<StorageFailure, Unit>
}

class PreKeyDataSource(
    private val preKeyApi: PreKeyApi,
    private val proteusClientProvider: ProteusClientProvider,
    private val prekeyDAO: PrekeyDAO,
    private val preKeyListMapper: PreKeyListMapper = MapperProvider.preKeyListMapper()
) : PreKeyRepository {
    override suspend fun preKeysOfClientsByQualifiedUsers(
        qualifiedIdsMap: Map<UserId, List<ClientId>>
    ): Either<NetworkFailure,  Map<String, Map<String, Map<String, PreKeyDTO?>>>> = wrapApiRequest {
        preKeyApi.getUsersPreKey(preKeyListMapper.toRemoteClientPreKeyInfoTo(qualifiedIdsMap))
    }

    override suspend fun generateNewPreKeys(
        firstKeyId: Int,
        keysCount: Int
    ): Either<ProteusFailure, List<PreKeyCrypto>> =
        wrapCryptoRequest { proteusClientProvider.getOrCreate().newPreKeys(firstKeyId, keysCount) }

    override suspend fun generateNewLastKey(): Either<ProteusFailure, PreKeyCrypto> =
        wrapCryptoRequest { proteusClientProvider.getOrCreate().newLastPreKey()
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
