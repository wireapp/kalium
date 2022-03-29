package com.wire.kalium.logic.feature.client

import com.wire.kalium.cryptography.ProteusClient
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.prekey.PreKeyMapper
import com.wire.kalium.logic.data.prekey.PreKeyRepository

class ClientScope(
    private val clientRepository: ClientRepository,
    private val preKeyRepository: PreKeyRepository
) {
    val register: RegisterClientUseCase get() = RegisterClientUseCaseImpl(clientRepository, preKeyRepository)
    val selfClients: SelfClientsUseCase get() = SelfClientsUseCaseImpl(clientRepository)
    val deleteClient: DeleteClientUseCase get() = DeleteClientUseCaseImpl(clientRepository)
    val currentClientId: CurrentClientIdUseCase get() = CurrentClientIdUseCaseImpl(clientRepository)
}
