package com.wire.kalium.logic.feature.client

import com.wire.kalium.cryptography.ProteusClient
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.prekey.PreKeyMapper

class ClientScope(
    clientRepository: ClientRepository,
    proteusClient: ProteusClient,
    preKeyMapper: PreKeyMapper
) {
    // TODO : get() ?
    val register: RegisterClientUseCase = RegisterClientUseCase(clientRepository, proteusClient, preKeyMapper)
}
