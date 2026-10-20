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

import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.asset.AssetAuditFeatureHandler
import com.wire.kalium.logic.data.asset.AssetDataSource
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.asset.KaliumFileSystem
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.client.CryptoTransactionProvider
import com.wire.kalium.logic.data.client.CryptoTransactionProviderImpl
import com.wire.kalium.logic.data.client.IsClientAsyncNotificationsCapableProvider
import com.wire.kalium.logic.data.client.MLSClientProvider
import com.wire.kalium.logic.data.client.ProteusClientProvider
import com.wire.kalium.logic.data.client.remote.ClientRemoteRepository
import com.wire.kalium.logic.data.connection.ConnectionRepository
import com.wire.kalium.logic.data.conversation.ConversationGroupRepository
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.FetchConversationUseCase
import com.wire.kalium.logic.data.conversation.FetchConversationsUseCase
import com.wire.kalium.logic.data.conversation.FetchMLSOneToOneConversationUseCase
import com.wire.kalium.logic.data.conversation.JoinExistingMLSConversationUseCase
import com.wire.kalium.logic.data.conversation.JoinExistingMLSConversationUseCaseImpl
import com.wire.kalium.logic.data.conversation.JoinExistingMLSConversationsUseCase
import com.wire.kalium.logic.data.conversation.JoinExistingMLSConversationsUseCaseImpl
import com.wire.kalium.logic.data.conversation.JoinSubconversationUseCase
import com.wire.kalium.logic.data.conversation.JoinSubconversationUseCaseImpl
import com.wire.kalium.logic.data.conversation.LeaveSubconversationUseCase
import com.wire.kalium.logic.data.conversation.LeaveSubconversationUseCaseImpl
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.conversation.PersistConversationsUseCase
import com.wire.kalium.logic.data.conversation.ResetMLSConversationUseCase
import com.wire.kalium.logic.data.conversation.SubconversationRepositoryImpl
import com.wire.kalium.logic.data.conversation.UpdateConversationProtocolUseCase
import com.wire.kalium.logic.data.conversation.UpdateConversationProtocolUseCaseImpl
import com.wire.kalium.logic.data.conversation.mls.PendingActionsRepository
import com.wire.kalium.logic.data.conversation.mls.PersistentPendingActionsRepository
import com.wire.kalium.logic.data.event.EventRepository
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.keypackage.KeyPackageLimitsProvider
import com.wire.kalium.logic.data.keypackage.KeyPackageRepository
import com.wire.kalium.logic.data.logout.LogoutRepository
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.message.PendingSubconversationProposals
import com.wire.kalium.logic.data.message.SystemMessageInserterImpl
import com.wire.kalium.logic.data.properties.UserPropertyRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncRepository
import com.wire.kalium.logic.data.team.TeamRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.client.RegisterMLSClientUseCase
import com.wire.kalium.logic.feature.client.RegisterMLSClientUseCaseImpl
import com.wire.kalium.logic.feature.connection.SyncConnectionsUseCase
import com.wire.kalium.logic.feature.connection.SyncConnectionsUseCaseImpl
import com.wire.kalium.logic.feature.conversation.RecoverMLSConversationsForUserUseCase
import com.wire.kalium.logic.feature.conversation.RecoverMLSConversationsForUserUseCaseImpl
import com.wire.kalium.logic.feature.conversation.RecoverMLSConversationsUseCase
import com.wire.kalium.logic.feature.conversation.RecoverMLSConversationsUseCaseImpl
import com.wire.kalium.logic.feature.conversation.SyncConversationsUseCase
import com.wire.kalium.logic.feature.conversation.SyncConversationsUseCaseImpl
import com.wire.kalium.logic.feature.conversation.mls.MLSOneOnOneConversationResolver
import com.wire.kalium.logic.feature.conversation.mls.MLSOneOnOneConversationResolverImpl
import com.wire.kalium.logic.feature.conversation.mls.OneOnOneMigrator
import com.wire.kalium.logic.feature.conversation.mls.OneOnOneMigratorImpl
import com.wire.kalium.logic.feature.conversation.mls.OneOnOneResolver
import com.wire.kalium.logic.feature.conversation.mls.OneOnOneResolverImpl
import com.wire.kalium.logic.feature.conversation.mls.RecoverPendingMLSGroupJoinsUseCase
import com.wire.kalium.logic.feature.conversation.mls.RecoverPendingMLSGroupJoinsUseCaseImpl
import com.wire.kalium.logic.feature.conversation.mls.RecoverPendingOneOnOneResolutionsUseCase
import com.wire.kalium.logic.feature.conversation.mls.RecoverPendingOneOnOneResolutionsUseCaseImpl
import com.wire.kalium.logic.feature.debug.OptimizeDatabaseUseCaseImpl
import com.wire.kalium.logic.feature.featureConfig.SyncFeatureConfigsUseCase
import com.wire.kalium.logic.feature.legalhold.FetchLegalHoldForSelfUserFromRemoteUseCase
import com.wire.kalium.logic.feature.meeting.SyncMeetingsUseCase
import com.wire.kalium.logic.feature.protocol.OneOnOneProtocolSelector
import com.wire.kalium.logic.feature.team.SyncSelfTeamUseCase
import com.wire.kalium.logic.feature.team.SyncSelfTeamUseCaseImpl
import com.wire.kalium.logic.feature.user.LegacyUserBinding
import com.wire.kalium.logic.feature.user.SyncContactsUseCase
import com.wire.kalium.logic.feature.user.SyncContactsUseCaseImpl
import com.wire.kalium.logic.feature.user.SyncSelfUserUseCase
import com.wire.kalium.logic.feature.user.SyncSelfUserUseCaseImpl
import com.wire.kalium.logic.feature.user.SyncUserPropertiesUseCase
import com.wire.kalium.logic.feature.user.SyncUserPropertiesUseCaseImpl
import com.wire.kalium.logic.feature.user.UpdateSelfUserSupportedProtocolsUseCase
import com.wire.kalium.logic.feature.user.UpdateSelfUserSupportedProtocolsUseCaseImpl
import com.wire.kalium.logic.feature.user.UpdateSupportedProtocolsAndResolveOneOnOnesUseCase
import com.wire.kalium.logic.feature.user.UpdateSupportedProtocolsAndResolveOneOnOnesUseCaseImpl
import com.wire.kalium.logic.featureFlags.FeatureSupport
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.sync.SyncExecutor
import com.wire.kalium.logic.sync.SyncExecutorImpl
import com.wire.kalium.logic.sync.SyncManager
import com.wire.kalium.logic.sync.SyncShutdownCoordinator
import com.wire.kalium.logic.sync.SyncStateObserver
import com.wire.kalium.logic.sync.SyncStateObserverImpl
import com.wire.kalium.logic.sync.incremental.EventGatherer
import com.wire.kalium.logic.sync.incremental.EventGathererImpl
import com.wire.kalium.logic.sync.incremental.EventProcessor
import com.wire.kalium.logic.sync.incremental.EventProcessorImpl
import com.wire.kalium.logic.sync.incremental.IncrementalSyncManager
import com.wire.kalium.logic.sync.receiver.ConversationEventReceiver
import com.wire.kalium.logic.sync.receiver.FeatureConfigEventReceiver
import com.wire.kalium.logic.sync.receiver.FederationEventReceiver
import com.wire.kalium.logic.sync.receiver.MeetingEventReceiver
import com.wire.kalium.logic.sync.receiver.TeamEventReceiver
import com.wire.kalium.logic.sync.receiver.UserEventReceiver
import com.wire.kalium.logic.sync.receiver.UserPropertiesEventReceiver
import com.wire.kalium.logic.sync.slow.SlowSlowSyncCriteriaProviderImpl
import com.wire.kalium.logic.sync.slow.SlowSyncCriteriaProvider
import com.wire.kalium.logic.sync.slow.SlowSyncManager
import com.wire.kalium.logic.sync.slow.SlowSyncWorker
import com.wire.kalium.logic.sync.slow.SlowSyncWorkerImpl
import com.wire.kalium.logic.sync.slow.SyncNomadMessagesDuringSlowSyncUseCaseImpl
import com.wire.kalium.messaging.hooks.CryptoStateChangeHookNotifier
import com.wire.kalium.network.networkContainer.AuthenticatedNetworkContainer
import com.wire.kalium.userstorage.di.UserStorage
import com.wire.kalium.util.DelicateKaliumApi
import com.wire.kalium.util.InternalKaliumApi
import dev.zacsweers.metro.Provider
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope

@Suppress("TooManyFunctions", "LongParameterList", "LargeClass") // Individual qualified construction edges.
internal interface UserSessionBindings3 {
    @Provides
    @UserSessionBinding("assetRepository")
    fun userSessionProvideAssetRepository(
        @LegacyUserBinding("assetAuditLog")
        userAssetAuditLog: Provider<AssetAuditFeatureHandler>,
        @UserSessionBinding("authenticatedNetworkContainer")
        authenticatedNetworkContainer: Provider<AuthenticatedNetworkContainer>,
        @UserSessionBinding("kaliumFileSystem")
        kaliumFileSystem: Provider<KaliumFileSystem>,
        @UserSessionBinding("userStorage")
        userStorage: Provider<UserStorage>,
        @UserSessionBinding("userId")
        userId: Provider<UserId>,
    ): AssetRepository =
        AssetDataSource(
            assetApi = authenticatedNetworkContainer().assetApi,
            assetDao = userStorage().database.assetDAO,
            selfUserId = userId(),
            assetAuditLog = lazy { userAssetAuditLog() },
            kaliumFileSystem = kaliumFileSystem()
        )

    @Provides
    @UserSessionBinding("eventGatherer")
    fun userSessionProvideEventGatherer(
        @UserSessionBinding("isClientAsyncNotificationsCapableProvider")
        isClientAsyncNotificationsCapableProvider: Provider<IsClientAsyncNotificationsCapableProvider>,
        @UserSessionBinding("userScopedLogger")
        userScopedLogger: Provider<KaliumLogger>,
        @UserSessionBinding("eventRepository")
        eventRepository: Provider<EventRepository>,
    ): EventGatherer =
        EventGathererImpl(
            isClientAsyncNotificationsCapableProvider = isClientAsyncNotificationsCapableProvider(),
            eventRepository = eventRepository(),
            logger = userScopedLogger()
        )

