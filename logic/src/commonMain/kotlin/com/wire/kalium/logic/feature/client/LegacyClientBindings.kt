/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

@file:OptIn(DelicateKaliumApi::class)

package com.wire.kalium.logic.feature.client

import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.configuration.notification.NotificationTokenRepository
import com.wire.kalium.logic.data.auth.verification.SecondFactorVerificationRepository
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.client.CryptoTransactionProvider
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
import com.wire.kalium.logic.di.UserLifetime
import com.wire.kalium.logic.feature.CachedClientIdClearer
import com.wire.kalium.logic.feature.UserSessionScopeProvider
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
import com.wire.kalium.messaging.hooks.CryptoStateChangeHookNotifier
import com.wire.kalium.util.DelicateKaliumApi
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.Qualifier
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope

@Qualifier
internal annotation class LegacyClientBinding(val value: String)

/** Deferred inputs from the user owner. Each capture is read once per user graph, only when needed. */
internal interface LegacyClientDependencies {
    val clientRepository: ClientRepository
    val pushTokenRepository: PushTokenRepository
    val logoutRepository: LogoutRepository
    val preKeyRepository: PreKeyRepository
    val keyPackageRepository: KeyPackageRepository
    val keyPackageLimitsProvider: KeyPackageLimitsProvider
    val mlsClientProvider: MLSClientProvider
    val notificationTokenRepository: NotificationTokenRepository
    val clientRemoteRepository: ClientRemoteRepository
    val proteusClientProvider: ProteusClientProvider
    val sessionRepository: SessionRepository
    val upgradeCurrentSessionUseCase: UpgradeCurrentSessionUseCase
    val selfUserId: UserId
    val isAllowedToRegisterMLSClient: IsAllowedToRegisterMLSClientUseCase
    val clientIdProvider: CurrentClientIdProvider
    val userRepository: UserRepository
    val secondFactorVerificationRepository: SecondFactorVerificationRepository
    val slowSyncRepository: SlowSyncRepository
    val cachedClientIdClearer: CachedClientIdClearer
    val updateSupportedProtocolsAndResolveOneOnOnes: UpdateSupportedProtocolsAndResolveOneOnOnesUseCase
    val registerMLSClientUseCase: RegisterMLSClientUseCase
    val syncFeatureConfigsUseCase: SyncFeatureConfigsUseCase
    val userConfigRepository: UserConfigRepository
    val transactionProvider: CryptoTransactionProvider
    val cryptoStateChangeHookNotifier: CryptoStateChangeHookNotifier
    val originatingScope: CoroutineScope
    val userSessionScopeProvider: UserSessionScopeProvider
}

/** Individual accessors; qualified captures preserve the cached facade's original identities. */
internal interface LegacyClientApi {
    @get:LegacyClientBinding("clientRepository")
    val clientClientRepository: ClientRepository

    @get:LegacyClientBinding("pushTokenRepository")
    val clientPushTokenRepository: PushTokenRepository

    @get:LegacyClientBinding("logoutRepository")
    val clientLogoutRepository: LogoutRepository

    @get:LegacyClientBinding("preKeyRepository")
    val clientPreKeyRepository: PreKeyRepository

    @get:LegacyClientBinding("keyPackageRepository")
    val clientKeyPackageRepository: KeyPackageRepository

    @get:LegacyClientBinding("keyPackageLimitsProvider")
    val clientKeyPackageLimitsProvider: KeyPackageLimitsProvider

    @get:LegacyClientBinding("mlsClientProvider")
    val clientMlsClientProvider: MLSClientProvider

    @get:LegacyClientBinding("notificationTokenRepository")
    val clientNotificationTokenRepository: NotificationTokenRepository

    @get:LegacyClientBinding("clientRemoteRepository")
    val clientClientRemoteRepository: ClientRemoteRepository

    @get:LegacyClientBinding("proteusClientProvider")
    val clientProteusClientProvider: ProteusClientProvider

