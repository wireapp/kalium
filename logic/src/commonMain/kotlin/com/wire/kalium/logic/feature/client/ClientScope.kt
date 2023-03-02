/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.client.MLSClientProvider
import com.wire.kalium.logic.data.client.remote.ClientRemoteRepository
import com.wire.kalium.logic.data.keypackage.KeyPackageLimitsProvider
import com.wire.kalium.logic.data.keypackage.KeyPackageRepository
import com.wire.kalium.logic.data.prekey.PreKeyRepository
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.UserStorage
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
    private val userStorage: UserStorage,
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

    val selfClients: FetchSelfClientsFromRemoteUseCase get() = FetchSelfClientsFromRemoteUseCaseImpl(clientRepository, clientIdProvider)
    val observeClientDetailsUseCase: ObserveClientDetailsUseCase get() = ObserveClientDetailsUseCaseImpl(clientRepository, clientIdProvider)
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

    val getProteusFingerprint: GetProteusFingerprintUseCase
        get() = GetProteusFingerprintUseCaseImpl(preKeyRepository)

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

    val remoteClientFingerPrint: ClientFingerprintUseCase get() = ClientFingerprintUseCase(proteusClientProvider, preKeyRepository)
}