    @Provides
    @UserSessionBinding("cryptoTransactionProvider")
    fun userSessionProvideCryptoTransactionProvider(
        @UserSessionBinding("proteusClientProvider")
        proteusClientProvider: Provider<ProteusClientProvider>,
        @UserSessionBinding("mlsClientProvider")
        mlsClientProvider: Provider<MLSClientProvider>,
    ): CryptoTransactionProvider =
        CryptoTransactionProviderImpl(
            mlsClientProvider = mlsClientProvider(),
            proteusClientProvider = proteusClientProvider(),
        )

    @Provides
    @UserSessionBinding("eventProcessor")
    @SingleIn(UserLifetime::class)
    fun userSessionProvideEventProcessor(
        @UserSessionBinding("userPropertiesEventReceiver")
        userPropertiesEventReceiver: Provider<UserPropertiesEventReceiver>,
        @UserSessionBinding("featureConfigEventReceiver")
        featureConfigEventReceiver: Provider<FeatureConfigEventReceiver>,
        @UserSessionBinding("conversationEventReceiver")
        conversationEventReceiver: Provider<ConversationEventReceiver>,
        @UserSessionBinding("federationEventReceiver")
        federationEventReceiver: Provider<FederationEventReceiver>,
        @UserSessionBinding("sessionCoroutineScope")
        sessionCoroutineScope: Provider<CoroutineScope>,
        @UserSessionBinding("meetingEventReceiver")
        meetingEventReceiver: Provider<MeetingEventReceiver>,
        @UserSessionBinding("teamEventReceiver")
        teamEventReceiver: Provider<TeamEventReceiver>,
        @UserSessionBinding("userEventReceiver")
        userEventReceiver: Provider<UserEventReceiver>,
        @UserSessionBinding("userScopedLogger")
        userScopedLogger: Provider<KaliumLogger>,
    ): EventProcessor =
        EventProcessorImpl(
            conversationEventReceiver = conversationEventReceiver(),
            userEventReceiver = userEventReceiver(),
            teamEventReceiver = teamEventReceiver(),
            featureConfigEventReceiver = featureConfigEventReceiver(),
            userPropertiesEventReceiver = userPropertiesEventReceiver(),
            federationEventReceiver = federationEventReceiver(),
            meetingEventReceiver = meetingEventReceiver(),
            processingScope = sessionCoroutineScope(),
            logger = userScopedLogger(),
        )

