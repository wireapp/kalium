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
@file:OptIn(InternalKaliumApi::class, DelicateKaliumApi::class)

package com.wire.kalium.logic.di

import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.call.EndCallOnMLSResetUseCase
import com.wire.kalium.logic.data.call.VideoStateChecker
import com.wire.kalium.logic.data.call.VideoStateCheckerImpl
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.client.CryptoTransactionProvider
import com.wire.kalium.logic.data.conversation.ConversationMetaDataRepository
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.JoinExistingMLSConversationsUseCase
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.conversation.SubconversationRepositoryImpl
import com.wire.kalium.logic.data.conversation.UpdateConversationProtocolUseCase
import com.wire.kalium.logic.data.event.EventDataSource
import com.wire.kalium.logic.data.event.EventRepository
import com.wire.kalium.logic.data.featureConfig.FeatureConfigRepository
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.id.FederatedIdMapper
import com.wire.kalium.logic.data.id.SelfTeamIdProvider
import com.wire.kalium.logic.data.message.PendingSubconversationProposals
import com.wire.kalium.logic.data.message.PendingSubconversationProposalsImpl
import com.wire.kalium.logic.data.message.SystemMessageInserterImpl
import com.wire.kalium.logic.data.mlspublickeys.MLSPublicKeysRepository
import com.wire.kalium.logic.data.mlspublickeys.MLSPublicKeysRepositoryImpl
import com.wire.kalium.logic.data.sync.IncrementalSyncRepository
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.TimestampKeyRepositoryImpl
import com.wire.kalium.logic.feature.auth.LogoutUseCase
import com.wire.kalium.logic.feature.call.CallBackgroundManager
import com.wire.kalium.logic.feature.call.CallBackgroundManagerImpl
import com.wire.kalium.logic.feature.call.CallManager
import com.wire.kalium.logic.feature.call.usecase.ConversationClientsInCallUpdater
import com.wire.kalium.logic.feature.call.usecase.ConversationClientsInCallUpdaterImpl
import com.wire.kalium.logic.feature.call.usecase.EndCallOnMLSResetUseCaseImpl
import com.wire.kalium.logic.feature.call.usecase.EpochInfoUpdater
import com.wire.kalium.logic.feature.call.usecase.EpochInfoUpdaterImpl
import com.wire.kalium.logic.feature.call.usecase.GetCallConversationTypeProvider
import com.wire.kalium.logic.feature.call.usecase.GetCallConversationTypeProviderImpl
import com.wire.kalium.logic.feature.call.usecase.UpdateConversationClientsForCurrentCallUseCase
import com.wire.kalium.logic.feature.call.usecase.UpdateConversationClientsForCurrentCallUseCaseImpl
import com.wire.kalium.logic.feature.client.IsAllowedToRegisterMLSClientUseCase
import com.wire.kalium.logic.feature.client.LegacyClientBinding
import com.wire.kalium.logic.feature.client.MLSClientManager
import com.wire.kalium.logic.feature.client.MLSClientManagerImpl
import com.wire.kalium.logic.feature.client.RegisterMLSClientUseCaseImpl
import com.wire.kalium.logic.feature.conversation.ConversationsRecoveryManager
import com.wire.kalium.logic.feature.conversation.ConversationsRecoveryManagerImpl
import com.wire.kalium.logic.feature.conversation.JoinPendingMLSConversationsManager
import com.wire.kalium.logic.feature.conversation.JoinPendingMLSConversationsManagerImpl
import com.wire.kalium.logic.feature.conversation.LegacyConversationBinding
import com.wire.kalium.logic.feature.conversation.MLSConversationsRecoveryManager
import com.wire.kalium.logic.feature.conversation.MLSConversationsRecoveryManagerImpl
import com.wire.kalium.logic.feature.conversation.MLSFaultyKeysConversationsRepairUseCaseImpl
import com.wire.kalium.logic.feature.conversation.RecoverMLSConversationsUseCase
import com.wire.kalium.logic.feature.conversation.keyingmaterials.KeyingMaterialsManager
import com.wire.kalium.logic.feature.conversation.keyingmaterials.KeyingMaterialsManagerImpl
import com.wire.kalium.logic.feature.conversation.keyingmaterials.UpdateKeyingMaterialsUseCase
import com.wire.kalium.logic.feature.debug.LegacyDebugBinding
import com.wire.kalium.logic.feature.debug.RepairFaultyRemovalKeysUseCase
import com.wire.kalium.logic.feature.featureConfig.handler.MLSConfigHandler
import com.wire.kalium.logic.feature.featureConfig.handler.MLSMigrationConfigHandler
import com.wire.kalium.logic.feature.keypackage.KeyPackageManager
import com.wire.kalium.logic.feature.keypackage.KeyPackageManagerImpl
import com.wire.kalium.logic.feature.keypackage.MLSKeyPackageCountUseCase
import com.wire.kalium.logic.feature.keypackage.RefillKeyPackagesUseCase
import com.wire.kalium.logic.feature.message.AddSystemMessageToAllConversationsUseCase
import com.wire.kalium.logic.feature.message.PendingProposalScheduler
import com.wire.kalium.logic.feature.message.PendingProposalSchedulerImpl
import com.wire.kalium.logic.feature.mlsmigration.MLSMigrationManager
import com.wire.kalium.logic.feature.mlsmigration.MLSMigrationManagerImpl
import com.wire.kalium.logic.feature.mlsmigration.MLSMigrationWorkerImpl
import com.wire.kalium.logic.feature.mlsmigration.MLSMigrator
import com.wire.kalium.logic.feature.mlsmigration.MLSMigratorImpl
import com.wire.kalium.logic.feature.session.UpgradeCurrentSessionUseCaseImpl
import com.wire.kalium.logic.feature.session.token.AccessTokenRefresher
import com.wire.kalium.logic.feature.user.IsMLSEnabledUseCase
import com.wire.kalium.logic.feature.user.LegacyUserBinding
import com.wire.kalium.logic.featureFlags.FeatureSupport
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.network.ApiMigrationManager
import com.wire.kalium.logic.network.ApiMigrationV3
import com.wire.kalium.logic.sync.SyncStateObserver
import com.wire.kalium.logic.sync.UserSessionWorkScheduler
import com.wire.kalium.logic.sync.incremental.EventGatherer
import com.wire.kalium.logic.sync.incremental.EventProcessor
import com.wire.kalium.logic.sync.incremental.IncrementalSyncManager
import com.wire.kalium.logic.sync.incremental.IncrementalSyncRecoveryHandlerImpl
import com.wire.kalium.logic.sync.incremental.IncrementalSyncWorker
import com.wire.kalium.logic.sync.local.LocalEventManagerImpl
import com.wire.kalium.logic.sync.local.LocalEventRepository
import com.wire.kalium.logic.sync.receiver.handler.SessionRefreshSuggestedEventHandler
import com.wire.kalium.logic.sync.receiver.handler.SessionRefreshSuggestedEventHandlerImpl
import com.wire.kalium.logic.sync.slow.RestartSlowSyncProcessForRecoveryUseCase
import com.wire.kalium.logic.sync.slow.SlowSyncCriteriaProvider
import com.wire.kalium.logic.sync.slow.SlowSyncManager
import com.wire.kalium.logic.sync.slow.SlowSyncRecoveryHandler
import com.wire.kalium.logic.sync.slow.SlowSyncRecoveryHandlerImpl
import com.wire.kalium.logic.sync.slow.SlowSyncWorker
import com.wire.kalium.logic.sync.slow.migration.SyncMigrationStepsProvider
import com.wire.kalium.logic.sync.slow.migration.SyncMigrationStepsProviderImpl
import com.wire.kalium.network.NetworkStateObserver
import com.wire.kalium.network.networkContainer.AuthenticatedNetworkContainer
import com.wire.kalium.network.session.SessionManager
import com.wire.kalium.persistence.client.ClientRegistrationStorage
import com.wire.kalium.userstorage.di.UserStorage
import com.wire.kalium.util.DelicateKaliumApi
import com.wire.kalium.util.InternalKaliumApi
import dev.zacsweers.metro.Provider
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope

@Suppress("TooManyFunctions", "LongParameterList", "LargeClass") // Individual qualified construction edges.
internal interface UserSessionBindings4 {
    @Provides
    @UserSessionBinding("slowSyncRecoveryHandler")
    fun userSessionProvideSlowSyncRecoveryHandler(
        @UserSessionBinding("logout")
        logout: Provider<LogoutUseCase>,
    ): SlowSyncRecoveryHandler =
        SlowSyncRecoveryHandlerImpl(logout())

    @Provides
    @UserSessionBinding("syncMigrationStepsProvider")
    @SingleIn(UserLifetime::class)
    fun userSessionProvideSyncMigrationStepsProvider(
        @UserSessionBinding("syncMigrationStepsProviderSyncMigrationStepsProviderImpl")
        syncMigrationStepsProviderSyncMigrationStepsProviderImpl: Provider<SyncMigrationStepsProviderImpl>,
    ): UserSessionValue<() -> SyncMigrationStepsProvider> =
        UserSessionValue({
            syncMigrationStepsProviderSyncMigrationStepsProviderImpl()
        })

    @Provides
    @UserSessionBinding("slowSyncManager")
    @SingleIn(UserLifetime::class)
    fun userSessionProvideSlowSyncManager(
        @UserSessionBinding("syncMigrationStepsProvider")
        syncMigrationStepsProvider: Provider<UserSessionValue<() -> SyncMigrationStepsProvider>>,
        @UserSessionBinding("slowSyncCriteriaProvider")
        slowSyncCriteriaProvider: Provider<SlowSyncCriteriaProvider>,
        @UserSessionBinding("slowSyncRecoveryHandler")
        slowSyncRecoveryHandler: Provider<SlowSyncRecoveryHandler>,
        @UserSessionBinding("networkStateObserver")
        networkStateObserver: Provider<NetworkStateObserver>,
        @UserSessionBinding("slowSyncRepository")
        slowSyncRepository: Provider<SlowSyncRepository>,
        @UserSessionBinding("userScopedLogger")
        userScopedLogger: Provider<KaliumLogger>,
        @UserSessionBinding("sessionManager")
        sessionManager: Provider<SessionManager>,
        @UserSessionBinding("slowSyncWorker")
        slowSyncWorker: Provider<SlowSyncWorker>,
    ): SlowSyncManager =
        SlowSyncManager(
            slowSyncCriteriaProvider = slowSyncCriteriaProvider(),
            slowSyncRepository = slowSyncRepository(),
            slowSyncWorker = slowSyncWorker(),
            slowSyncRecoveryHandler = slowSyncRecoveryHandler(),
            networkStateObserver = networkStateObserver(),
            syncMigrationStepsProvider = syncMigrationStepsProvider().value,
            userScopedLogger = userScopedLogger(),
            apiVersion = sessionManager().serverConfig().metaData.commonApiVersion.version,
        )

