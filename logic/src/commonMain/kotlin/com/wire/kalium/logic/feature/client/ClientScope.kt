package com.wire.kalium.logic.feature.client

import com.wire.kalium.cryptography.ProteusClient
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.prekey.PreKeyMapper

class ClientScope(
    private val clientRepository: ClientRepository,
    private val proteusClient: ProteusClient
) {
    val register: RegisterClientUseCase get() = RegisterClientUseCaseImpl(clientRepository, proteusClient)
    val selfClients: SelfClientsUseCase get() = SelfClientsUseCaseImpl(clientRepository)
    val deleteClient: DeleteClientUseCase get() = DeleteClientUseCaseImpl(clientRepository)
}