    @get:LegacyClientBinding("sessionRepository")
    val clientSessionRepository: SessionRepository

    @get:LegacyClientBinding("upgradeCurrentSessionUseCase")
    val clientUpgradeCurrentSessionUseCase: UpgradeCurrentSessionUseCase

    @get:LegacyClientBinding("selfUserId")
    val clientSelfUserId: UserId

    @get:LegacyClientBinding("isAllowedToRegisterMLSClient")
    val clientIsAllowedToRegisterMLSClient: IsAllowedToRegisterMLSClientUseCase

    @get:LegacyClientBinding("clientIdProvider")
    val clientClientIdProvider: CurrentClientIdProvider

    @get:LegacyClientBinding("userRepository")
    val clientUserRepository: UserRepository

    @get:LegacyClientBinding("secondFactorVerificationRepository")
    val clientSecondFactorVerificationRepository: SecondFactorVerificationRepository

    @get:LegacyClientBinding("slowSyncRepository")
    val clientSlowSyncRepository: SlowSyncRepository

    @get:LegacyClientBinding("cachedClientIdClearer")
    val clientCachedClientIdClearer: CachedClientIdClearer

    @get:LegacyClientBinding("updateSupportedProtocolsAndResolveOneOnOnes")
    val clientUpdateSupportedProtocolsAndResolveOneOnOnes: UpdateSupportedProtocolsAndResolveOneOnOnesUseCase

    @get:LegacyClientBinding("registerMLSClientUseCase")
    val clientRegisterMLSClientUseCase: RegisterMLSClientUseCase

    @get:LegacyClientBinding("syncFeatureConfigsUseCase")
    val clientSyncFeatureConfigsUseCase: SyncFeatureConfigsUseCase

    @get:LegacyClientBinding("userConfigRepository")
    val clientUserConfigRepository: UserConfigRepository

    @get:LegacyClientBinding("transactionProvider")
    val clientTransactionProvider: CryptoTransactionProvider

    @get:LegacyClientBinding("cryptoStateChangeHookNotifier")
    val clientCryptoStateChangeHookNotifier: CryptoStateChangeHookNotifier

    @get:LegacyClientBinding("originatingScope")
    val clientOriginatingScope: CoroutineScope

    @get:LegacyClientBinding("userSessionScopeProvider")
    val clientUserSessionScopeProvider: UserSessionScopeProvider

    @get:LegacyClientBinding("register")
    val clientRegister: RegisterClientUseCase

    @get:LegacyClientBinding("fetchSelfClients")
    val clientFetchSelfClients: FetchSelfClientsFromRemoteUseCase

    @get:LegacyClientBinding("fetchUsersClients")
    val clientFetchUsersClients: FetchUsersClientsFromRemoteUseCase

    @get:LegacyClientBinding("getOtherUserClients")
    val clientGetOtherUserClients: ObserveClientsByUserIdUseCase

    @get:LegacyClientBinding("observeClientDetailsUseCase")
    val clientObserveClientDetailsUseCase: ObserveClientDetailsUseCase

    @get:LegacyClientBinding("deleteClient")
    val clientDeleteClient: DeleteClientUseCase

    @get:LegacyClientBinding("needsToRegisterClient")
    val clientNeedsToRegisterClient: NeedsToRegisterClientUseCase

    @get:LegacyClientBinding("deregisterNativePushToken")
    val clientDeregisterNativePushToken: DeregisterTokenUseCase

    @get:LegacyClientBinding("mlsKeyPackageCountUseCase")
    val clientMlsKeyPackageCountUseCase: MLSKeyPackageCountUseCase

    @get:LegacyClientBinding("restartSlowSyncProcessForRecoveryUseCase")
    val clientRestartSlowSyncProcessForRecoveryUseCase: RestartSlowSyncProcessForRecoveryUseCase

