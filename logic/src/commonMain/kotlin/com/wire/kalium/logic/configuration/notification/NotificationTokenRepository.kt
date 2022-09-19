package com.wire.kalium.logic.configuration.notification

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.wrapStorageRequest
import com.wire.kalium.persistence.client.TokenStorage

data class NotificationToken(val token: String, val transport: String)

interface NotificationTokenRepository {

    suspend fun persistNotificationToken(token: String, transport: String): Either<StorageFailure, Unit>
    suspend fun getNotificationToken(): Either<StorageFailure, NotificationToken>
}

class NotificationTokenDataSource(
    private val tokenStorage: TokenStorage
) : NotificationTokenRepository {

    override suspend fun persistNotificationToken(token: String, transport: String): Either<StorageFailure, Unit> =
        wrapStorageRequest { tokenStorage.saveToken(token, transport) }

    override suspend fun getNotificationToken(): Either<StorageFailure, NotificationToken> = wrapStorageRequest { tokenStorage.getToken() }.map {
        with(it) { NotificationToken(token, transport) }
    }
}
