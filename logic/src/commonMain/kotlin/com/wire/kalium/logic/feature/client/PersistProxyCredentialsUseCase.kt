package com.wire.kalium.logic.feature.client

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.configuration.ProxyCredentialsRepository
import com.wire.kalium.logic.functional.fold

/**
 * use case to persist the proxy credentials that been added from user while login, so it will be used
 * to authenticate the proxy for the rest of the API calls in the app
 */
interface PersistProxyCredentialsUseCase {
    suspend operator fun invoke(username: String, password: String): PersistProxyCredentialsResult

    sealed class PersistProxyCredentialsResult {
        object Success : PersistProxyCredentialsResult()
        sealed class Failure : PersistProxyCredentialsResult() {
            class Generic(val failure: StorageFailure) : Failure()
        }
    }

}

class PersistProxyCredentialsUseCaseImpl(
    private val proxyCredentialsRepository: ProxyCredentialsRepository
) : PersistProxyCredentialsUseCase {

    override suspend operator fun invoke(username: String, password: String): PersistProxyCredentialsUseCase.PersistProxyCredentialsResult =
        proxyCredentialsRepository.persistProxyCredentials(username, password).fold({
            PersistProxyCredentialsUseCase.PersistProxyCredentialsResult.Failure.Generic(it)
        }, {
            PersistProxyCredentialsUseCase.PersistProxyCredentialsResult.Success
        })

}


