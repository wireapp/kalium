package com.wire.kalium.logic.feature.client

import com.wire.kalium.logic.configuration.notification.NotificationTokenRepository
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.client.MLSClientProvider
import com.wire.kalium.logic.data.keypackage.KeyPackageRepository
import com.wire.kalium.logic.data.prekey.PreKeyRepository
import com.wire.kalium.logic.feature.session.DeregisterTokenUseCase
import com.wire.kalium.logic.feature.session.DeregisterTokenUseCaseImpl
import com.wire.kalium.logic.feature.session.RegisterTokenUseCase

class ClientScope(
    private val clientRepository: ClientRepository,
    private val preKeyRepository: PreKeyRepository,
    private val keyPackageRepository: KeyPackageRepository,
    private val mlsClientProvider: MLSClientProvider,
    private val notificationTokenRepository: NotificationTokenRepository

) {
    val register: RegisterClientUseCase
        get() = RegisterClientUseCaseImpl(
            clientRepository,
            preKeyRepository,
            keyPackageRepository,
            mlsClientProvider,
            notificationTokenRepository
        )
    val selfClients: SelfClientsUseCase get() = SelfClientsUseCaseImpl(clientRepository)
    val deleteClient: DeleteClientUseCase get() = DeleteClientUseCaseImpl(clientRepository)
    val needsToRegisterClient: NeedsToRegisterClientUseCase get() = NeedsToRegisterClientUseCaseImpl(clientRepository)
    val registerNativePushToken: RegisterTokenUseCase get() = RegisterTokenUseCase(clientRepository)
    val deregisterNativePushToken: DeregisterTokenUseCase get() = DeregisterTokenUseCaseImpl(clientRepository, notificationTokenRepository)
}