    @Provides
    @UserSessionBinding("mlsConversationsRecoveryManager")
    @SingleIn(UserLifetime::class)
    fun userSessionProvideMlsConversationsRecoveryManager(
        @UserSessionBinding("recoverMLSConversationsUseCase")
        recoverMLSConversationsUseCase: Provider<RecoverMLSConversationsUseCase>,
        @UserSessionBinding("cryptoTransactionProvider")
        cryptoTransactionProvider: Provider<CryptoTransactionProvider>,
        @UserSessionBinding("incrementalSyncRepository")
        incrementalSyncRepository: Provider<IncrementalSyncRepository>,
        @UserSessionBinding("slowSyncRepository")
        slowSyncRepository: Provider<SlowSyncRepository>,
        @UserSessionBinding("clientRepository")
        clientRepository: Provider<ClientRepository>,
        @UserSessionBinding("userScopedLogger")
        userScopedLogger: Provider<KaliumLogger>,
        @UserSessionBinding("featureSupport")
        featureSupport: Provider<FeatureSupport>,
    ): MLSConversationsRecoveryManager =
        MLSConversationsRecoveryManagerImpl(
            featureSupport(),
            incrementalSyncRepository(),
            clientRepository(),
            recoverMLSConversationsUseCase(),
            slowSyncRepository(),
            cryptoTransactionProvider(),
            userScopedLogger()
        )

    @Provides
    @UserSessionBinding("joinPendingMLSConversationsManager")
    @SingleIn(UserLifetime::class)
    fun userSessionProvideJoinPendingMLSConversationsManager(
        @UserSessionBinding("joinExistingMLSConversations")
        joinExistingMLSConversations: Provider<JoinExistingMLSConversationsUseCase>,
        @UserSessionBinding("incrementalSyncRepository")
        incrementalSyncRepository: Provider<IncrementalSyncRepository>,
        @UserSessionBinding("userConfigRepository")
        userConfigRepository: Provider<UserConfigRepository>,
        @UserSessionBinding("userScopedLogger")
        userScopedLogger: Provider<KaliumLogger>,
    ): JoinPendingMLSConversationsManager =
        JoinPendingMLSConversationsManagerImpl(
            incrementalSyncRepository(),
            userConfigRepository(),
            joinExistingMLSConversations(),
            userScopedLogger()
        )