    @Provides
    @UserSessionBinding("slowSyncCriteriaProvider")
    fun userSessionProvideSlowSyncCriteriaProvider(
        @UserSessionBinding("clientRepository")
        clientRepository: Provider<ClientRepository>,
        @UserSessionBinding("logoutRepository")
        logoutRepository: Provider<LogoutRepository>,
    ): SlowSyncCriteriaProvider =
        SlowSlowSyncCriteriaProviderImpl(clientRepository(), logoutRepository())

    @Provides
    @UserSessionBinding("syncManager")
    fun userSessionProvideSyncManager(
        @UserSessionBinding("syncStateObserver")
        syncStateObserver: Provider<UserSessionValue<Lazy<SyncStateObserver>>>,
    ): SyncManager =
        syncStateObserver().value.value

    @Provides
    @UserSessionBinding("syncStateObserver")
    @SingleIn(UserLifetime::class)
    fun userSessionProvideSyncStateObserver(
        @UserSessionBinding("syncStateObserverSyncStateObserverImpl")
        syncStateObserverSyncStateObserverImpl: Provider<SyncStateObserverImpl>,
    ): UserSessionValue<Lazy<SyncStateObserver>> =
        UserSessionValue(
            lazy {
                syncStateObserverSyncStateObserverImpl()
            }
        )

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
    @UserSessionBinding("syncExecutor")
    fun userSessionProvideSyncExecutor(
        @UserSessionBinding("syncExecutorImpl")
        syncExecutorImpl: Provider<SyncExecutorImpl>,
    ): SyncExecutor =
        syncExecutorImpl()

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
    @UserSessionBinding("updateConversationProtocolUseCase")
    fun userSessionProvideUpdateConversationProtocolUseCase(
        @UserSessionBinding("persistConversationsUseCase")
        persistConversationsUseCase: Provider<PersistConversationsUseCase>,
        @UserSessionBinding("conversationRepository")
        conversationRepository: Provider<ConversationRepository>,
    ): UpdateConversationProtocolUseCase =
        UpdateConversationProtocolUseCaseImpl(
            conversationRepository(),
            persistConversationsUseCase()
        )

    @Provides
    @UserSessionBinding("syncConnections")
    fun userSessionProvideSyncConnections(
        @UserSessionBinding("cryptoTransactionProvider")
        cryptoTransactionProvider: Provider<CryptoTransactionProvider>,
        @UserSessionBinding("connectionRepository")
        connectionRepository: Provider<ConnectionRepository>,
    ): SyncConnectionsUseCase =
        SyncConnectionsUseCaseImpl(
            connectionRepository = connectionRepository(),
            transactionProvider = cryptoTransactionProvider()
        )

    @Provides
    @UserSessionBinding("syncSelfUser")
    fun userSessionProvideSyncSelfUser(
        @UserSessionBinding("userRepository")
        userRepository: Provider<UserRepository>,
    ): SyncSelfUserUseCase =
        SyncSelfUserUseCaseImpl(userRepository())

    @Provides
    @UserSessionBinding("syncUserProperties")
    fun userSessionProvideSyncUserProperties(
        @UserSessionBinding("userPropertyRepository")
        userPropertyRepository: Provider<UserPropertyRepository>,
    ): SyncUserPropertiesUseCase =
        SyncUserPropertiesUseCaseImpl(userPropertyRepository())

