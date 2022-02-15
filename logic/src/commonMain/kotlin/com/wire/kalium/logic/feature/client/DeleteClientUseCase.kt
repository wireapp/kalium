package com.wire.kalium.logic.feature.client

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.client.DeleteClientParam
import com.wire.kalium.logic.functional.suspending

interface DeleteClientUseCase {
    suspend operator fun invoke(param: DeleteClientParam): DeleteClientResult
}

class DeleteClientUseCaseImpl(private val clientRepository: ClientRepository) : DeleteClientUseCase {
    override suspend operator fun invoke(param: DeleteClientParam): DeleteClientResult = (suspending {
        clientRepository.deleteClient(param)
    }.fold({ failure ->
        DeleteClientResult.Failure.Generic(failure)
    }, {
        DeleteClientResult.Success
    }))!!
}

sealed class DeleteClientResult {
    object Success : DeleteClientResult()

    sealed class Failure : DeleteClientResult() {
        class Generic(val genericFailure: CoreFailure) : Failure()
    }
}
