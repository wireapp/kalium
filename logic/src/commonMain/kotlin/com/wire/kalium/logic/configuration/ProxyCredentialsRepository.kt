package com.wire.kalium.logic.configuration

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.wrapStorageRequest
import com.wire.kalium.persistence.client.ProxyCredentialsStorage

interface ProxyCredentialsRepository {
    fun persist(username: String, password: String): Either<StorageFailure, Unit>
}

class ProxyCredentialsDataSource(
    private val proxyCredentialsStorage: ProxyCredentialsStorage
) : ProxyCredentialsRepository {

    override fun persist(username: String, password: String): Either<StorageFailure, Unit> =
        wrapStorageRequest { proxyCredentialsStorage.persist(username, password) }
}
