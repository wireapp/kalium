package com.wire.kalium.logic.data.notification

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.wrapStorageRequest
import com.wire.kalium.persistence.dao.MetadataDAO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface PushTokenRepository {
    /**
     * Method responsible to observe the flag indicating if the firebase token should be registered via the ClientRepository.registerToken
     * next time the token changes.
     * @return [Flow] of [Boolean] that indicates if the firebase token should be registered.
     */
    suspend fun observeUpdateFirebaseTokenFlag(): Flow<Boolean>

    /**
     * Method responsible to set the flag indicating if the firebase token should be registered via the ClientRepository.registerToken
     */
    suspend fun setUpdateFirebaseTokenFlag(shouldUpdate: Boolean): Either<StorageFailure, Unit>
}

class PushTokenDataSource internal constructor(private val metadataDAO: MetadataDAO) : PushTokenRepository {

    override suspend fun setUpdateFirebaseTokenFlag(shouldUpdate: Boolean) =
        wrapStorageRequest { metadataDAO.insertValue(shouldUpdate.toString(), SHOULD_UPDATE_FIREBASE_TOKEN_KEY) }

    override suspend fun observeUpdateFirebaseTokenFlag() = metadataDAO.valueByKeyFlow(SHOULD_UPDATE_FIREBASE_TOKEN_KEY)
        .map {
            // if the flag is absent (null, or empty) it means it's a new user
            // so we need to register Firebase token for such a user too
            it == null || it.isEmpty() || it.toBoolean()
        }

    companion object {
        const val SHOULD_UPDATE_FIREBASE_TOKEN_KEY = "shouldUpdateFirebaseCloudToken"
    }
}
