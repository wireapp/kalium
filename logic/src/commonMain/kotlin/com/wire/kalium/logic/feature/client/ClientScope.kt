package com.wire.kalium.logic.feature.client

import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.client.MLSClientProvider
import com.wire.kalium.logic.data.client.remote.ClientRemoteRepository
import com.wire.kalium.logic.data.keypackage.KeyPackageRepository
import com.wire.kalium.logic.data.prekey.PreKeyRepository
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.register.RegisterParam
import com.wire.kalium.logic.feature.session.RegisterTokenUseCase
import com.wire.kalium.persistence.client.ClientRegistrationStorage

class ClientScope(
    private val clientRepository: ClientRepository,
    private val preKeyRepository: PreKeyRepository,
    private val keyPackageRepository: KeyPackageRepository,
    private val mlsClientProvider: MLSClientProvider,
    private val clientRemoteRepository: ClientRemoteRepository,
    private val clientRegistrationStorage: ClientRegistrationStorage
) {
    val register: RegisterClientUseCase
        get() = RegisterClientUseCaseImpl(
            clientRepository,
            preKeyRepository,
            keyPackageRepository,
            mlsClientProvider,
            clientRemoteRepository,
            clientRegistrationStorage
        )
    val selfClients: SelfClientsUseCase get() = SelfClientsUseCaseImpl(clientRepository)
    val deleteClient: DeleteClientUseCase get() = DeleteClientUseCaseImpl(clientRepository)
    val needsToRegisterClient: NeedsToRegisterClientUseCase get() = NeedsToRegisterClientUseCaseImpl(clientRepository)
    val pushTokenUseCase: RegisterTokenUseCase get() = RegisterTokenUseCase(clientRemoteRepository)
}
