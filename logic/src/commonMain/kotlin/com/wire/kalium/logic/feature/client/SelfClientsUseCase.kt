package com.wire.kalium.logic.feature.client

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.client.Client
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.functional.suspending

interface SelfClientsUseCase {
    suspend operator fun invoke(): SelfClientsResult
}

class SelfClientsUseCaseImpl(private val clientRepository: ClientRepository) : SelfClientsUseCase {
    override suspend fun invoke(): SelfClientsResult = suspending {
        clientRepository.selfListOfClients()
    }.fold(
        {
            SelfClientsResult.Failure.Generic(it)
        }, {
            SelfClientsResult.Success(it)
        }
    )!!
}


sealed class SelfClientsResult {
    data class Success(val clients: List<Client>) : SelfClientsResult()

    sealed class Failure : SelfClientsResult() {
        class Generic(val genericFailure: CoreFailure) : Failure()
    }
}