    @Provides
    @UserSessionBinding("syncContacts")
    fun userSessionProvideSyncContacts(
        @UserSessionBinding("userRepository")
        userRepository: Provider<UserRepository>,
    ): SyncContactsUseCase =
        SyncContactsUseCaseImpl(userRepository())

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
    @UserSessionBinding("joinExistingMLSConversationUseCase")
    fun userSessionProvideJoinExistingMLSConversationUseCase(
        @UserSessionBinding("fetchMLSOneToOneConversationUseCase")
        fetchMLSOneToOneConversationUseCase: Provider<FetchMLSOneToOneConversationUseCase>,
        @UserSessionBinding("authenticatedNetworkContainer")
        authenticatedNetworkContainer: Provider<AuthenticatedNetworkContainer>,
        @UserSessionBinding("mlsConversationRepository")
        mlsConversationRepository: Provider<MLSConversationRepository>,
        @UserSessionBinding("fetchConversationUseCase")
        fetchConversationUseCase: Provider<FetchConversationUseCase>,
        @UserSessionBinding("conversationRepository")
        conversationRepository: Provider<ConversationRepository>,
        @UserSessionBinding("resetMlsConversation")
        resetMlsConversation: Provider<ResetMLSConversationUseCase>,
        @UserSessionBinding("clientRepository")
        clientRepository: Provider<ClientRepository>,
        @UserSessionBinding("featureSupport")
        featureSupport: Provider<FeatureSupport>,
        @UserSessionBinding("userId")
        userId: Provider<UserId>,
    ): JoinExistingMLSConversationUseCase =
        JoinExistingMLSConversationUseCaseImpl(
            featureSupport(),
            authenticatedNetworkContainer().conversationApi,
            clientRepository(),
            conversationRepository(),
            mlsConversationRepository(),
            fetchMLSOneToOneConversationUseCase(),
            fetchConversationUseCase(),
            resetMlsConversation(),
            userId(),
        )

    @Provides
    @UserSessionBinding("registerMLSClientUseCase")
    fun userSessionProvideRegisterMLSClientUseCase(
        @UserSessionBinding("currentCryptoStateChangeHookNotifier")
        currentCryptoStateChangeHookNotifier: Provider<CryptoStateChangeHookNotifier>,
        @UserSessionBinding("keyPackageLimitsProvider")
        keyPackageLimitsProvider: Provider<KeyPackageLimitsProvider>,
        @UserSessionBinding("keyPackageRepository")
        keyPackageRepository: Provider<KeyPackageRepository>,
        @UserSessionBinding("userConfigRepository")
        userConfigRepository: Provider<UserConfigRepository>,
        @UserSessionBinding("mlsClientProvider")
        mlsClientProvider: Provider<MLSClientProvider>,
        @UserSessionBinding("clientRepository")
        clientRepository: Provider<ClientRepository>,
        @UserSessionBinding("userId")
        userId: Provider<UserId>,
    ): RegisterMLSClientUseCase =
        RegisterMLSClientUseCaseImpl(
            mlsClientProvider(),
            clientRepository(),
            keyPackageRepository(),
            keyPackageLimitsProvider(),
            userConfigRepository(),
            userId(),
            currentCryptoStateChangeHookNotifier()
        )

    @Provides
    @UserSessionBinding("recoverMLSConversationsUseCase")
    fun userSessionProvideRecoverMLSConversationsUseCase(
        @UserSessionBinding("joinExistingMLSConversationUseCase")
        joinExistingMLSConversationUseCase: Provider<JoinExistingMLSConversationUseCase>,
        @UserSessionBinding("mlsConversationRepository")
        mlsConversationRepository: Provider<MLSConversationRepository>,
        @UserSessionBinding("conversationRepository")
        conversationRepository: Provider<ConversationRepository>,
        @UserSessionBinding("clientRepository")
        clientRepository: Provider<ClientRepository>,
        @UserSessionBinding("featureSupport")
        featureSupport: Provider<FeatureSupport>,
        @UserSessionBinding("userId")
        userId: Provider<UserId>,
    ): RecoverMLSConversationsUseCase =
        RecoverMLSConversationsUseCaseImpl(
            featureSupport(),
            clientRepository(),
            conversationRepository(),
            mlsConversationRepository(),
            joinExistingMLSConversationUseCase(),
            userId(),
        )

