package com.wire.kalium.logic.feature.client

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.client.DeleteClientParam
import com.wire.kalium.logic.functional.suspending
import com.wire.kalium.network.exceptions.KaliumException

interface DeleteClientUseCase {
    suspend operator fun invoke(param: DeleteClientParam): DeleteClientResult
}

class DeleteClientUseCaseImpl(private val clientRepository: ClientRepository) : DeleteClientUseCase {
    override suspend operator fun invoke(param: DeleteClientParam): DeleteClientResult = (suspending {
        clientRepository.deleteClient(param)
    }.fold({ failure ->
        if (failure is NetworkFailure.ServerMiscommunication && failure.kaliumException is KaliumException.InvalidRequestError)
            DeleteClientResult.Failure.InvalidCredentials
        else
            DeleteClientResult.Failure.Generic(failure)
    }, {
        DeleteClientResult.Success
    }))
}

sealed class DeleteClientResult {
    object Success : DeleteClientResult()

    sealed class Failure : DeleteClientResult() {
        object InvalidCredentials : DeleteClientResult.Failure()
        class Generic(val genericFailure: CoreFailure) : Failure()
    }
}