    @get:LegacyClientBinding("refillKeyPackages")
    val clientRefillKeyPackages: RefillKeyPackagesUseCase

    @get:LegacyClientBinding("observeCurrentClientId")
    val clientObserveCurrentClientId: ObserveCurrentClientIdUseCase

    @get:LegacyClientBinding("clearClientData")
    val clientClearClientData: ClearClientDataUseCase

    @get:LegacyClientBinding("getProteusFingerprint")
    val clientGetProteusFingerprint: GetProteusFingerprintUseCase

    @get:LegacyClientBinding("verifyExistingClientUseCase")
    val clientVerifyExistingClientUseCase: VerifyExistingClientUseCase

    @get:LegacyClientBinding("importClient")
    val clientImportClient: ImportClientUseCase

    @get:LegacyClientBinding("getOrRegister")
    val clientGetOrRegister: GetOrRegisterClientUseCase

    @get:LegacyClientBinding("remoteClientFingerPrint")
    val clientRemoteClientFingerPrint: ClientFingerprintUseCase

    @get:LegacyClientBinding("updateClientVerificationStatus")
    val clientUpdateClientVerificationStatus: UpdateClientVerificationStatusUseCase
}

/** Individual bindings in the user graph; this package does not introduce a feature graph. */
@Suppress("TooManyFunctions", "LongParameterList", "LargeClass") // Declarative bindings preserve legacy captures and factories.
internal interface LegacyClientBindings : LegacyClientApi {
    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyClientBinding("clientRepository")
    fun provideClientClientRepository(dependencies: LegacyClientDependencies): ClientRepository =
        dependencies.clientRepository

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyClientBinding("pushTokenRepository")
    fun provideClientPushTokenRepository(dependencies: LegacyClientDependencies): PushTokenRepository =
        dependencies.pushTokenRepository

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyClientBinding("logoutRepository")
    fun provideClientLogoutRepository(dependencies: LegacyClientDependencies): LogoutRepository =
        dependencies.logoutRepository

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyClientBinding("preKeyRepository")
    fun provideClientPreKeyRepository(dependencies: LegacyClientDependencies): PreKeyRepository =
        dependencies.preKeyRepository

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyClientBinding("keyPackageRepository")
    fun provideClientKeyPackageRepository(dependencies: LegacyClientDependencies): KeyPackageRepository =
        dependencies.keyPackageRepository

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyClientBinding("keyPackageLimitsProvider")
    fun provideClientKeyPackageLimitsProvider(dependencies: LegacyClientDependencies): KeyPackageLimitsProvider =
        dependencies.keyPackageLimitsProvider

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyClientBinding("mlsClientProvider")
    fun provideClientMlsClientProvider(dependencies: LegacyClientDependencies): MLSClientProvider =
        dependencies.mlsClientProvider

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyClientBinding("notificationTokenRepository")
    fun provideClientNotificationTokenRepository(dependencies: LegacyClientDependencies): NotificationTokenRepository =
        dependencies.notificationTokenRepository

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyClientBinding("clientRemoteRepository")
    fun provideClientClientRemoteRepository(dependencies: LegacyClientDependencies): ClientRemoteRepository =
        dependencies.clientRemoteRepository

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyClientBinding("proteusClientProvider")
    fun provideClientProteusClientProvider(dependencies: LegacyClientDependencies): ProteusClientProvider =
        dependencies.proteusClientProvider

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyClientBinding("sessionRepository")
    fun provideClientSessionRepository(dependencies: LegacyClientDependencies): SessionRepository =
        dependencies.sessionRepository

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyClientBinding("upgradeCurrentSessionUseCase")
    fun provideClientUpgradeCurrentSessionUseCase(dependencies: LegacyClientDependencies): UpgradeCurrentSessionUseCase =
        dependencies.upgradeCurrentSessionUseCase

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyClientBinding("selfUserId")
    fun provideClientSelfUserId(dependencies: LegacyClientDependencies): UserId =
        dependencies.selfUserId

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyClientBinding("isAllowedToRegisterMLSClient")
    fun provideClientIsAllowedToRegisterMLSClient(dependencies: LegacyClientDependencies): IsAllowedToRegisterMLSClientUseCase =
        dependencies.isAllowedToRegisterMLSClient

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyClientBinding("clientIdProvider")
    fun provideClientClientIdProvider(dependencies: LegacyClientDependencies): CurrentClientIdProvider =
        dependencies.clientIdProvider

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyClientBinding("userRepository")
    fun provideClientUserRepository(dependencies: LegacyClientDependencies): UserRepository =
        dependencies.userRepository

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyClientBinding("secondFactorVerificationRepository")
    fun provideClientSecondFactorVerificationRepository(dependencies: LegacyClientDependencies): SecondFactorVerificationRepository =
        dependencies.secondFactorVerificationRepository

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyClientBinding("slowSyncRepository")
    fun provideClientSlowSyncRepository(dependencies: LegacyClientDependencies): SlowSyncRepository =
        dependencies.slowSyncRepository

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyClientBinding("cachedClientIdClearer")
    fun provideClientCachedClientIdClearer(dependencies: LegacyClientDependencies): CachedClientIdClearer =
        dependencies.cachedClientIdClearer

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyClientBinding("updateSupportedProtocolsAndResolveOneOnOnes")
    fun provideClientUpdateSupportedProtocolsAndResolveOneOnOnes(
        dependencies: LegacyClientDependencies,
    ): UpdateSupportedProtocolsAndResolveOneOnOnesUseCase =
        dependencies.updateSupportedProtocolsAndResolveOneOnOnes

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyClientBinding("registerMLSClientUseCase")
    fun provideClientRegisterMLSClientUseCase(dependencies: LegacyClientDependencies): RegisterMLSClientUseCase =
        dependencies.registerMLSClientUseCase

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyClientBinding("syncFeatureConfigsUseCase")
    fun provideClientSyncFeatureConfigsUseCase(dependencies: LegacyClientDependencies): SyncFeatureConfigsUseCase =
        dependencies.syncFeatureConfigsUseCase

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyClientBinding("userConfigRepository")
    fun provideClientUserConfigRepository(dependencies: LegacyClientDependencies): UserConfigRepository =
        dependencies.userConfigRepository

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyClientBinding("transactionProvider")
    fun provideClientTransactionProvider(dependencies: LegacyClientDependencies): CryptoTransactionProvider =
        dependencies.transactionProvider

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyClientBinding("cryptoStateChangeHookNotifier")
    fun provideClientCryptoStateChangeHookNotifier(dependencies: LegacyClientDependencies): CryptoStateChangeHookNotifier =
        dependencies.cryptoStateChangeHookNotifier

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyClientBinding("originatingScope")
    fun provideClientOriginatingScope(dependencies: LegacyClientDependencies): CoroutineScope =
        dependencies.originatingScope

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyClientBinding("userSessionScopeProvider")
    fun provideClientUserSessionScopeProvider(dependencies: LegacyClientDependencies): UserSessionScopeProvider =
        dependencies.userSessionScopeProvider

