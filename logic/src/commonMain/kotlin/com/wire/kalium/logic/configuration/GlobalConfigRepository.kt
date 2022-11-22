package com.wire.kalium.logic.configuration

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.wrapStorageRequest
import com.wire.kalium.persistence.config.GlobalAppConfigStorage

interface GlobalConfigRepository {
    fun persistEnableLogging(enabled: Boolean): Either<StorageFailure, Unit>
    fun isLoggingEnabled(): Either<StorageFailure, Boolean>
}

internal class GlobalConfigDataSource internal constructor(
    private val globalAppConfigStorage: GlobalAppConfigStorage
) : GlobalConfigRepository {

    override fun persistEnableLogging(enabled: Boolean): Either<StorageFailure, Unit> =
        wrapStorageRequest { globalAppConfigStorage.enableLogging(enabled) }

    override fun isLoggingEnabled(): Either<StorageFailure, Boolean> =
        wrapStorageRequest { globalAppConfigStorage.isLoggingEnables() }
}