    @Provides
    @UserSessionBinding("joinExistingMLSConversations")
    fun userSessionProvideJoinExistingMLSConversations(
        @UserSessionBinding("joinExistingMLSConversationUseCase")
        joinExistingMLSConversationUseCase: Provider<JoinExistingMLSConversationUseCase>,
        @UserSessionBinding("cryptoTransactionProvider")
        cryptoTransactionProvider: Provider<CryptoTransactionProvider>,
        @UserSessionBinding("pendingActionsRepository")
        pendingActionsRepository: Provider<PendingActionsRepository>,
        @UserSessionBinding("conversationRepository")
        conversationRepository: Provider<ConversationRepository>,
        @UserSessionBinding("clientRepository")
        clientRepository: Provider<ClientRepository>,
        @UserSessionBinding("featureSupport")
        featureSupport: Provider<FeatureSupport>,
        @UserSessionBinding("userId")
        userId: Provider<UserId>,
    ): JoinExistingMLSConversationsUseCase =
        JoinExistingMLSConversationsUseCaseImpl(
            featureSupport(),
            clientRepository(),
            conversationRepository(),
            joinExistingMLSConversationUseCase(),
            cryptoTransactionProvider(),
            pendingActionsRepository(),
            userId(),
        )

    @Provides
    @UserSessionBinding("recoverMLSConversationsForUser")
    fun userSessionProvideRecoverMLSConversationsForUser(
        @UserSessionBinding("recoverMLSConversationsUseCase")
        recoverMLSConversationsUseCase: Provider<RecoverMLSConversationsUseCase>,
        @UserSessionBinding("joinExistingMLSConversations")
        joinExistingMLSConversations: Provider<JoinExistingMLSConversationsUseCase>,
        @UserSessionBinding("cryptoTransactionProvider")
        cryptoTransactionProvider: Provider<CryptoTransactionProvider>,
    ): RecoverMLSConversationsForUserUseCase =
        RecoverMLSConversationsForUserUseCaseImpl(
            recoverEstablishedMLSConversations = recoverMLSConversationsUseCase(),
            joinPendingMLSConversations = joinExistingMLSConversations(),
            transactionProvider = cryptoTransactionProvider(),
        )

    @Provides
    @UserSessionBinding("joinSubconversationUseCase")
    fun userSessionProvideJoinSubconversationUseCase(
        @UserSessionBinding("authenticatedNetworkContainer")
        authenticatedNetworkContainer: Provider<AuthenticatedNetworkContainer>,
        @UserSessionBinding("cryptoTransactionProvider")
        cryptoTransactionProvider: Provider<CryptoTransactionProvider>,
        @UserSessionBinding("mlsConversationRepository")
        mlsConversationRepository: Provider<MLSConversationRepository>,
        @UserSessionBinding("subconversationRepository")
        subconversationRepository: Provider<SubconversationRepositoryImpl>,
    ): JoinSubconversationUseCase =
        JoinSubconversationUseCaseImpl(
            authenticatedNetworkContainer().conversationApi,
            mlsConversationRepository(),
            subconversationRepository(),
            cryptoTransactionProvider()
        )

    @Provides
    @UserSessionBinding("leaveSubconversationUseCase")
    fun userSessionProvideLeaveSubconversationUseCase(
        @UserSessionBinding("pendingSubconversationProposals")
        pendingSubconversationProposals: Provider<PendingSubconversationProposals>,
        @UserSessionBinding("authenticatedNetworkContainer")
        authenticatedNetworkContainer: Provider<AuthenticatedNetworkContainer>,
        @UserSessionBinding("subconversationRepository")
        subconversationRepository: Provider<SubconversationRepositoryImpl>,
        @UserSessionBinding("clientIdProvider")
        clientIdProvider: Provider<CurrentClientIdProvider>,
        @UserSessionBinding("userId")
        userId: Provider<UserId>,
    ): LeaveSubconversationUseCase =
        LeaveSubconversationUseCaseImpl(
            authenticatedNetworkContainer().conversationApi,
            subconversationRepository(),
            userId(),
            clientIdProvider(),
            pendingSubconversationProposals(),
        )