    @Provides
    @UserSessionBinding("mlsFaultyKeysConversationsRepairUseCase")
    @SingleIn(UserLifetime::class)
    fun userSessionProvideMlsFaultyKeysConversationsRepairUseCase(
        @LegacyDebugBinding("repairFaultyRemovalKeysUseCase")
        debugRepairFaultyRemovalKeysUseCase: Provider<RepairFaultyRemovalKeysUseCase>,
        @UserSessionBinding("userConfigRepository")
        userConfigRepository: Provider<UserConfigRepository>,
        @UserSessionBinding("syncStateObserver")
        syncStateObserver: Provider<UserSessionValue<Lazy<SyncStateObserver>>>,
        @UserSessionBinding("userScopedLogger")
        userScopedLogger: Provider<KaliumLogger>,
        @UserSessionBinding("kaliumConfigs")
        kaliumConfigs: Provider<KaliumConfigs>,
        @UserSessionBinding("userId")
        userId: Provider<UserId>,
    ): MLSFaultyKeysConversationsRepairUseCaseImpl =
        MLSFaultyKeysConversationsRepairUseCaseImpl(
            selfUserId = userId(),
            syncStateObserver = syncStateObserver().value.value,
            kaliumConfigs = kaliumConfigs(),
            userConfigRepository = userConfigRepository(),
            repairFaultyRemovalKeys = debugRepairFaultyRemovalKeysUseCase(),
            kaliumLogger = userScopedLogger()
        )

    @Provides
    @UserSessionBinding("conversationsRecoveryManager")
    @SingleIn(UserLifetime::class)
    fun userSessionProvideConversationsRecoveryManager(
        @UserSessionBinding("addSystemMessageToAllConversationsUseCase")
        addSystemMessageToAllConversationsUseCase: Provider<AddSystemMessageToAllConversationsUseCase>,
        @UserSessionBinding("incrementalSyncRepository")
        incrementalSyncRepository: Provider<IncrementalSyncRepository>,
        @UserSessionBinding("slowSyncRepository")
        slowSyncRepository: Provider<SlowSyncRepository>,
        @UserSessionBinding("userScopedLogger")
        userScopedLogger: Provider<KaliumLogger>,
    ): ConversationsRecoveryManager =
        ConversationsRecoveryManagerImpl(
            incrementalSyncRepository(),
            addSystemMessageToAllConversationsUseCase(),
            slowSyncRepository(),
            userScopedLogger()
        )

    @Provides
    @UserSessionBinding("incrementalSyncWorker")
    @SingleIn(UserLifetime::class)
    fun userSessionProvideIncrementalSyncWorker(
        @UserSessionBinding("incrementalWorkerFactory")
        incrementalWorkerFactory: Provider<UserSessionIncrementalWorkerFactory>,
        @UserSessionBinding("eventGatherer")
        eventGatherer: Provider<EventGatherer>,
    ): IncrementalSyncWorker =
        incrementalWorkerFactory().create(eventGatherer())

    @Provides
    @UserSessionBinding("incrementalSyncRecoveryHandler")
    fun userSessionProvideIncrementalSyncRecoveryHandler(
        @UserSessionBinding("restartSlowSyncProcessForRecoveryUseCase")
        restartSlowSyncProcessForRecoveryUseCase: Provider<RestartSlowSyncProcessForRecoveryUseCase>,
        @UserSessionBinding("userScopedLogger")
        userScopedLogger: Provider<KaliumLogger>,
        @UserSessionBinding("eventRepository")
        eventRepository: Provider<EventRepository>,
    ): IncrementalSyncRecoveryHandlerImpl =
        IncrementalSyncRecoveryHandlerImpl(
            restartSlowSyncProcessForRecoveryUseCase(),
            eventRepository(),
            userScopedLogger(),
        )

    @Provides
    @UserSessionBinding("incrementalSyncManager")
    @SingleIn(UserLifetime::class)
    fun userSessionProvideIncrementalSyncManager(
        @UserSessionBinding("incrementalSyncRecoveryHandler")
        incrementalSyncRecoveryHandler: Provider<IncrementalSyncRecoveryHandlerImpl>,
        @UserSessionBinding("incrementalSyncRepository")
        incrementalSyncRepository: Provider<IncrementalSyncRepository>,
        @UserSessionBinding("userSessionWorkScheduler")
        userSessionWorkScheduler: Provider<UserSessionWorkScheduler>,
        @UserSessionBinding("incrementalSyncWorker")
        incrementalSyncWorker: Provider<IncrementalSyncWorker>,
        @UserSessionBinding("networkStateObserver")
        networkStateObserver: Provider<NetworkStateObserver>,
        @UserSessionBinding("userScopedLogger")
        userScopedLogger: Provider<KaliumLogger>,
    ): IncrementalSyncManager =
        IncrementalSyncManager(
            incrementalSyncWorker(),
            incrementalSyncRepository(),
            incrementalSyncRecoveryHandler(),
            networkStateObserver(),
            userScopedLogger(),
            userSessionWorkScheduler(),
        )

