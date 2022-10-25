package com.wire.kalium.logic.feature.client

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.client.Client
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.functional.fold

/**
 * Checks if the given ClientId is currently registered and persists it in the local storage if so, otherwise returns failure.
 */
interface PersistRegisteredClientIdUseCase {

    /**
     * @param clientId client id to be persisted
     * @return success if the given id is registered, failure otherwise
     */
    suspend operator fun invoke(clientId: ClientId): PersistRegisteredClientIdResult
}

internal class PersistRegisteredClientIdUseCaseImpl(
    private val clientRepository: ClientRepository
) : PersistRegisteredClientIdUseCase {

    override suspend fun invoke(clientId: ClientId): PersistRegisteredClientIdResult {
        return clientRepository.selfListOfClients()
            .fold({
                PersistRegisteredClientIdResult.Failure.Generic(it)
            }, { listOfClients ->
                val client = listOfClients.firstOrNull { it.id == clientId }
                if (client != null) {
                    clientRepository.persistClientId(client.id)
                    PersistRegisteredClientIdResult.Success(client)
                } else {
                    PersistRegisteredClientIdResult.Failure.ClientNotRegistered
                }
            })
    }
}

sealed class PersistRegisteredClientIdResult {
    class Success(val client: Client) : PersistRegisteredClientIdResult()

    sealed class Failure : PersistRegisteredClientIdResult() {
        object ClientNotRegistered : Failure()
        class Generic(val genericFailure: CoreFailure) : Failure()
    }
}
