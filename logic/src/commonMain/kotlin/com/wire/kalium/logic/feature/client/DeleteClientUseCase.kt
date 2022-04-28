package com.wire.kalium.logic.feature.client

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.client.DeleteClientParam
import com.wire.kalium.logic.functional.suspending
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.exceptions.isInvalidCredentials
import com.wire.kalium.network.exceptions.isMissingAuth

interface DeleteClientUseCase {
    suspend operator fun invoke(param: DeleteClientParam): DeleteClientResult
}

class DeleteClientUseCaseImpl(private val clientRepository: ClientRepository) : DeleteClientUseCase {
    override suspend operator fun invoke(param: DeleteClientParam): DeleteClientResult = suspending {
        clientRepository.deleteClient(param)
    }.fold({ failure ->
        if (failure is NetworkFailure.ServerMiscommunication && failure.kaliumException is KaliumException.InvalidRequestError)
            when {
                failure.kaliumException.isInvalidCredentials() -> DeleteClientResult.Failure.InvalidCredentials
                failure.kaliumException.isMissingAuth() -> DeleteClientResult.Failure.PasswordAuthRequired
                else -> DeleteClientResult.Failure.Generic(failure)
            }
        else {
            DeleteClientResult.Failure.Generic(failure)
        }
    }, {
        DeleteClientResult.Success
    })
}

sealed class DeleteClientResult {
    object Success : DeleteClientResult()

    sealed class Failure : DeleteClientResult() {
        object InvalidCredentials : Failure()
        object PasswordAuthRequired : Failure()
        class Generic(val genericFailure: CoreFailure) : Failure()
    }
}
