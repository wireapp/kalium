package com.wire.kalium.logic.feature.client

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.functional.fold


interface PersistProxyCredentialsUseCase {
    suspend operator fun invoke(username: String, password: String): Result
}

class PersistProxyCredentialsUseCaseImpl(
    private val clientRepository: ClientRepository
) : PersistProxyCredentialsUseCase {

    override suspend operator fun invoke(username: String, password: String): Result =
        clientRepository.persistProxyCredentials(username, password).fold({
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
