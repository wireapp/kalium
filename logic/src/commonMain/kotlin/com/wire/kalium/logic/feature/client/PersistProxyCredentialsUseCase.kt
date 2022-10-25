package com.wire.kalium.logic.feature.client

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.configuration.ProxyCredentialsRepository
import com.wire.kalium.logic.functional.fold

/**
 * use case to persist the proxy credentials that been added from user while login, so it will be used
 * to authenticate the proxy for the rest of the API calls in the app
 */
interface PersistProxyCredentialsUseCase {
    suspend operator fun invoke(username: String, password: String): Result
}

class PersistProxyCredentialsUseCaseImpl(
    private val proxyCredentialsRepository: ProxyCredentialsRepository
) : PersistProxyCredentialsUseCase {

    override suspend operator fun invoke(username: String, password: String): Result =
        proxyCredentialsRepository.persistProxyCredentials(username, password).fold({
            Result.Failure.Generic(it)
        }, {
            Result.Success
        })
}

sealed class Result {
    object Success : Result()
    sealed class Failure : Result() {
        class Generic(val failure: StorageFailure) : Failure()
    }
}
