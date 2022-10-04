package com.wire.kalium.logic.feature.client

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.client.Client
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.feature.CurrentClientIdProvider
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.getOrNull

interface SelfClientsUseCase {
    suspend operator fun invoke(): SelfClientsResult
}

class SelfClientsUseCaseImpl(
    private val clientRepository: ClientRepository,
    private val provideClientId: CurrentClientIdProvider,
) : SelfClientsUseCase {
    override suspend fun invoke(): SelfClientsResult = clientRepository.selfListOfClients().fold(
        { SelfClientsResult.Failure.Generic(it) },
        { clients ->
            val currentClientId = provideClientId()
            SelfClientsResult.Success(
                clients = clients.sortedByDescending { it.registrationTime },
                currentClient = clients.firstOrNull { it.id == currentClientId.getOrNull() })
        }
    )
}


sealed class SelfClientsResult {
    data class Success(val clients: List<Client>, val currentClient: Client?) : SelfClientsResult()

    sealed class Failure : SelfClientsResult() {
        class Generic(val genericFailure: CoreFailure) : Failure()
    }
}