    @Provides
    @UserSessionBinding("localEventManager")
    @SingleIn(UserLifetime::class)
    fun userSessionProvideLocalEventManager(
        @UserSessionBinding("cryptoTransactionProvider")
        cryptoTransactionProvider: Provider<CryptoTransactionProvider>,
        @UserSessionBinding("sessionCoroutineScope")
        sessionCoroutineScope: Provider<CoroutineScope>,
        @UserSessionBinding("localEventRepository")
        localEventRepository: Provider<LocalEventRepository>,
        @UserSessionBinding("eventProcessor")
        eventProcessor: Provider<EventProcessor>,
    ): LocalEventManagerImpl =
        LocalEventManagerImpl(
            localEventRepository(),
            eventProcessor(),
            cryptoTransactionProvider(),
            sessionCoroutineScope()
        )

    @Provides
    @UserSessionBinding("upgradeCurrentSessionUseCase")
    fun userSessionProvideUpgradeCurrentSessionUseCase(
        @UserSessionBinding("authenticatedNetworkContainer")
        authenticatedNetworkContainer: Provider<AuthenticatedNetworkContainer>,
        @UserSessionBinding("accessTokenRefresher")
        accessTokenRefresher: Provider<AccessTokenRefresher>,
        @UserSessionBinding("sessionManager")
        sessionManager: Provider<SessionManager>,
    ): UpgradeCurrentSessionUseCaseImpl =
        UpgradeCurrentSessionUseCaseImpl(
            authenticatedNetworkContainer(),
            accessTokenRefresher(),
            sessionManager()
        )

    @Provides
    @UserSessionBinding("sessionRefreshSuggestedEventHandler")
    fun userSessionProvideSessionRefreshSuggestedEventHandler(
        @UserSessionBinding("authenticatedNetworkContainer")
        authenticatedNetworkContainer: Provider<AuthenticatedNetworkContainer>,
        @UserSessionBinding("sessionManager")
        sessionManager: Provider<SessionManager>,
    ): SessionRefreshSuggestedEventHandler =
        SessionRefreshSuggestedEventHandlerImpl(
            authenticatedNetworkContainer(),
            sessionManager()
        )

    @Provides
    @UserSessionBinding("apiMigrations")
    @SingleIn(UserLifetime::class)
    fun userSessionProvideApiMigrations(
        @UserSessionBinding("apiMigrationsApiMigrationV3")
        apiMigrationsApiMigrationV3: Provider<ApiMigrationV3>,
    ): List<Pair<Int, ApiMigrationV3>> =
        listOf(
            Pair(API_MIGRATION_V3_VERSION, apiMigrationsApiMigrationV3())
        )

    @Provides
    @UserSessionBinding("apiMigrationManager")
    fun userSessionProvideApiMigrationManager(
        @UserSessionBinding("sessionManager")
        sessionManager: Provider<SessionManager>,
        @UserSessionBinding("apiMigrations")
        apiMigrations: Provider<List<Pair<Int, ApiMigrationV3>>>,
        @UserSessionBinding("userStorage")
        userStorage: Provider<UserStorage>,
    ): ApiMigrationManager =
        ApiMigrationManager(
            sessionManager().serverConfig().metaData.commonApiVersion.version,
            userStorage().database.metadataDAO,
            apiMigrations()
        )

    @Provides
    @UserSessionBinding("eventRepository")
    @SingleIn(UserLifetime::class)
    fun userSessionProvideEventRepository(
        @UserSessionBinding("restartSlowSyncProcessForRecoveryUseCase")
        restartSlowSyncProcessForRecoveryUseCase: Provider<RestartSlowSyncProcessForRecoveryUseCase>,
        @UserSessionBinding("authenticatedNetworkContainer")
        authenticatedNetworkContainer: Provider<AuthenticatedNetworkContainer>,
        @UserSessionBinding("clientRegistrationStorage")
        clientRegistrationStorage: Provider<ClientRegistrationStorage>,
        @UserSessionBinding("clientIdProvider")
        clientIdProvider: Provider<CurrentClientIdProvider>,
        @UserSessionBinding("userScopedLogger")
        userScopedLogger: Provider<KaliumLogger>,
        @UserSessionBinding("userStorage")
        userStorage: Provider<UserStorage>,
        @UserSessionBinding("userId")
        userId: Provider<UserId>,
    ): EventRepository =
        EventDataSource(
            notificationApi = authenticatedNetworkContainer().notificationApi,
            metadataDAO = userStorage().database.metadataDAO,
            eventDAO = userStorage().database.eventDAO,
            currentClientId = clientIdProvider(),
            clientRegistrationStorage = clientRegistrationStorage(),
            restartSlowSyncProcessForRecovery = restartSlowSyncProcessForRecoveryUseCase(),
            selfUserId = userId(),
            logger = userScopedLogger()
        )

