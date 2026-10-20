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
import com.wire.kalium.logic.cache.SelfConversationIdProvider
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.client.CryptoTransactionProvider
import com.wire.kalium.logic.data.client.IsClientAsyncNotificationsCapableProvider
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.FetchConversationsUseCase
import com.wire.kalium.logic.data.conversation.JoinExistingMLSConversationsUseCase
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.conversation.SubconversationRepositoryImpl
import com.wire.kalium.logic.data.conversation.TypingIndicatorIncomingRepositoryImpl
import com.wire.kalium.logic.data.e2ei.E2EIRepository
import com.wire.kalium.logic.data.event.EventRepository
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.id.SelfTeamIdProvider
import com.wire.kalium.logic.data.message.PendingSubconversationProposals
import com.wire.kalium.logic.data.message.SystemMessageInserterImpl
import com.wire.kalium.logic.data.mlspublickeys.MLSPublicKeysRepository
import com.wire.kalium.logic.data.properties.UserPropertiesSyncDataSource
import com.wire.kalium.logic.data.sync.IncrementalSyncRepository
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.data.sync.SlowSyncRepositoryImpl
import com.wire.kalium.logic.data.team.TeamRepository
import com.wire.kalium.logic.data.user.AccountRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.TimestampKeyRepositoryImpl
import com.wire.kalium.logic.feature.analytics.AnalyticsIdentifierManager
import com.wire.kalium.logic.feature.call.CallBackgroundManager
import com.wire.kalium.logic.feature.call.CallBackgroundManagerImpl
import com.wire.kalium.logic.feature.call.CallManager
import com.wire.kalium.logic.feature.client.IsAllowedToRegisterMLSClientUseCase
import com.wire.kalium.logic.feature.client.LegacyClientBinding
import com.wire.kalium.logic.feature.client.MLSClientManager
import com.wire.kalium.logic.feature.client.MLSClientManagerImpl
import com.wire.kalium.logic.feature.client.RegisterMLSClientUseCaseImpl
import com.wire.kalium.logic.feature.connection.SyncConnectionsUseCase
import com.wire.kalium.logic.feature.conversation.LegacyConversationBinding
import com.wire.kalium.logic.feature.conversation.SyncConversationsUseCase
import com.wire.kalium.logic.feature.conversation.SyncConversationsUseCaseImpl
import com.wire.kalium.logic.feature.conversation.TypingIndicatorSyncManager
import com.wire.kalium.logic.feature.conversation.keyingmaterials.KeyingMaterialsManager
import com.wire.kalium.logic.feature.conversation.keyingmaterials.KeyingMaterialsManagerImpl
import com.wire.kalium.logic.feature.conversation.keyingmaterials.UpdateKeyingMaterialsUseCase
import com.wire.kalium.logic.feature.conversation.mls.OneOnOneResolver
import com.wire.kalium.logic.feature.debug.OptimizeDatabaseUseCaseImpl
import com.wire.kalium.logic.feature.e2ei.ACMECertificatesSyncUseCase
import com.wire.kalium.logic.feature.e2ei.ACMECertificatesSyncUseCaseImpl
import com.wire.kalium.logic.feature.featureConfig.SyncFeatureConfigsUseCase
import com.wire.kalium.logic.feature.keypackage.KeyPackageManager
import com.wire.kalium.logic.feature.keypackage.KeyPackageManagerImpl
import com.wire.kalium.logic.feature.keypackage.MLSKeyPackageCountUseCase
import com.wire.kalium.logic.feature.keypackage.RefillKeyPackagesUseCase
import com.wire.kalium.logic.feature.legalhold.FetchLegalHoldForSelfUserFromRemoteUseCase
import com.wire.kalium.logic.feature.meeting.SyncMeetingsUseCase
import com.wire.kalium.logic.feature.message.LegacyMessageBinding
import com.wire.kalium.logic.feature.message.MessageSendingScheduler
import com.wire.kalium.logic.feature.message.PendingProposalScheduler
import com.wire.kalium.logic.feature.message.PendingProposalSchedulerImpl
import com.wire.kalium.logic.feature.mlsmigration.MLSMigrationManager
import com.wire.kalium.logic.feature.mlsmigration.MLSMigrationManagerImpl
import com.wire.kalium.logic.feature.mlsmigration.MLSMigrationWorkerImpl
import com.wire.kalium.logic.feature.proteus.ProteusPreKeyRefiller
import com.wire.kalium.logic.feature.session.UpgradeCurrentSessionUseCaseImpl
import com.wire.kalium.logic.feature.team.SyncSelfTeamUseCase
import com.wire.kalium.logic.feature.team.SyncSelfTeamUseCaseImpl
import com.wire.kalium.logic.feature.user.IsE2EIEnabledUseCase
import com.wire.kalium.logic.feature.user.IsMLSEnabledUseCase
import com.wire.kalium.logic.feature.user.LegacyUserBinding
import com.wire.kalium.logic.feature.user.SyncContactsUseCase
import com.wire.kalium.logic.feature.user.SyncSelfUserUseCase
import com.wire.kalium.logic.feature.user.SyncUserPropertiesUseCase
import com.wire.kalium.logic.feature.user.UpdateSelfUserSupportedProtocolsUseCase
import com.wire.kalium.logic.featureFlags.FeatureSupport
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.network.ApiMigrationManager
import com.wire.kalium.logic.network.ApiMigrationV3
import com.wire.kalium.logic.sync.AvsSyncStateReporter
import com.wire.kalium.logic.sync.AvsSyncStateReporterImpl
import com.wire.kalium.logic.sync.ObserveSyncStateUseCase
import com.wire.kalium.logic.sync.ObserveSyncStateUseCaseImpl
import com.wire.kalium.logic.sync.SyncExecutor
import com.wire.kalium.logic.sync.SyncExecutorImpl
import com.wire.kalium.logic.sync.SyncManager
import com.wire.kalium.logic.sync.SyncShutdownCoordinator
import com.wire.kalium.logic.sync.SyncStateObserver
import com.wire.kalium.logic.sync.UserSessionWorkScheduler
import com.wire.kalium.logic.sync.incremental.EventGatherer
import com.wire.kalium.logic.sync.incremental.EventProcessor
import com.wire.kalium.logic.sync.incremental.IncrementalSyncManager
import com.wire.kalium.logic.sync.incremental.IncrementalSyncRecoveryHandlerImpl
import com.wire.kalium.logic.sync.incremental.IncrementalSyncWorker
import com.wire.kalium.logic.sync.local.LocalEventManagerImpl
import com.wire.kalium.logic.sync.local.LocalEventRepository
import com.wire.kalium.logic.sync.periodic.UserConfigSyncWorker
import com.wire.kalium.logic.sync.periodic.UserConfigSyncWorkerImpl
import com.wire.kalium.logic.sync.receiver.asset.AudioNormalizedLoudnessScheduler
import com.wire.kalium.logic.sync.slow.SlowSyncCriteriaProvider
import com.wire.kalium.logic.sync.slow.SlowSyncManager
import com.wire.kalium.logic.sync.slow.SlowSyncRecoveryHandler
import com.wire.kalium.logic.sync.slow.SlowSyncWorker
import com.wire.kalium.logic.sync.slow.SlowSyncWorkerImpl
import com.wire.kalium.logic.sync.slow.SyncNomadMessagesDuringSlowSyncUseCaseImpl
import com.wire.kalium.logic.sync.slow.migration.SyncMigrationStepsProvider
import com.wire.kalium.logic.sync.slow.migration.SyncMigrationStepsProviderImpl
import com.wire.kalium.messaging.sending.MessageSender
import com.wire.kalium.network.NetworkStateObserver
import com.wire.kalium.network.networkContainer.AuthenticatedNetworkContainer
import com.wire.kalium.network.session.SessionManager
import com.wire.kalium.userstorage.di.PlatformUserStorageProperties
import com.wire.kalium.userstorage.di.UserStorage
import com.wire.kalium.util.DelicateKaliumApi
import com.wire.kalium.util.InternalKaliumApi
import dev.zacsweers.metro.Provider
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope

/** Sync and recovery factories preserve deferred work, original defaults and user-owned runtime dependencies. */
@Suppress("TooManyFunctions", "LongParameterList", "LargeClass")
internal interface UserSessionSyncBindings {
    @Provides
    @UserSessionBinding("acmeCertificatesSyncUseCase")
    @SingleIn(UserLifetime::class)
    fun userSessionProvideAcmeCertificatesSyncUseCase(
        @UserSessionBinding("userScopedLogger")
        userScopedLogger: Provider<KaliumLogger>,
        @UserSessionBinding("e2eiRepository")
        e2eiRepository: Provider<E2EIRepository>,
        @UserSessionBinding("isE2EIEnabled")
        isE2EIEnabled: Provider<IsE2EIEnabledUseCase>,
    ): ACMECertificatesSyncUseCase =
        ACMECertificatesSyncUseCaseImpl(
            e2eiRepository = e2eiRepository(),
            kaliumLogger = userScopedLogger(),
            isE2EIEnabledUseCase = isE2EIEnabled()
        )

    @Provides
    @UserSessionBinding("analyticsIdentifierManager")
    fun userSessionProvideAnalyticsIdentifierManager(
        @LegacyMessageBinding("messageSender")
        messageMessageSender: Provider<MessageSender>,
        @UserSessionBinding("selfConversationIdProvider")
        selfConversationIdProvider: Provider<SelfConversationIdProvider>,
        @UserSessionBinding("userConfigRepository")
        userConfigRepository: Provider<UserConfigRepository>,
        @UserSessionBinding("clientIdProvider")
        clientIdProvider: Provider<CurrentClientIdProvider>,
        @UserSessionBinding("userScopedLogger")
        userScopedLogger: Provider<KaliumLogger>,
        @UserSessionBinding("syncManager")
        syncManager: Provider<SyncManager>,
        @UserSessionBinding("userId")
        userId: Provider<UserId>,
    ): AnalyticsIdentifierManager =
        AnalyticsIdentifierManager(
            messageMessageSender(),
            userConfigRepository(),
            userId(),
            clientIdProvider(),
            selfConversationIdProvider(),
            syncManager(),
            userScopedLogger()
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
    @UserSessionBinding("apiMigrationsApiMigrationV3")
    fun userSessionProvideApiMigrationsApiMigrationV3(
        @UserSessionBinding("upgradeCurrentSessionUseCase")
        upgradeCurrentSessionUseCase: Provider<UpgradeCurrentSessionUseCaseImpl>,
        @UserSessionBinding("clientIdProvider")
        clientIdProvider: Provider<CurrentClientIdProvider>,
    ): ApiMigrationV3 =
        ApiMigrationV3(clientIdProvider(), upgradeCurrentSessionUseCase())

    @Provides
    @UserSessionBinding("audioNormalizedLoudnessScheduler")
    fun userSessionProvideAudioNormalizedLoudnessScheduler(
        @UserSessionBinding("userSessionWorkScheduler")
        userSessionWorkScheduler: Provider<UserSessionWorkScheduler>,
    ): AudioNormalizedLoudnessScheduler =
        userSessionWorkScheduler()

    @Provides
    @UserSessionBinding("avsSyncStateReporter")
    @SingleIn(UserLifetime::class)
    fun userSessionProvideAvsSyncStateReporter(
        @UserSessionBinding("incrementalSyncRepository")
        incrementalSyncRepository: Provider<IncrementalSyncRepository>,
        @UserSessionBinding("userScopedLogger")
        userScopedLogger: Provider<KaliumLogger>,
        @UserSessionBinding("callManager")
        callManager: Provider<UserSessionValue<Lazy<CallManager>>>,
    ): AvsSyncStateReporter =
        AvsSyncStateReporterImpl(
            callManager = callManager().value,
            incrementalSyncRepository = incrementalSyncRepository(),
            kaliumLogger = userScopedLogger()
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
    @UserSessionBinding("messageSendingScheduler")
    fun userSessionProvideMessageSendingScheduler(
        @UserSessionBinding("userSessionWorkScheduler")
        userSessionWorkScheduler: Provider<UserSessionWorkScheduler>,
    ): MessageSendingScheduler =
        userSessionWorkScheduler()

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
    @UserSessionBinding("observeSyncState")
    fun userSessionProvideObserveSyncState(
        @UserSessionBinding("syncManager")
        syncManager: Provider<SyncManager>,
    ): ObserveSyncStateUseCase =
        ObserveSyncStateUseCaseImpl(syncManager())

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
    @UserSessionBinding("slowSyncRepository")
    @SingleIn(UserLifetime::class)
    fun userSessionProvideSlowSyncRepository(
        @UserSessionBinding("userScopedLogger")
        userScopedLogger: Provider<KaliumLogger>,
        @UserSessionBinding("userStorage")
        userStorage: Provider<UserStorage>,
    ): SlowSyncRepository =
        SlowSyncRepositoryImpl(userStorage().database.metadataDAO, userScopedLogger())

    @Provides
    @UserSessionBinding("slowSyncWorker")
    @SingleIn(UserLifetime::class)
    fun userSessionProvideSlowSyncWorker(
        @UserSessionBinding("slowSyncWorkerSyncNomadMessagesDuringSlowSyncUseCaseImpl")
        slowSyncWorkerSyncNomadMessagesDuringSlowSyncUseCaseImpl: Provider<SyncNomadMessagesDuringSlowSyncUseCaseImpl>,
        @UserSessionBinding("fetchLegalHoldForSelfUserFromRemoteUseCase")
        fetchLegalHoldForSelfUserFromRemoteUseCase: Provider<FetchLegalHoldForSelfUserFromRemoteUseCase>,
        @UserSessionBinding("isClientAsyncNotificationsCapableProvider")
        isClientAsyncNotificationsCapableProvider: Provider<IsClientAsyncNotificationsCapableProvider>,
        @UserSessionBinding("slowSyncWorkerOptimizeDatabaseUseCaseImpl")
        slowSyncWorkerOptimizeDatabaseUseCaseImpl: Provider<OptimizeDatabaseUseCaseImpl>,
        @UserSessionBinding("joinExistingMLSConversations")
        joinExistingMLSConversations: Provider<JoinExistingMLSConversationsUseCase>,
        @UserSessionBinding("cryptoTransactionProvider")
        cryptoTransactionProvider: Provider<CryptoTransactionProvider>,
        @UserSessionBinding("syncFeatureConfigsUseCase")
        syncFeatureConfigsUseCase: Provider<SyncFeatureConfigsUseCase>,
        @UserSessionBinding("updateSupportedProtocols")
        updateSupportedProtocols: Provider<UpdateSelfUserSupportedProtocolsUseCase>,
        @UserSessionBinding("syncMeetingsUseCase")
        syncMeetingsUseCase: Provider<SyncMeetingsUseCase>,
        @UserSessionBinding("syncSelfTeamUseCase")
        syncSelfTeamUseCase: Provider<SyncSelfTeamUseCase>,
        @UserSessionBinding("syncUserProperties")
        syncUserProperties: Provider<SyncUserPropertiesUseCase>,
        @UserSessionBinding("syncConversations")
        syncConversations: Provider<SyncConversationsUseCase>,
        @UserSessionBinding("oneOnOneResolver")
        oneOnOneResolver: Provider<OneOnOneResolver>,
        @UserSessionBinding("eventRepository")
        eventRepository: Provider<EventRepository>,
        @UserSessionBinding("syncConnections")
        syncConnections: Provider<SyncConnectionsUseCase>,
        @UserSessionBinding("syncContacts")
        syncContacts: Provider<SyncContactsUseCase>,
        @UserSessionBinding("syncSelfUser")
        syncSelfUser: Provider<SyncSelfUserUseCase>,
    ): SlowSyncWorker =
        SlowSyncWorkerImpl(
            isClientAsyncNotificationsCapableProvider(),
            eventRepository(),
            syncSelfUser(),
            syncUserProperties(),
            syncFeatureConfigsUseCase(),
            updateSupportedProtocols(),
            syncConversations(),
            syncConnections(),
            syncSelfTeamUseCase(),
            syncContacts(),
            joinExistingMLSConversations(),
            fetchLegalHoldForSelfUserFromRemoteUseCase(),
            oneOnOneResolver(),
            cryptoTransactionProvider(),
            syncNomadMessagesDuringSlowSync = slowSyncWorkerSyncNomadMessagesDuringSlowSyncUseCaseImpl(),
            syncMeetings = syncMeetingsUseCase(),
            optimizer = slowSyncWorkerOptimizeDatabaseUseCaseImpl()
        )

    @Provides
    @UserSessionBinding("syncConversations")
    fun userSessionProvideSyncConversations(
        @UserSessionBinding("cryptoTransactionProvider")
        cryptoTransactionProvider: Provider<CryptoTransactionProvider>,
        @UserSessionBinding("fetchConversationsUseCase")
        fetchConversationsUseCase: Provider<FetchConversationsUseCase>,
        @UserSessionBinding("conversationRepository")
        conversationRepository: Provider<ConversationRepository>,
        @UserSessionBinding("systemMessageInserter")
        systemMessageInserter: Provider<SystemMessageInserterImpl>,
    ): SyncConversationsUseCase =
        SyncConversationsUseCaseImpl(
            conversationRepository(),
            systemMessageInserter(),
            fetchConversationsUseCase(),
            cryptoTransactionProvider()
        )

    @Provides
    @UserSessionBinding("syncExecutor")
    fun userSessionProvideSyncExecutor(
        @UserSessionBinding("syncExecutorImpl")
        syncExecutorImpl: Provider<SyncExecutorImpl>,
    ): SyncExecutor =
        syncExecutorImpl()

    @Provides
    @UserSessionBinding("syncExecutorImpl")
    @SingleIn(UserLifetime::class)
    fun userSessionProvideSyncExecutorImpl(
        @UserSessionBinding("syncShutdownCoordinator")
        syncShutdownCoordinator: Provider<SyncShutdownCoordinator>,
        @UserSessionBinding("incrementalSyncManager")
        incrementalSyncManager: Provider<IncrementalSyncManager>,
        @UserSessionBinding("sessionCoroutineScope")
        sessionCoroutineScope: Provider<CoroutineScope>,
        @UserSessionBinding("syncStateObserver")
        syncStateObserver: Provider<UserSessionValue<Lazy<SyncStateObserver>>>,
        @UserSessionBinding("userScopedLogger")
        userScopedLogger: Provider<KaliumLogger>,
        @UserSessionBinding("slowSyncManager")
        slowSyncManager: Provider<SlowSyncManager>,
    ): SyncExecutorImpl =
        SyncExecutorImpl(
            syncStateObserver().value.value,
            slowSyncManager(),
            incrementalSyncManager(),
            sessionCoroutineScope(),
            userScopedLogger = userScopedLogger(),
            shutdownCoordinator = syncShutdownCoordinator()
        )

    @Provides
    @UserSessionBinding("syncManager")
    fun userSessionProvideSyncManager(
        @UserSessionBinding("syncStateObserver")
        syncStateObserver: Provider<UserSessionValue<Lazy<SyncStateObserver>>>,
    ): SyncManager =
        syncStateObserver().value.value

    @Provides
    @UserSessionBinding("syncMigrationStepsProviderSyncMigrationStepsProviderImpl")
    fun userSessionProvideSyncMigrationStepsProviderSyncMigrationStepsProviderImpl(
        @UserSessionBinding("platformUserStorageProperties")
        platformUserStorageProperties: Provider<PlatformUserStorageProperties>,
        @UserSessionBinding("accountRepository")
        accountRepository: Provider<AccountRepository>,
        @UserSessionBinding("kaliumConfigs")
        kaliumConfigs: Provider<KaliumConfigs>,
        @UserSessionBinding("userStorage")
        userStorage: Provider<UserStorage>,
        @UserSessionBinding("selfTeamId")
        selfTeamId: Provider<SelfTeamIdProvider>,
        @UserSessionBinding("userId")
        userId: Provider<UserId>,
    ): SyncMigrationStepsProviderImpl =
        SyncMigrationStepsProviderImpl(
            accountRepository = lazy { accountRepository() },
            selfTeamIdProvider = selfTeamId(),
            oldUserConfigStorage = lazy {
                UserConfigStorageFactory().create(userId(), kaliumConfigs().shouldEncryptData(), platformUserStorageProperties())
            },
            newUserConfigStorage = lazy { userStorage().database.userPrefsDAO }
        )

    @Provides
    @UserSessionBinding("syncSelfTeamUseCase")
    fun userSessionProvideSyncSelfTeamUseCase(
        @UserSessionBinding("teamRepository")
        teamRepository: Provider<TeamRepository>,
        @UserSessionBinding("userRepository")
        userRepository: Provider<UserRepository>,
        @UserSessionBinding("kaliumConfigs")
        kaliumConfigs: Provider<KaliumConfigs>,
    ): SyncSelfTeamUseCase =
        SyncSelfTeamUseCaseImpl(
            userRepository = userRepository(),
            teamRepository = teamRepository(),
            fetchedUsersLimit = kaliumConfigs().limitTeamMembersFetchDuringSlowSync
        )

    @Provides
    @UserSessionBinding("typingIndicatorSyncManager")
    @SingleIn(UserLifetime::class)
    fun userSessionProvideTypingIndicatorSyncManager(
        @LegacyConversationBinding("typingIndicatorIncomingRepository")
        conversationTypingIndicatorIncomingRepository: Provider<TypingIndicatorIncomingRepositoryImpl>,
        @UserSessionBinding("observeSyncState")
        observeSyncState: Provider<ObserveSyncStateUseCase>,
        @UserSessionBinding("userScopedLogger")
        userScopedLogger: Provider<KaliumLogger>,
    ): TypingIndicatorSyncManager =
        TypingIndicatorSyncManager(
            typingIndicatorIncomingRepository = lazy { conversationTypingIndicatorIncomingRepository() },
            observeSyncStateUseCase = observeSyncState(),
            kaliumLogger = userScopedLogger(),
        )

    @Provides
    @UserSessionBinding("userConfigSyncWorker")
    @SingleIn(UserLifetime::class)
    fun userSessionProvideUserConfigSyncWorker(
        @UserSessionBinding("acmeCertificatesSyncUseCase")
        acmeCertificatesSyncUseCase: Provider<ACMECertificatesSyncUseCase>,
        @UserSessionBinding("incrementalSyncRepository")
        incrementalSyncRepository: Provider<IncrementalSyncRepository>,
        @UserSessionBinding("syncFeatureConfigsUseCase")
        syncFeatureConfigsUseCase: Provider<SyncFeatureConfigsUseCase>,
        @UserSessionBinding("mlsPublicKeysRepository")
        mlsPublicKeysRepository: Provider<MLSPublicKeysRepository>,
        @UserSessionBinding("proteusPreKeyRefiller")
        proteusPreKeyRefiller: Provider<ProteusPreKeyRefiller>,
        @UserSessionBinding("userScopedLogger")
        userScopedLogger: Provider<KaliumLogger>,
    ): UserConfigSyncWorker =
        UserConfigSyncWorkerImpl(
            incrementalSyncRepository = incrementalSyncRepository(),
            syncFeatureConfigsUseCase = syncFeatureConfigsUseCase(),
            proteusPreKeyRefiller = proteusPreKeyRefiller(),
            mlsPublicKeysRepository = mlsPublicKeysRepository(),
            acmeCertificatesSyncUseCase = acmeCertificatesSyncUseCase(),
            kaliumLogger = userScopedLogger(),
        )

    @Provides
    @UserSessionBinding("userPropertyRepositoryUserPropertiesSyncDataSource")
    fun userSessionProvideUserPropertyRepositoryUserPropertiesSyncDataSource(
        @UserSessionBinding("authenticatedNetworkContainer")
        authenticatedNetworkContainer: Provider<AuthenticatedNetworkContainer>,
        @UserSessionBinding("userConfigRepository")
        userConfigRepository: Provider<UserConfigRepository>,
    ): UserPropertiesSyncDataSource =
        UserPropertiesSyncDataSource(authenticatedNetworkContainer().propertiesApi, userConfigRepository())
}

private const val API_MIGRATION_V3_VERSION = 3
