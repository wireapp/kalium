package com.wire.kalium.logic.configuration

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.wrapStorageRequest
import com.wire.kalium.persistence.client.UserConfigStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface UserConfigRepository {
    suspend fun persistEnableLogging(enabled: Boolean): Either<StorageFailure, Unit>
    suspend fun isLoggingEnabled(): Either<StorageFailure, Boolean>
    fun isPersistentWebSocketConnectionEnabledFlow(): Flow<Either<StorageFailure, Boolean>>
    suspend fun persistPersistentWebSocketConnectionStatus(status: Boolean): Either<StorageFailure, Unit>
    suspend fun setFileSharingStatus(status: Boolean, isStatusChanged: Boolean?): Either<StorageFailure, Unit>
    suspend fun isFileSharingEnabled(): Either<StorageFailure, FileSharingStatus>
    fun isFileSharingEnabledFlow(): Flow<Either<StorageFailure, FileSharingStatus>>
    suspend fun isMLSEnabled(): Either<StorageFailure, Boolean>
    suspend fun setMLSEnabled(enabled: Boolean): Either<StorageFailure, Unit>
    suspend fun setClassifiedDomainsStatus(enabled: Boolean, domains: List<String>): Either<StorageFailure, Unit>
    fun getClassifiedDomainsStatus(): Flow<Either<StorageFailure, ClassifiedDomainsStatus>>
}

class UserConfigDataSource(
    private val userConfigStorage: UserConfigStorage
) : UserConfigRepository {

    override suspend fun persistEnableLogging(enabled: Boolean): Either<StorageFailure, Unit> =
        wrapStorageRequest { userConfigStorage.enableLogging(enabled) }

    override suspend fun isLoggingEnabled(): Either<StorageFailure, Boolean> = wrapStorageRequest { userConfigStorage.isLoggingEnables() }

    override fun isPersistentWebSocketConnectionEnabledFlow(): Flow<Either<StorageFailure, Boolean>> =
        userConfigStorage.isPersistentWebSocketConnectionEnabledFlow().wrapStorageRequest()

    override suspend fun persistPersistentWebSocketConnectionStatus(status: Boolean): Either<StorageFailure, Unit> =
        wrapStorageRequest { userConfigStorage.persistPersistentWebSocketConnectionStatus(status) }

    override suspend fun setFileSharingStatus(status: Boolean, isStatusChanged: Boolean?): Either<StorageFailure, Unit> =
        wrapStorageRequest { userConfigStorage.persistFileSharingStatus(status, isStatusChanged) }

    override suspend fun isFileSharingEnabled(): Either<StorageFailure, FileSharingStatus> =
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

    override suspend fun isMLSEnabled(): Either<StorageFailure, Boolean> =
        wrapStorageRequest { userConfigStorage.isMLSEnabled() }

    override suspend fun setMLSEnabled(enabled: Boolean): Either<StorageFailure, Unit> =
        wrapStorageRequest { userConfigStorage.enableMLS(enabled) }

    override suspend fun setClassifiedDomainsStatus(enabled: Boolean, domains: List<String>) =
        wrapStorageRequest { userConfigStorage.persistClassifiedDomainsStatus(enabled, domains) }

    override fun getClassifiedDomainsStatus(): Flow<Either<StorageFailure, ClassifiedDomainsStatus>> =
        userConfigStorage.isClassifiedDomainsEnabledFlow().wrapStorageRequest().map {
            it.map { classifiedDomain ->
                ClassifiedDomainsStatus(classifiedDomain.status, classifiedDomain.trustedDomains)
            }
        }

}
