package com.wire.kalium.logic.feature.client

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.client.Client
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.feature.CurrentClientIdProvider
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.getOrNull
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.withContext

/**
 * This use case will return the list of clients of the current user.
 */
interface SelfClientsUseCase {
    /**
     * @return the [SelfClientsResult] with the list of clients of the current user, otherwise a [CoreFailure]
     */
    suspend operator fun invoke(): SelfClientsResult
}

class SelfClientsUseCaseImpl(
    private val clientRepository: ClientRepository,
    private val provideClientId: CurrentClientIdProvider,
    private val dispatcher: KaliumDispatcher = KaliumDispatcherImpl
) : SelfClientsUseCase {
    override suspend fun invoke(): SelfClientsResult = withContext(dispatcher.default) {
        clientRepository.selfListOfClients().fold(
            { SelfClientsResult.Failure.Generic(it) },
            { clients ->
                val currentClientId = provideClientId()
                SelfClientsResult.Success(
                    clients = clients.sortedByDescending { it.registrationTime },
                    currentClientId = currentClientId.getOrNull()
                )
            }
        )
    }
}

sealed class SelfClientsResult {
    data class Success(val clients: List<Client>, val currentClientId: ClientId?) : SelfClientsResult()

    sealed class Failure : SelfClientsResult() {
        class Generic(val genericFailure: CoreFailure) : Failure()
    }
}
