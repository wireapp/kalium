package com.wire.kalium.logic.feature.client

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.client.DeleteClientParam
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.exceptions.isBadRequest
import com.wire.kalium.network.exceptions.isInvalidCredentials
import com.wire.kalium.network.exceptions.isMissingAuth

interface DeleteClientUseCase {
    suspend operator fun invoke(param: DeleteClientParam): DeleteClientResult
}

class DeleteClientUseCaseImpl(private val clientRepository: ClientRepository) : DeleteClientUseCase {
    override suspend operator fun invoke(param: DeleteClientParam): DeleteClientResult =
        clientRepository.deleteClient(param).fold(
            {
                handleError(it)
            }, {
                DeleteClientResult.Success
            })

    private fun handleError(failure: NetworkFailure): DeleteClientResult.Failure =
        if (failure is NetworkFailure.ServerMiscommunication && failure.kaliumException is KaliumException.InvalidRequestError)
            when {
                failure.kaliumException.isInvalidCredentials() -> DeleteClientResult.Failure.InvalidCredentials
                failure.kaliumException.isMissingAuth() -> DeleteClientResult.Failure.PasswordAuthRequired
                failure.kaliumException.isBadRequest() -> DeleteClientResult.Failure.InvalidCredentials
                else -> DeleteClientResult.Failure.Generic(failure)
            }
        else {
            DeleteClientResult.Failure.Generic(failure)
        }
}

sealed class DeleteClientResult {
    object Success : DeleteClientResult()

    sealed class Failure : DeleteClientResult() {
        object InvalidCredentials : Failure()
        object PasswordAuthRequired : Failure()
        class Generic(val genericFailure: CoreFailure) : Failure()
    }
}
