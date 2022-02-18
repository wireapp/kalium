package com.wire.kalium.logic.feature.client

import com.wire.kalium.cryptography.ProteusClient
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.prekey.PreKeyMapper

class ClientScope(
    private val clientRepository: ClientRepository,
    private val proteusClient: ProteusClient,
    private val preKeyMapper: PreKeyMapper
) {
    val register: RegisterClientUseCase get() = RegisterClientUseCaseImpl(clientRepository, proteusClient, preKeyMapper)
    val selfClients: SelfClientsUseCase get() = SelfClientsUseCaseImpl(clientRepository)
}
