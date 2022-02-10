package com.wire.kalium.logic.feature.client

import com.wire.kalium.logic.data.client.ClientRepository

class ClientScope(
    clientRepository: ClientRepository
) {
    val register: RegisterClientUseCase = RegisterClientUseCaseImpl(clientRepository)
}