    @Provides
    @UserSessionBinding("mlsMigrator")
    fun userSessionProvideMlsMigrator(
        @UserSessionBinding("updateConversationProtocolUseCase")
        updateConversationProtocolUseCase: Provider<UpdateConversationProtocolUseCase>,
        @UserSessionBinding("cryptoTransactionProvider")
        cryptoTransactionProvider: Provider<CryptoTransactionProvider>,
        @UserSessionBinding("mlsConversationRepository")
        mlsConversationRepository: Provider<MLSConversationRepository>,
        @UserSessionBinding("conversationRepository")
        conversationRepository: Provider<ConversationRepository>,
        @UserSessionBinding("systemMessageInserter")
        systemMessageInserter: Provider<SystemMessageInserterImpl>,
        @UserSessionBinding("callRepository")
        callRepository: Provider<CallRepository>,
        @UserSessionBinding("userRepository")
        userRepository: Provider<UserRepository>,
        @UserSessionBinding("selfTeamId")
        selfTeamId: Provider<SelfTeamIdProvider>,
        @UserSessionBinding("userId")
        userId: Provider<UserId>,
    ): MLSMigrator =
        MLSMigratorImpl(
            userId(),
            selfTeamId(),
            userRepository(),
            conversationRepository(),
            mlsConversationRepository(),
            systemMessageInserter(),
            callRepository(),
            updateConversationProtocolUseCase(),
            cryptoTransactionProvider()
        )

    @Provides
    @UserSessionBinding("keyPackageManager")
    @SingleIn(UserLifetime::class)
    fun userSessionProvideKeyPackageManager(
        @LegacyClientBinding("refillKeyPackages")
        clientRefillKeyPackages: Provider<RefillKeyPackagesUseCase>,
        @LegacyClientBinding("mlsKeyPackageCountUseCase")
        clientMlsKeyPackageCountUseCase: Provider<MLSKeyPackageCountUseCase>,
        @LegacyUserBinding("timestampKeyRepository")
        userTimestampKeyRepository: Provider<TimestampKeyRepositoryImpl>,
        @UserSessionBinding("cryptoTransactionProvider")
        cryptoTransactionProvider: Provider<CryptoTransactionProvider>,
        @UserSessionBinding("incrementalSyncRepository")
        incrementalSyncRepository: Provider<IncrementalSyncRepository>,
        @UserSessionBinding("clientRepository")
        clientRepository: Provider<ClientRepository>,
        @UserSessionBinding("featureSupport")
        featureSupport: Provider<FeatureSupport>,
    ): KeyPackageManager =
        KeyPackageManagerImpl(
            featureSupport(),
            incrementalSyncRepository(),
            lazy { clientRepository() },
            lazy { clientRefillKeyPackages() },
            lazy { clientMlsKeyPackageCountUseCase() },
            lazy { userTimestampKeyRepository() },
            cryptoTransactionProvider()
        )

    @Provides
    @UserSessionBinding("keyingMaterialsManager")
    fun userSessionProvideKeyingMaterialsManager(
        @LegacyConversationBinding("updateMLSGroupsKeyingMaterials")
        conversationUpdateMLSGroupsKeyingMaterials: Provider<UpdateKeyingMaterialsUseCase>,
        @LegacyUserBinding("timestampKeyRepository")
        userTimestampKeyRepository: Provider<TimestampKeyRepositoryImpl>,
        @UserSessionBinding("sessionCoroutineScope")
        sessionCoroutineScope: Provider<CoroutineScope>,
        @UserSessionBinding("syncStateObserver")
        syncStateObserver: Provider<UserSessionValue<Lazy<SyncStateObserver>>>,
        @UserSessionBinding("clientRepository")
        clientRepository: Provider<ClientRepository>,
        @UserSessionBinding("featureSupport")
        featureSupport: Provider<FeatureSupport>,
    ): KeyingMaterialsManager =
        KeyingMaterialsManagerImpl(
            featureSupport(),
            syncStateObserver().value.value,
            lazy { clientRepository() },
            lazy { conversationUpdateMLSGroupsKeyingMaterials() },
            lazy { userTimestampKeyRepository() },
            sessionCoroutineScope(),
        )