    @Provides
    @LegacyClientBinding("register")
    fun provideClientRegister(
        @LegacyClientBinding("clientRepository") clientRepository: ClientRepository,
        @LegacyClientBinding("preKeyRepository") preKeyRepository: PreKeyRepository,
        @LegacyClientBinding("sessionRepository") sessionRepository: SessionRepository,
        @LegacyClientBinding("selfUserId") selfUserId: UserId,
        @LegacyClientBinding("isAllowedToRegisterMLSClient") isAllowedToRegisterMLSClient: IsAllowedToRegisterMLSClientUseCase,
        @LegacyClientBinding("userRepository") userRepository: UserRepository,
        @LegacyClientBinding("secondFactorVerificationRepository") secondFactorVerificationRepository: SecondFactorVerificationRepository,
        @LegacyClientBinding("registerMLSClientUseCase") registerMLSClientUseCase: RegisterMLSClientUseCase,
    ): RegisterClientUseCase =
        RegisterClientUseCaseImpl(
            isAllowedToRegisterMLSClient,
            clientRepository,
            preKeyRepository,
            sessionRepository,
            selfUserId,
            userRepository,
            secondFactorVerificationRepository,
            registerMLSClientUseCase
        )

    @Provides
    @LegacyClientBinding("fetchSelfClients")
    fun provideClientFetchSelfClients(
        @LegacyClientBinding("clientRepository") clientRepository: ClientRepository,
        @LegacyClientBinding("clientIdProvider") clientIdProvider: CurrentClientIdProvider,
    ): FetchSelfClientsFromRemoteUseCase = FetchSelfClientsFromRemoteUseCaseImpl(clientRepository, clientIdProvider)

