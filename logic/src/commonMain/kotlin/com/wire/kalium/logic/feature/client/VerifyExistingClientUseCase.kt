package com.wire.kalium.logic.feature.client

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.client.Client
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.withContext

/**
 * Checks if the given client is still exists on the backend, otherwise returns failure.
 */
interface VerifyExistingClientUseCase {

    /**
     * @param clientId client id of client
     * @return success if the given id is registered, failure otherwise
     */
    suspend operator fun invoke(clientId: ClientId): VerifyExistingClientResult
}

internal class VerifyExistingClientUseCaseImpl(
    private val clientRepository: ClientRepository,
    private val dispatcher: KaliumDispatcher = KaliumDispatcherImpl
) : VerifyExistingClientUseCase {

    override suspend fun invoke(clientId: ClientId): VerifyExistingClientResult = withContext(dispatcher.default) {
        clientRepository.selfListOfClients()
            .fold({
                VerifyExistingClientResult.Failure.Generic(it)
            }, { listOfClients ->
                val client = listOfClients.firstOrNull { it.id == clientId }
                if (client != null) {
                    VerifyExistingClientResult.Success(client)
                } else {
                    VerifyExistingClientResult.Failure.ClientNotRegistered
                }
            })
    }
}

sealed class VerifyExistingClientResult {
    class Success(val client: Client) : VerifyExistingClientResult()

    sealed class Failure : VerifyExistingClientResult() {
        object ClientNotRegistered : Failure()
        class Generic(val genericFailure: CoreFailure) : Failure()
    }
}
