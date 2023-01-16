package com.wire.kalium.logic.feature.client

import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * This use case will observe and return the current client id of the current user.
 */
interface ObserveCurrentClientIdUseCase {
    suspend operator fun invoke(): Flow<ClientId?>
}

class ObserveCurrentClientIdUseCaseImpl internal constructor(
    private val clientRepository: ClientRepository,
    private val dispatcher: KaliumDispatcher = KaliumDispatcherImpl
) : ObserveCurrentClientIdUseCase {
    override suspend operator fun invoke(): Flow<ClientId?> = withContext(dispatcher.default) {
        clientRepository.observeCurrentClientId()
    }
}