    @Provides
    @UserSessionBinding("mlsOneOnOneConversationResolver")
    fun userSessionProvideMlsOneOnOneConversationResolver(
        @UserSessionBinding("fetchMLSOneToOneConversationUseCase")
        fetchMLSOneToOneConversationUseCase: Provider<FetchMLSOneToOneConversationUseCase>,
        @UserSessionBinding("joinExistingMLSConversationUseCase")
        joinExistingMLSConversationUseCase: Provider<JoinExistingMLSConversationUseCase>,
        @UserSessionBinding("mlsConversationRepository")
        mlsConversationRepository: Provider<MLSConversationRepository>,
        @UserSessionBinding("conversationRepository")
        conversationRepository: Provider<ConversationRepository>,
    ): MLSOneOnOneConversationResolver =
        MLSOneOnOneConversationResolverImpl(
            conversationRepository(),
            joinExistingMLSConversationUseCase(),
            fetchMLSOneToOneConversationUseCase(),
            mlsConversationRepository(),
        )

    @Provides
    @UserSessionBinding("oneOnOneMigrator")
    fun userSessionProvideOneOnOneMigrator(
        @UserSessionBinding("mlsOneOnOneConversationResolver")
        mlsOneOnOneConversationResolver: Provider<MLSOneOnOneConversationResolver>,
        @UserSessionBinding("conversationGroupRepository")
        conversationGroupRepository: Provider<ConversationGroupRepository>,
        @UserSessionBinding("clientRemoteRepository")
        clientRemoteRepository: Provider<ClientRemoteRepository>,
        @UserSessionBinding("conversationRepository")
        conversationRepository: Provider<ConversationRepository>,
        @UserSessionBinding("systemMessageInserter")
        systemMessageInserter: Provider<SystemMessageInserterImpl>,
        @UserSessionBinding("messageRepository")
        messageRepository: Provider<MessageRepository>,
        @UserSessionBinding("userRepository")
        userRepository: Provider<UserRepository>,
    ): OneOnOneMigrator =
        OneOnOneMigratorImpl(
            mlsOneOnOneConversationResolver(),
            conversationGroupRepository(),
            conversationRepository(),
            messageRepository(),
            userRepository(),
            systemMessageInserter(),
            clientRemoteRepository(),
        )

    @Provides
    @UserSessionBinding("pendingActionsRepository")
    @SingleIn(UserLifetime::class)
    fun userSessionProvidePendingActionsRepository(
        @UserSessionBinding("userStorage")
        userStorage: Provider<UserStorage>,
    ): PendingActionsRepository =
        PersistentPendingActionsRepository(
            pendingActionDAO = userStorage().database.pendingActionDAO
        )

    @Provides
    @UserSessionBinding("oneOnOneResolver")
    fun userSessionProvideOneOnOneResolver(
        @UserSessionBinding("incrementalSyncRepository")
        incrementalSyncRepository: Provider<IncrementalSyncRepository>,
        @UserSessionBinding("mlsConversationRepository")
        mlsConversationRepository: Provider<MLSConversationRepository>,
        @UserSessionBinding("oneOnOneProtocolSelector")
        oneOnOneProtocolSelector: Provider<OneOnOneProtocolSelector>,
        @UserSessionBinding("pendingActionsRepository")
        pendingActionsRepository: Provider<PendingActionsRepository>,
        @UserSessionBinding("conversationRepository")
        conversationRepository: Provider<ConversationRepository>,
        @UserSessionBinding("oneOnOneMigrator")
        oneOnOneMigrator: Provider<OneOnOneMigrator>,
        @UserSessionBinding("userRepository")
        userRepository: Provider<UserRepository>,
    ): OneOnOneResolver =
        OneOnOneResolverImpl(
            userRepository(),
            oneOnOneProtocolSelector(),
            oneOnOneMigrator(),
            incrementalSyncRepository(),
            pendingActionsRepository(),
            conversationRepository(),
            mlsConversationRepository(),
        )

