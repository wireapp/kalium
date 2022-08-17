package com.wire.kalium.logic.configuration

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.wrapStorageRequest
import com.wire.kalium.persistence.client.UserConfigStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface UserConfigRepository {
    fun persistEnableLogging(enabled: Boolean): Either<StorageFailure, Unit>
    fun isLoggingEnabled(): Either<StorageFailure, Boolean>
    fun isPersistentWebSocketConnectionEnabledFlow(): Flow<Either<StorageFailure, Boolean>>
    fun persistPersistentWebSocketConnectionStatus(status: Boolean): Either<StorageFailure, Unit>
    fun setFileSharingStatus(status: Boolean, isStatusChanged: Boolean?): Either<StorageFailure, Unit>
    fun isFileSharingEnabled(): Either<StorageFailure, FileSharingStatus>
    fun isFileSharingEnabledFlow(): Flow<Either<StorageFailure, FileSharingStatus>>
    fun isMLSEnabled(): Either<StorageFailure, Boolean>
    fun setMLSEnabled(enabled: Boolean): Either<StorageFailure, Unit>
}

class UserConfigDataSource(
    private val userConfigStorage: UserConfigStorage
) : UserConfigRepository {

    override fun persistEnableLogging(enabled: Boolean): Either<StorageFailure, Unit> =
        wrapStorageRequest { userConfigStorage.enableLogging(enabled) }

    override fun isLoggingEnabled(): Either<StorageFailure, Boolean> = wrapStorageRequest { userConfigStorage.isLoggingEnables() }

    override fun isPersistentWebSocketConnectionEnabledFlow(): Flow<Either<StorageFailure, Boolean>> =
        userConfigStorage.isPersistentWebSocketConnectionEnabledFlow().wrapStorageRequest()

    override fun persistPersistentWebSocketConnectionStatus(status: Boolean): Either<StorageFailure, Unit> =
        wrapStorageRequest { userConfigStorage.persistPersistentWebSocketConnectionStatus(status) }

    override fun setFileSharingStatus(status: Boolean, isStatusChanged: Boolean?): Either<StorageFailure, Unit> =
        wrapStorageRequest { userConfigStorage.persistFileSharingStatus(status, isStatusChanged) }

    override fun isFileSharingEnabled(): Either<StorageFailure, FileSharingStatus> =
        wrapStorageRequest { userConfigStorage.isFileSharingEnabled() }.map {
            with(it) { FileSharingStatus(status, isStatusChanged) }
        }

    override fun isFileSharingEnabledFlow(): Flow<Either<StorageFailure, FileSharingStatus>> =
        userConfigStorage.isFileSharingEnabledFlow()
            .wrapStorageRequest()
            .map {
                it.map { isFileSharingEnabledEntity ->
                    FileSharingStatus(
                        isFileSharingEnabledEntity.status,
                        isFileSharingEnabledEntity.isStatusChanged
                    )
                }
            }

    override fun isMLSEnabled(): Either<StorageFailure, Boolean> =
        wrapStorageRequest { userConfigStorage.isMLSEnabled() }

    override fun setMLSEnabled(enabled: Boolean): Either<StorageFailure, Unit> =
        wrapStorageRequest { userConfigStorage.enableMLS(enabled) }

}