    @Provides
    @UserSessionBinding("mlsClientManager")
    fun userSessionProvideMlsClientManager(
        @UserSessionBinding("mlsClientManagerRegisterMLSClientUseCaseImpl")
        mlsClientManagerRegisterMLSClientUseCaseImpl: Provider<RegisterMLSClientUseCaseImpl>,
        @UserSessionBinding("isAllowedToRegisterMLSClient")
        isAllowedToRegisterMLSClient: Provider<IsAllowedToRegisterMLSClientUseCase>,
        @UserSessionBinding("sessionCoroutineScope")
        sessionCoroutineScope: Provider<CoroutineScope>,
        @UserSessionBinding("slowSyncRepository")
        slowSyncRepository: Provider<SlowSyncRepository>,
        @UserSessionBinding("syncStateObserver")
        syncStateObserver: Provider<UserSessionValue<Lazy<SyncStateObserver>>>,
        @UserSessionBinding("clientIdProvider")
        clientIdProvider: Provider<CurrentClientIdProvider>,
        @UserSessionBinding("clientRepository")
        clientRepository: Provider<ClientRepository>,
    ): MLSClientManager =
        MLSClientManagerImpl(
            clientIdProvider(),
            isAllowedToRegisterMLSClient(),
            syncStateObserver().value.value,
            lazy { slowSyncRepository() },
            lazy { clientRepository() },
            lazy {
                mlsClientManagerRegisterMLSClientUseCaseImpl()
            },
            sessionCoroutineScope(),
        )

    @Provides
    @UserSessionBinding("mlsMigrationWorker")
    fun userSessionProvideMlsMigrationWorker(
        @UserSessionBinding("mlsMigrationConfigHandler")
        mlsMigrationConfigHandler: Provider<MLSMigrationConfigHandler>,
        @UserSessionBinding("featureConfigRepository")
        featureConfigRepository: Provider<FeatureConfigRepository>,
        @UserSessionBinding("userConfigRepository")
        userConfigRepository: Provider<UserConfigRepository>,
        @UserSessionBinding("mlsConfigHandler")
        mlsConfigHandler: Provider<MLSConfigHandler>,
        @UserSessionBinding("mlsMigrator")
        mlsMigrator: Provider<MLSMigrator>,
    ): MLSMigrationWorkerImpl =
        MLSMigrationWorkerImpl(
            userConfigRepository(),
            featureConfigRepository(),
            mlsConfigHandler(),
            mlsMigrationConfigHandler(),
            mlsMigrator(),
        )

    @Provides
    @UserSessionBinding("mlsMigrationManager")
    fun userSessionProvideMlsMigrationManager(
        @LegacyUserBinding("timestampKeyRepository")
        userTimestampKeyRepository: Provider<TimestampKeyRepositoryImpl>,
        @UserSessionBinding("sessionCoroutineScope")
        sessionCoroutineScope: Provider<CoroutineScope>,
        @UserSessionBinding("mlsMigrationWorker")
        mlsMigrationWorker: Provider<MLSMigrationWorkerImpl>,
        @UserSessionBinding("syncStateObserver")
        syncStateObserver: Provider<UserSessionValue<Lazy<SyncStateObserver>>>,
        @UserSessionBinding("clientRepository")
        clientRepository: Provider<ClientRepository>,
        @UserSessionBinding("kaliumConfigs")
        kaliumConfigs: Provider<KaliumConfigs>,
        @UserSessionBinding("isMLSEnabled")
        isMLSEnabled: Provider<IsMLSEnabledUseCase>,
    ): MLSMigrationManager =
        MLSMigrationManagerImpl(
            kaliumConfigs(),
            isMLSEnabled(),
            syncStateObserver().value.value,
            lazy { clientRepository() },
            lazy { userTimestampKeyRepository() },
            lazy { mlsMigrationWorker() },
            sessionCoroutineScope(),
        )

    @Provides
    @UserSessionBinding("mlsPublicKeysRepository")
    fun userSessionProvideMlsPublicKeysRepository(
        @UserSessionBinding("authenticatedNetworkContainer")
        authenticatedNetworkContainer: Provider<AuthenticatedNetworkContainer>,
    ): MLSPublicKeysRepository =
        MLSPublicKeysRepositoryImpl(
            authenticatedNetworkContainer().mlsPublicKeyApi,
        )

    @Provides
    @UserSessionBinding("videoStateChecker")
    fun userSessionProvideVideoStateChecker(): VideoStateChecker =
        VideoStateCheckerImpl()

    @Provides
    @UserSessionBinding("pendingSubconversationProposals")
    @SingleIn(UserLifetime::class)
    fun userSessionProvidePendingSubconversationProposals(
        @UserSessionBinding("userStorage")
        userStorage: Provider<UserStorage>,
    ): PendingSubconversationProposals =
        PendingSubconversationProposalsImpl(userStorage().database.pendingSubconversationProposalDAO)

