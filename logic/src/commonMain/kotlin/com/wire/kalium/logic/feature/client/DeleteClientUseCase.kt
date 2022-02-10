package com.wire.kalium.logic.feature.client

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.client.DeleteClientParam
import com.wire.kalium.logic.functional.Either

interface DeleteClientUseCase {
    suspend operator fun invoke(param: DeleteClientParam): Either<CoreFailure, Unit>
}

class DeleteClientUseCaseImpl(private val clientRepository: ClientRepository) : DeleteClientUseCase {
    override suspend operator fun invoke(param: DeleteClientParam) =
        clientRepository.deleteClient(param)
}