    @Provides
    @LegacyClientBinding("fetchUsersClients")
    fun provideClientFetchUsersClients(
        @LegacyClientBinding("clientRepository") clientRepository: ClientRepository,
        @LegacyClientBinding("clientRemoteRepository") clientRemoteRepository: ClientRemoteRepository,
    ): FetchUsersClientsFromRemoteUseCase = FetchUsersClientsFromRemoteUseCaseImpl(clientRemoteRepository, clientRepository)

    @Provides
    @LegacyClientBinding("getOtherUserClients")
    fun provideClientGetOtherUserClients(
        @LegacyClientBinding("clientRepository") clientRepository: ClientRepository,
    ): ObserveClientsByUserIdUseCase = ObserveClientsByUserIdUseCase(clientRepository)

    @Provides
    @LegacyClientBinding("observeClientDetailsUseCase")
    fun provideClientObserveClientDetailsUseCase(
        @LegacyClientBinding("clientRepository") clientRepository: ClientRepository,
        @LegacyClientBinding("clientIdProvider") clientIdProvider: CurrentClientIdProvider,
    ): ObserveClientDetailsUseCase = ObserveClientDetailsUseCaseImpl(clientRepository, clientIdProvider)

    @Provides
    @LegacyClientBinding("deleteClient")
    fun provideClientDeleteClient(
        @LegacyClientBinding("clientRepository") clientRepository: ClientRepository,
        @LegacyClientBinding("updateSupportedProtocolsAndResolveOneOnOnes")
        updateSupportedProtocolsAndResolveOneOnOnes: UpdateSupportedProtocolsAndResolveOneOnOnesUseCase,
        @LegacyClientBinding("transactionProvider") transactionProvider: CryptoTransactionProvider,
    ): DeleteClientUseCase =
        DeleteClientUseCaseImpl(
            clientRepository,
            updateSupportedProtocolsAndResolveOneOnOnes,
            transactionProvider
        )

    @Provides
    @LegacyClientBinding("needsToRegisterClient")
    fun provideClientNeedsToRegisterClient(
        @LegacyClientBinding("sessionRepository") sessionRepository: SessionRepository,
        @LegacyClientBinding("selfUserId") selfUserId: UserId,
        @LegacyClientBinding("clientIdProvider") clientIdProvider: CurrentClientIdProvider,
        @LegacyClientBinding("transactionProvider") transactionProvider: CryptoTransactionProvider,
    ): NeedsToRegisterClientUseCase = NeedsToRegisterClientUseCaseImpl(clientIdProvider, sessionRepository, transactionProvider, selfUserId)

    @Provides
    @LegacyClientBinding("deregisterNativePushToken")
    fun provideClientDeregisterNativePushToken(
        @LegacyClientBinding("clientRepository") clientRepository: ClientRepository,
        @LegacyClientBinding("notificationTokenRepository") notificationTokenRepository: NotificationTokenRepository,
    ): DeregisterTokenUseCase = DeregisterTokenUseCaseImpl(clientRepository, notificationTokenRepository)

