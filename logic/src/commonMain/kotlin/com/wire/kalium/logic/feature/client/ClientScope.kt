package com.wire.kalium.logic.feature.client

import com.wire.kalium.cryptography.ProteusClient
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.configuration.notification.NotificationTokenRepository
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.client.MLSClientProvider
import com.wire.kalium.logic.data.client.remote.ClientRemoteRepository
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.keypackage.KeyPackageLimitsProvider
import com.wire.kalium.logic.data.keypackage.KeyPackageRepository
import com.wire.kalium.logic.data.prekey.PreKeyRepository
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.CurrentClientIdProvider
import com.wire.kalium.logic.feature.keypackage.MLSKeyPackageCountUseCase
import com.wire.kalium.logic.feature.keypackage.MLSKeyPackageCountUseCaseImpl
import com.wire.kalium.logic.feature.keypackage.RefillKeyPackagesUseCase
import com.wire.kalium.logic.feature.keypackage.RefillKeyPackagesUseCaseImpl
import com.wire.kalium.logic.feature.session.DeregisterTokenUseCase
import com.wire.kalium.logic.feature.session.DeregisterTokenUseCaseImpl
import com.wire.kalium.logic.featureFlags.FeatureSupport
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.onSuccess

@Suppress("LongParameterList")
class ClientScope(
    private val clientRepository: ClientRepository,
    private val preKeyRepository: PreKeyRepository,
    private val keyPackageRepository: KeyPackageRepository,
    private val keyPackageLimitsProvider: KeyPackageLimitsProvider,
    private val mlsClientProvider: MLSClientProvider,
    private val notificationTokenRepository: NotificationTokenRepository,
    private val clientRemoteRepository: ClientRemoteRepository,
    private val proteusClient: ProteusClient,
    private val sessionRepository: SessionRepository,
    private val selfUserId: UserId,
    private val featureSupport: FeatureSupport,
) {
    val register: RegisterClientUseCase
        get() = RegisterClientUseCaseImpl(
            featureSupport,
            clientRepository,
            preKeyRepository,
            keyPackageRepository,
            keyPackageLimitsProvider,
            mlsClientProvider
        )

    // TODO: extract client id provider to it's own class and test it
    private var _clientId: ClientId? = null
    private suspend fun clientId(): Either<CoreFailure, ClientId> =
        if (_clientId != null) Either.Right(_clientId!!) else {
            clientRepository.currentClientId().onSuccess {
                _clientId = it
            }
        }

    private val clientIdProvider = CurrentClientIdProvider { clientId() }

    val selfClients: SelfClientsUseCase get() = SelfClientsUseCaseImpl(clientRepository, clientIdProvider)
    val deleteClient: DeleteClientUseCase get() = DeleteClientUseCaseImpl(clientRepository)
    val needsToRegisterClient: NeedsToRegisterClientUseCase
        get() = NeedsToRegisterClientUseCaseImpl(clientRepository, sessionRepository, selfUserId)
    val deregisterNativePushToken: DeregisterTokenUseCase
        get() = DeregisterTokenUseCaseImpl(clientRepository, notificationTokenRepository)
    val mlsKeyPackageCountUseCase: MLSKeyPackageCountUseCase
        get() = MLSKeyPackageCountUseCaseImpl(keyPackageRepository, clientRepository, keyPackageLimitsProvider)
    val refillKeyPackages: RefillKeyPackagesUseCase
        get() = RefillKeyPackagesUseCaseImpl(
            keyPackageRepository,
            keyPackageLimitsProvider,
            clientRepository
        )
    val persistOtherUserClients: PersistOtherUserClientsUseCase
        get() = PersistOtherUserClientsUseCaseImpl(
            clientRemoteRepository,
            clientRepository
        )
    val getOtherUserClients: GetOtherUserClientsUseCase
        get() = GetOtherUserClientsUseCaseImpl(
            clientRepository
        )

    val observeCurrentClientId: ObserveCurrentClientIdUseCase
        get() = ObserveCurrentClientIdUseCaseImpl(clientRepository)

    val clearClientData: ClearClientDataUseCase
        get() = ClearClientDataUseCaseImpl(mlsClientProvider, proteusClient)

    val getOrRegister: GetOrRegisterClientUseCase
        get() = GetOrRegisterClientUseCaseImpl(clientRepository, register, clearClientData, proteusClient)
}
