package com.wire.kalium.logic.feature.client

import com.wire.kalium.logic.configuration.notification.NotificationTokenRepository
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.client.MLSClientProvider
import com.wire.kalium.logic.data.client.remote.ClientRemoteRepository
import com.wire.kalium.logic.data.keypackage.KeyPackageLimitsProvider
import com.wire.kalium.logic.data.keypackage.KeyPackageRepository
import com.wire.kalium.logic.data.prekey.PreKeyRepository
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.CurrentClientIdProvider
import com.wire.kalium.logic.feature.ProteusClientProvider
import com.wire.kalium.logic.feature.keypackage.MLSKeyPackageCountUseCase
import com.wire.kalium.logic.feature.keypackage.MLSKeyPackageCountUseCaseImpl
import com.wire.kalium.logic.feature.keypackage.RefillKeyPackagesUseCase
import com.wire.kalium.logic.feature.keypackage.RefillKeyPackagesUseCaseImpl
import com.wire.kalium.logic.feature.session.DeregisterTokenUseCase
import com.wire.kalium.logic.feature.session.DeregisterTokenUseCaseImpl
import com.wire.kalium.logic.feature.session.UpgradeCurrentSessionUseCase
import com.wire.kalium.logic.sync.incremental.RestartSlowSyncProcessForRecoveryUseCase
import com.wire.kalium.logic.sync.incremental.RestartSlowSyncProcessForRecoveryUseCaseImpl
import com.wire.kalium.util.DelicateKaliumApi

@Suppress("LongParameterList")
class ClientScope @OptIn(DelicateKaliumApi::class) internal constructor(
    private val clientRepository: ClientRepository,
    private val preKeyRepository: PreKeyRepository,
    private val keyPackageRepository: KeyPackageRepository,
    private val keyPackageLimitsProvider: KeyPackageLimitsProvider,
    private val mlsClientProvider: MLSClientProvider,
    private val notificationTokenRepository: NotificationTokenRepository,
    private val clientRemoteRepository: ClientRemoteRepository,
    private val proteusClientProvider: ProteusClientProvider,
    private val sessionRepository: SessionRepository,
    private val upgradeCurrentSessionUseCase: UpgradeCurrentSessionUseCase,
    private val selfUserId: UserId,
    private val isAllowedToRegisterMLSClient: IsAllowedToRegisterMLSClientUseCase,
    private val clientIdProvider: CurrentClientIdProvider,
    private val slowSyncRepository: SlowSyncRepository
) {
    @OptIn(DelicateKaliumApi::class)
    val register: RegisterClientUseCase
        get() = RegisterClientUseCaseImpl(
            isAllowedToRegisterMLSClient,
            clientRepository,
            preKeyRepository,
            keyPackageRepository,
            keyPackageLimitsProvider,
            mlsClientProvider,
            sessionRepository,
            selfUserId
        )

    val selfClients: SelfClientsUseCase get() = SelfClientsUseCaseImpl(clientRepository, clientIdProvider)
    val deleteClient: DeleteClientUseCase get() = DeleteClientUseCaseImpl(clientRepository)
    val needsToRegisterClient: NeedsToRegisterClientUseCase
        get() = NeedsToRegisterClientUseCaseImpl(clientIdProvider, sessionRepository, selfUserId)
    val deregisterNativePushToken: DeregisterTokenUseCase
        get() = DeregisterTokenUseCaseImpl(clientRepository, notificationTokenRepository)
    val mlsKeyPackageCountUseCase: MLSKeyPackageCountUseCase
        get() = MLSKeyPackageCountUseCaseImpl(keyPackageRepository, clientIdProvider, keyPackageLimitsProvider)
    val restartSlowSyncProcessForRecoveryUseCase: RestartSlowSyncProcessForRecoveryUseCase
        get() = RestartSlowSyncProcessForRecoveryUseCaseImpl(slowSyncRepository)
    val refillKeyPackages: RefillKeyPackagesUseCase
        get() = RefillKeyPackagesUseCaseImpl(
            keyPackageRepository,
            keyPackageLimitsProvider,
            clientIdProvider
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
        get() = ClearClientDataUseCaseImpl(mlsClientProvider, proteusClientProvider)

    private val verifyExistingClientUseCase: VerifyExistingClientUseCase
        get() = VerifyExistingClientUseCaseImpl(clientRepository)

    val importClient: ImportClientUseCase
        get() = ImportClientUseCaseImpl(
            clientRepository,
            getOrRegister
        )

    val getOrRegister: GetOrRegisterClientUseCase
        get() = GetOrRegisterClientUseCaseImpl(
            clientRepository,
            register,
            clearClientData,
            verifyExistingClientUseCase,
            upgradeCurrentSessionUseCase
        )
}
