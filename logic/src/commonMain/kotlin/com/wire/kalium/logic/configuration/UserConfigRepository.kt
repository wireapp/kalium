package com.wire.kalium.logic.configuration

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.configuration.notification.NotificationToken
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.wrapStorageRequest
import com.wire.kalium.persistence.client.UserConfigStorage
import kotlinx.serialization.SerialName

data class FileSharingEntity(val isFileSharingEnabled: Boolean?, val isStatusChanged: Boolean?)

interface UserConfigRepository {

    fun persistEnableLogging(enabled: Boolean): Either<StorageFailure, Unit>
    fun isLoggingEnabled(): Either<StorageFailure, Boolean>

    fun setFileSharingStatus(status: Boolean, isStatusChanged: Boolean?): Either<StorageFailure, Unit>
    fun isFileSharingEnabled(): Either<StorageFailure, FileSharingEntity>

}

class UserConfigDataSource(
    private val userConfigStorage: UserConfigStorage
) : UserConfigRepository {

    override fun persistEnableLogging(enabled: Boolean): Either<StorageFailure, Unit> =
        wrapStorageRequest { userConfigStorage.enableLogging(enabled) }

    override fun isLoggingEnabled(): Either<StorageFailure, Boolean> = wrapStorageRequest { userConfigStorage.isLoggingEnables() }

    override fun setFileSharingStatus(status: Boolean, isStatusChanged: Boolean?): Either<StorageFailure, Unit> =
        wrapStorageRequest { userConfigStorage.persistFileSharingStatus(status, isStatusChanged) }

    override fun isFileSharingEnabled(): Either<StorageFailure, FileSharingEntity> =
        wrapStorageRequest { userConfigStorage.isFileSharingEnabled() }.map {
            with(it) { FileSharingEntity(status, isStatusChanged) }
        }
}
