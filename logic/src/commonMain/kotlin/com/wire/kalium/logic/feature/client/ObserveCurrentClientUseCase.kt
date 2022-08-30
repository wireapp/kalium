package com.wire.kalium.logic.feature.client

import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.conversation.ClientId
import kotlinx.coroutines.flow.Flow

class ObserveCurrentClientUseCase internal constructor(
    private val clientRepository: ClientRepository
) {
    suspend operator fun invoke(): Flow<ClientId?> = clientRepository.observeCurrentClientId()
}