    @Provides
    @UserSessionBinding("recoverPendingOneOnOneResolutionsUseCase")
    fun userSessionProvideRecoverPendingOneOnOneResolutionsUseCase(
        @UserSessionBinding("cryptoTransactionProvider")
        cryptoTransactionProvider: Provider<CryptoTransactionProvider>,
        @UserSessionBinding("pendingActionsRepository")
        pendingActionsRepository: Provider<PendingActionsRepository>,
        @UserSessionBinding("syncStateObserver")
        syncStateObserver: Provider<UserSessionValue<Lazy<SyncStateObserver>>>,
        @UserSessionBinding("oneOnOneResolver")
        oneOnOneResolver: Provider<OneOnOneResolver>,
    ): RecoverPendingOneOnOneResolutionsUseCase =
        RecoverPendingOneOnOneResolutionsUseCaseImpl(
            pendingActionsRepository = pendingActionsRepository(),
            syncStateObserver = syncStateObserver().value.value,
            transactionProvider = cryptoTransactionProvider(),
            oneOnOneResolver = oneOnOneResolver()
        )

    @Provides
    @UserSessionBinding("recoverPendingMLSGroupJoinsUseCase")
    fun userSessionProvideRecoverPendingMLSGroupJoinsUseCase(
        @UserSessionBinding("joinExistingMLSConversationUseCase")
        joinExistingMLSConversationUseCase: Provider<JoinExistingMLSConversationUseCase>,
        @UserSessionBinding("cryptoTransactionProvider")
        cryptoTransactionProvider: Provider<CryptoTransactionProvider>,
        @UserSessionBinding("pendingActionsRepository")
        pendingActionsRepository: Provider<PendingActionsRepository>,
        @UserSessionBinding("conversationRepository")
        conversationRepository: Provider<ConversationRepository>,
        @UserSessionBinding("syncStateObserver")
        syncStateObserver: Provider<UserSessionValue<Lazy<SyncStateObserver>>>,
    ): RecoverPendingMLSGroupJoinsUseCase =
        RecoverPendingMLSGroupJoinsUseCaseImpl(
            pendingActionsRepository = pendingActionsRepository(),
            syncStateObserver = syncStateObserver().value.value,
            transactionProvider = cryptoTransactionProvider(),
            joinExistingMLSConversation = joinExistingMLSConversationUseCase(),
            conversationRepository = conversationRepository(),
        )

    @Provides
    @UserSessionBinding("updateSupportedProtocols")
    fun userSessionProvideUpdateSupportedProtocols(
        @UserSessionBinding("userConfigRepository")
        userConfigRepository: Provider<UserConfigRepository>,
        @UserSessionBinding("clientIdProvider")
        clientIdProvider: Provider<CurrentClientIdProvider>,
        @UserSessionBinding("clientRepository")
        clientRepository: Provider<ClientRepository>,
        @UserSessionBinding("userScopedLogger")
        userScopedLogger: Provider<KaliumLogger>,
        @UserSessionBinding("featureSupport")
        featureSupport: Provider<FeatureSupport>,
        @UserSessionBinding("userRepository")
        userRepository: Provider<UserRepository>,
    ): UpdateSelfUserSupportedProtocolsUseCase =
        UpdateSelfUserSupportedProtocolsUseCaseImpl(
            clientRepository(),
            userRepository(),
            userConfigRepository(),
            featureSupport(),
            clientIdProvider(),
            userScopedLogger()
        )

    @Provides
    @UserSessionBinding("updateSupportedProtocolsAndResolveOneOnOnes")
    fun userSessionProvideUpdateSupportedProtocolsAndResolveOneOnOnes(
        @UserSessionBinding("updateSupportedProtocols")
        updateSupportedProtocols: Provider<UpdateSelfUserSupportedProtocolsUseCase>,
        @UserSessionBinding("oneOnOneResolver")
        oneOnOneResolver: Provider<OneOnOneResolver>,
    ): UpdateSupportedProtocolsAndResolveOneOnOnesUseCase =
        UpdateSupportedProtocolsAndResolveOneOnOnesUseCaseImpl(
            updateSupportedProtocols(),
            oneOnOneResolver()
        )

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

}