    @Provides
    @LegacyClientBinding("mlsKeyPackageCountUseCase")
    fun provideClientMlsKeyPackageCountUseCase(
        @LegacyClientBinding("keyPackageRepository") keyPackageRepository: KeyPackageRepository,
        @LegacyClientBinding("keyPackageLimitsProvider") keyPackageLimitsProvider: KeyPackageLimitsProvider,
        @LegacyClientBinding("clientIdProvider") clientIdProvider: CurrentClientIdProvider,
        @LegacyClientBinding("userConfigRepository") userConfigRepository: UserConfigRepository,
        @LegacyClientBinding("transactionProvider") transactionProvider: CryptoTransactionProvider,
    ): MLSKeyPackageCountUseCase =
        MLSKeyPackageCountUseCaseImpl(
            keyPackageRepository = keyPackageRepository,
            currentClientIdProvider = clientIdProvider,
            keyPackageLimitsProvider = keyPackageLimitsProvider,
            userConfigRepository = userConfigRepository,
            transactionProvider = transactionProvider
        )

    @Provides
    @LegacyClientBinding("restartSlowSyncProcessForRecoveryUseCase")
    fun provideClientRestartSlowSyncProcessForRecoveryUseCase(
        @LegacyClientBinding("slowSyncRepository") slowSyncRepository: SlowSyncRepository,
    ): RestartSlowSyncProcessForRecoveryUseCase = RestartSlowSyncProcessForRecoveryUseCaseImpl(slowSyncRepository)

    @Provides
    @LegacyClientBinding("refillKeyPackages")
    fun provideClientRefillKeyPackages(
        @LegacyClientBinding("keyPackageRepository") keyPackageRepository: KeyPackageRepository,
        @LegacyClientBinding("keyPackageLimitsProvider") keyPackageLimitsProvider: KeyPackageLimitsProvider,
        @LegacyClientBinding("selfUserId") selfUserId: UserId,
        @LegacyClientBinding("clientIdProvider") clientIdProvider: CurrentClientIdProvider,
        @LegacyClientBinding("userConfigRepository") userConfigRepository: UserConfigRepository,
        @LegacyClientBinding("cryptoStateChangeHookNotifier") cryptoStateChangeHookNotifier: CryptoStateChangeHookNotifier,
    ): RefillKeyPackagesUseCase =
        RefillKeyPackagesUseCaseImpl(
            keyPackageRepository = keyPackageRepository,
            keyPackageLimitsProvider = keyPackageLimitsProvider,
            currentClientIdProvider = clientIdProvider,
            selfUserId = selfUserId,
            cryptoStateChangeHookNotifier = cryptoStateChangeHookNotifier,
            userConfigRepository = userConfigRepository,
        )

    @Provides
    @LegacyClientBinding("observeCurrentClientId")
    fun provideClientObserveCurrentClientId(
        @LegacyClientBinding("clientRepository") clientRepository: ClientRepository,
    ): ObserveCurrentClientIdUseCase = ObserveCurrentClientIdUseCaseImpl(clientRepository)

    @Provides
    @LegacyClientBinding("clearClientData")
    fun provideClientClearClientData(
        @LegacyClientBinding("mlsClientProvider") mlsClientProvider: MLSClientProvider,
        @LegacyClientBinding("proteusClientProvider") proteusClientProvider: ProteusClientProvider,
    ): ClearClientDataUseCase = ClearClientDataUseCaseImpl(mlsClientProvider, proteusClientProvider)

    @Provides
    @LegacyClientBinding("getProteusFingerprint")
    fun provideClientGetProteusFingerprint(
        @LegacyClientBinding("transactionProvider") transactionProvider: CryptoTransactionProvider,
    ): GetProteusFingerprintUseCase = GetProteusFingerprintUseCaseImpl(transactionProvider)