    @Provides
    @UserSessionBinding("pendingProposalScheduler")
    @SingleIn(UserLifetime::class)
    fun userSessionProvidePendingProposalScheduler(
        @UserSessionBinding("sessionCoroutineScope")
        sessionCoroutineScope: Provider<CoroutineScope>,
        @UserSessionBinding("pendingSubconversationProposals")
        pendingSubconversationProposals: Provider<PendingSubconversationProposals>,
        @UserSessionBinding("cryptoTransactionProvider")
        cryptoTransactionProvider: Provider<CryptoTransactionProvider>,
        @UserSessionBinding("incrementalSyncRepository")
        incrementalSyncRepository: Provider<IncrementalSyncRepository>,
        @UserSessionBinding("mlsConversationRepository")
        mlsConversationRepository: Provider<MLSConversationRepository>,
        @UserSessionBinding("subconversationRepository")
        subconversationRepository: Provider<SubconversationRepositoryImpl>,
    ): PendingProposalScheduler =
        PendingProposalSchedulerImpl(
            incrementalSyncRepository(),
            lazy { mlsConversationRepository() },
            lazy { subconversationRepository() },
            cryptoTransactionProvider(),
            pendingSubconversationProposals(),
            parentContext = sessionCoroutineScope().coroutineContext,
        )

    @Provides
    @UserSessionBinding("endCallOnMLSReset")
    @SingleIn(UserLifetime::class)
    fun userSessionProvideEndCallOnMLSReset(
        @UserSessionBinding("callRepository")
        callRepository: Provider<CallRepository>,
        @UserSessionBinding("callManager")
        callManager: Provider<UserSessionValue<Lazy<CallManager>>>,
    ): EndCallOnMLSResetUseCase =
        EndCallOnMLSResetUseCaseImpl(
            callManager = callManager().value,
            callRepository = callRepository(),
        )

    @Provides
    @UserSessionBinding("callBackgroundManager")
    @SingleIn(UserLifetime::class)
    fun userSessionProvideCallBackgroundManager(
        @UserSessionBinding("syncStateObserver")
        syncStateObserver: Provider<UserSessionValue<Lazy<SyncStateObserver>>>,
        @UserSessionBinding("userScopedLogger")
        userScopedLogger: Provider<KaliumLogger>,
        @UserSessionBinding("callManager")
        callManager: Provider<UserSessionValue<Lazy<CallManager>>>,
        @UserSessionBinding("userId")
        userId: Provider<UserId>,
    ): CallBackgroundManager =
        CallBackgroundManagerImpl(
            callManager = callManager().value,
            syncStateObserver = syncStateObserver().value,
            selfUserId = userId(),
            logger = userScopedLogger()
        )

    @Provides
    @UserSessionBinding("conversationClientsInCallUpdater")
    fun userSessionProvideConversationClientsInCallUpdater(
        @UserSessionBinding("conversationRepository")
        conversationRepository: Provider<ConversationRepository>,
        @UserSessionBinding("federatedIdMapper")
        federatedIdMapper: Provider<FederatedIdMapper>,
        @UserSessionBinding("callManager")
        callManager: Provider<UserSessionValue<Lazy<CallManager>>>,
    ): ConversationClientsInCallUpdater =
        ConversationClientsInCallUpdaterImpl(
            callManager = callManager().value,
            conversationRepository = conversationRepository(),
            federatedIdMapper = federatedIdMapper()
        )

    @Provides
    @UserSessionBinding("epochInfoUpdater")
    fun userSessionProvideEpochInfoUpdater(
        @UserSessionBinding("callRepository")
        callRepository: Provider<CallRepository>,
        @UserSessionBinding("callManager")
        callManager: Provider<UserSessionValue<Lazy<CallManager>>>,
    ): EpochInfoUpdater =
        EpochInfoUpdaterImpl(
            callManager = callManager().value,
            callRepository = callRepository()
        )

    @Provides
    @UserSessionBinding("getCallConversationType")
    @SingleIn(UserLifetime::class)
    fun userSessionProvideGetCallConversationType(
        @UserSessionBinding("conversationMetaDataRepository")
        conversationMetaDataRepository: Provider<ConversationMetaDataRepository>,
        @UserSessionBinding("userConfigRepository")
        userConfigRepository: Provider<UserConfigRepository>,
    ): GetCallConversationTypeProvider =
        GetCallConversationTypeProviderImpl(
            userConfigRepository = userConfigRepository(),
            conversationMetaDataRepository = conversationMetaDataRepository(),
        )

    @Provides
    @UserSessionBinding("updateConversationClientsForCurrentCall")
    fun userSessionProvideUpdateConversationClientsForCurrentCall(
        @UserSessionBinding("newUpdateConversationClientsForCurrentCall")
        newUpdateConversationClientsForCurrentCall: Provider<UpdateConversationClientsForCurrentCallUseCaseImpl>,
    ): UserSessionValue<Lazy<UpdateConversationClientsForCurrentCallUseCase>> =
        UserSessionValue(
            lazy {
                newUpdateConversationClientsForCurrentCall()
            }
        )

}

private const val API_MIGRATION_V3_VERSION = 3
