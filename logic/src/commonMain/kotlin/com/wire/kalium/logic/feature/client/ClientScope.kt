/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

package com.wire.kalium.logic.feature.client

import com.wire.kalium.logic.configuration.notification.NotificationTokenRepository
import com.wire.kalium.logic.data.auth.verification.SecondFactorVerificationRepository
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.client.MLSClientProvider
import com.wire.kalium.logic.data.client.ProteusClientProvider
import com.wire.kalium.logic.data.client.remote.ClientRemoteRepository
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.keypackage.KeyPackageLimitsProvider
import com.wire.kalium.logic.data.keypackage.KeyPackageRepository
import com.wire.kalium.logic.data.logout.LogoutRepository
import com.wire.kalium.logic.data.notification.PushTokenRepository
import com.wire.kalium.logic.data.prekey.PreKeyRepository
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.CachedClientIdClearer
import com.wire.kalium.logic.feature.featureConfig.SyncFeatureConfigsUseCase
import com.wire.kalium.logic.feature.keypackage.MLSKeyPackageCountUseCase
import com.wire.kalium.logic.feature.keypackage.MLSKeyPackageCountUseCaseImpl
import com.wire.kalium.logic.feature.keypackage.RefillKeyPackagesUseCase
import com.wire.kalium.logic.feature.keypackage.RefillKeyPackagesUseCaseImpl
import com.wire.kalium.logic.feature.session.DeregisterTokenUseCase
import com.wire.kalium.logic.feature.session.DeregisterTokenUseCaseImpl
import com.wire.kalium.logic.feature.session.UpgradeCurrentSessionUseCase
import com.wire.kalium.logic.feature.user.UpdateSupportedProtocolsAndResolveOneOnOnesUseCase
import com.wire.kalium.logic.sync.slow.RestartSlowSyncProcessForRecoveryUseCase
import com.wire.kalium.logic.sync.slow.RestartSlowSyncProcessForRecoveryUseCaseImpl
import com.wire.kalium.util.DelicateKaliumApi

@Suppress("LongParameterList")
class ClientScope @OptIn(DelicateKaliumApi::class) internal constructor(
    val clientRepository: ClientRepository,
    private val pushTokenRepository: PushTokenRepository,
    private val logoutRepository: LogoutRepository,
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
    private val userRepository: UserRepository,
    private val secondFactorVerificationRepository: SecondFactorVerificationRepository,
    private val slowSyncRepository: SlowSyncRepository,
    private val cachedClientIdClearer: CachedClientIdClearer,
    private val updateSupportedProtocolsAndResolveOneOnOnes: UpdateSupportedProtocolsAndResolveOneOnOnesUseCase,
    private val registerMLSClientUseCase: RegisterMLSClientUseCase,
    private val syncFeatureConfigsUseCase: SyncFeatureConfigsUseCase
) {
    @OptIn(DelicateKaliumApi::class)
    val register: RegisterClientUseCase
        get() = RegisterClientUseCaseImpl(
            isAllowedToRegisterMLSClient,
            clientRepository,
            preKeyRepository,
            sessionRepository,
            selfUserId,
            userRepository,
            secondFactorVerificationRepository,
            registerMLSClientUseCase
        )

    val fetchSelfClients: FetchSelfClientsFromRemoteUseCase
        get() = FetchSelfClientsFromRemoteUseCaseImpl(clientRepository, clientIdProvider)
    val fetchUsersClients: FetchUsersClientsFromRemoteUseCase
        get() = FetchUsersClientsFromRemoteUseCaseImpl(clientRemoteRepository, clientRepository)
    val getOtherUserClients: ObserveClientsByUserIdUseCase get() = ObserveClientsByUserIdUseCase(clientRepository)
    val observeClientDetailsUseCase: ObserveClientDetailsUseCase get() = ObserveClientDetailsUseCaseImpl(clientRepository, clientIdProvider)
    val deleteClient: DeleteClientUseCase
        get() = DeleteClientUseCaseImpl(
            clientRepository,
            updateSupportedProtocolsAndResolveOneOnOnes,
        )
    val needsToRegisterClient: NeedsToRegisterClientUseCase
        get() = NeedsToRegisterClientUseCaseImpl(clientIdProvider, sessionRepository, proteusClientProvider, selfUserId)
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

    val observeCurrentClientId: ObserveCurrentClientIdUseCase
        get() = ObserveCurrentClientIdUseCaseImpl(clientRepository)

    val clearClientData: ClearClientDataUseCase
        get() = ClearClientDataUseCaseImpl(mlsClientProvider, proteusClientProvider)

    val getProteusFingerprint: GetProteusFingerprintUseCase
        get() = GetProteusFingerprintUseCaseImpl(preKeyRepository)

    @OptIn(DelicateKaliumApi::class)
    private val verifyExistingClientUseCase: VerifyExistingClientUseCase
        get() = VerifyExistingClientUseCaseImpl(
            selfUserId,
            clientRepository,
            isAllowedToRegisterMLSClient,
            registerMLSClientUseCase
        )
    val importClient: ImportClientUseCase
        get() = ImportClientUseCaseImpl(
            clientRepository,
            getOrRegister
        )

    val getOrRegister: GetOrRegisterClientUseCase
        get() = GetOrRegisterClientUseCaseImpl(
            clientRepository,
            pushTokenRepository,
            logoutRepository,
            register,
            clearClientData,
            verifyExistingClientUseCase,
            upgradeCurrentSessionUseCase,
            cachedClientIdClearer,
            syncFeatureConfigsUseCase
        )

    val remoteClientFingerPrint: ClientFingerprintUseCase get() = ClientFingerprintUseCaseImpl(proteusClientProvider, preKeyRepository)
    val updateClientVerificationStatus: UpdateClientVerificationStatusUseCase
        get() = UpdateClientVerificationStatusUseCase(clientRepository)

}