    @Provides
    @LegacyClientBinding("verifyExistingClientUseCase")
    fun provideClientVerifyExistingClientUseCase(
        @LegacyClientBinding("clientRepository") clientRepository: ClientRepository,
        @LegacyClientBinding("selfUserId") selfUserId: UserId,
        @LegacyClientBinding("isAllowedToRegisterMLSClient") isAllowedToRegisterMLSClient: IsAllowedToRegisterMLSClientUseCase,
        @LegacyClientBinding("slowSyncRepository") slowSyncRepository: SlowSyncRepository,
        @LegacyClientBinding("registerMLSClientUseCase") registerMLSClientUseCase: RegisterMLSClientUseCase,
    ): VerifyExistingClientUseCase =
        VerifyExistingClientUseCaseImpl(
            selfUserId,
            clientRepository,
            isAllowedToRegisterMLSClient,
            registerMLSClientUseCase,
            slowSyncRepository
        )

    @Provides
    @LegacyClientBinding("importClient")
    fun provideClientImportClient(
        @LegacyClientBinding("clientRepository") clientRepository: ClientRepository,
        @LegacyClientBinding("getOrRegister") getOrRegister: GetOrRegisterClientUseCase,
    ): ImportClientUseCase =
        ImportClientUseCaseImpl(
            clientRepository,
            getOrRegister
        )

    @Provides
    @LegacyClientBinding("getOrRegister")
    fun provideClientGetOrRegister(
        @LegacyClientBinding("clientRepository") clientRepository: ClientRepository,
        @LegacyClientBinding("pushTokenRepository") pushTokenRepository: PushTokenRepository,
        @LegacyClientBinding("logoutRepository") logoutRepository: LogoutRepository,
        @LegacyClientBinding("upgradeCurrentSessionUseCase") upgradeCurrentSessionUseCase: UpgradeCurrentSessionUseCase,
        @LegacyClientBinding("selfUserId") selfUserId: UserId,
        @LegacyClientBinding("cachedClientIdClearer") cachedClientIdClearer: CachedClientIdClearer,
        @LegacyClientBinding("syncFeatureConfigsUseCase") syncFeatureConfigsUseCase: SyncFeatureConfigsUseCase,
        @LegacyClientBinding("originatingScope") originatingScope: CoroutineScope,
        @LegacyClientBinding("userSessionScopeProvider") userSessionScopeProvider: UserSessionScopeProvider,
        @LegacyClientBinding("register") register: RegisterClientUseCase,
        @LegacyClientBinding("clearClientData") clearClientData: ClearClientDataUseCase,
        @LegacyClientBinding("verifyExistingClientUseCase") verifyExistingClientUseCase: VerifyExistingClientUseCase,
    ): GetOrRegisterClientUseCase =
        GetOrRegisterClientUseCaseImpl(
            clientRepository,
            pushTokenRepository,
            logoutRepository,
            register,
            clearClientData,
            verifyExistingClientUseCase,
            upgradeCurrentSessionUseCase,
            cachedClientIdClearer,
            syncFeatureConfigsUseCase,
            selfUserId,
            originatingScope,
            userSessionScopeProvider,
        )

    @Provides
    @LegacyClientBinding("remoteClientFingerPrint")
    fun provideClientRemoteClientFingerPrint(
        @LegacyClientBinding("preKeyRepository") preKeyRepository: PreKeyRepository,
        @LegacyClientBinding("transactionProvider") transactionProvider: CryptoTransactionProvider,
    ): ClientFingerprintUseCase = ClientFingerprintUseCaseImpl(transactionProvider, preKeyRepository)

    @Provides
    @LegacyClientBinding("updateClientVerificationStatus")
    fun provideClientUpdateClientVerificationStatus(
        @LegacyClientBinding("clientRepository") clientRepository: ClientRepository,
    ): UpdateClientVerificationStatusUseCase = UpdateClientVerificationStatusUseCase(clientRepository)
}
