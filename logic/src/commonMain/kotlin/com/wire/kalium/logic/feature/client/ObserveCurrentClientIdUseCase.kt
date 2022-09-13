package com.wire.kalium.logic.feature.client

import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.conversation.ClientId
import kotlinx.coroutines.flow.Flow

interface ObserveCurrentClientIdUseCase {
    suspend operator fun invoke(): Flow<ClientId?>
}

class ObserveCurrentClientIdUseCaseImpl internal constructor(
    private val clientRepository: ClientRepository
) : ObserveCurrentClientIdUseCase {
    override suspend operator fun invoke(): Flow<ClientId?> = clientRepository.observeCurrentClientId()
}
