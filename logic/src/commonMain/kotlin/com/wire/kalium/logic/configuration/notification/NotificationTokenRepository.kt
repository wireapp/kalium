package com.wire.kalium.logic.configuration.notification

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.wrapStorageRequest
import com.wire.kalium.persistence.client.TokenStorage

data class NotificationToken(val token: String, val transport: String, val applicationId: String)

interface NotificationTokenRepository {

    fun persistNotificationToken(token: String, transport: String, applicationId: String): Either<StorageFailure, Unit>
    fun getNotificationToken(): Either<StorageFailure, NotificationToken>
}

class NotificationTokenDataSource(
    private val tokenStorage: TokenStorage
) : NotificationTokenRepository {

    override fun persistNotificationToken(token: String, transport: String, applicationId: String): Either<StorageFailure, Unit> =
        wrapStorageRequest { tokenStorage.saveToken(token, transport, applicationId) }

    override fun getNotificationToken(): Either<StorageFailure, NotificationToken> = wrapStorageRequest { tokenStorage.getToken() }.map {
        with(it) { NotificationToken(token, transport, applicationId) }
    }
}
