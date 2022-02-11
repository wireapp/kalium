package com.wire.kalium.logic.feature.client

import com.wire.kalium.cryptography.ProteusClient
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.prekey.PreKeyMapper

class ClientScope(
    clientRepository: ClientRepository,
    proteusClient: ProteusClient,
) {
    // TODO : get() ?
    val register: RegisterClientUseCase = RegisterClientUseCaseImpl(clientRepository, proteusClient)
}
