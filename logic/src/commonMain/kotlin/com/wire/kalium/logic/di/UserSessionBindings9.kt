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
import com.wire.kalium.logic.configuration.server.ServerConfigRepository
import com.wire.kalium.logic.data.analytics.AnalyticsDataSource
import com.wire.kalium.logic.data.analytics.AnalyticsRepository
import com.wire.kalium.logic.data.asset.DataStoragePaths
import com.wire.kalium.logic.data.asset.KaliumFileSystem
import com.wire.kalium.logic.data.asset.KaliumFileSystemImpl
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.call.EndCallOnMLSResetUseCase
import com.wire.kalium.logic.data.client.CryptoTransactionProvider
import com.wire.kalium.logic.data.client.LegalHoldClientsImpl
import com.wire.kalium.logic.data.client.MLSClientProvider
import com.wire.kalium.logic.data.client.remote.ClientRemoteRepository
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.EpochChangesObserver
import com.wire.kalium.logic.data.conversation.FetchConversationUseCase
import com.wire.kalium.logic.data.conversation.MLSConversationDataSource
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.conversation.PersistConversationsUseCase
import com.wire.kalium.logic.data.conversation.ProposalTimer
import com.wire.kalium.logic.data.conversation.ResetMLSConversationUseCase
import com.wire.kalium.logic.data.conversation.ResetMLSConversationUseCaseImpl
import com.wire.kalium.logic.data.conversation.TypingIndicatorIncomingRepositoryImpl
import com.wire.kalium.logic.data.conversation.mls.PendingActionsRepository
import com.wire.kalium.logic.data.e2ei.E2EIRepository
import com.wire.kalium.logic.data.id.SelfTeamIdProvider
import com.wire.kalium.logic.data.keypackage.KeyPackageLimitsProvider
import com.wire.kalium.logic.data.keypackage.KeyPackageRepository
import com.wire.kalium.logic.data.meeting.MeetingDataSource
import com.wire.kalium.logic.data.meeting.MeetingRepository
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.mlspublickeys.MLSPublicKeysRepository
import com.wire.kalium.logic.data.properties.ConversationFoldersPropertyDataSource
import com.wire.kalium.logic.data.properties.LinkPreviewsPropertyDataSource
import com.wire.kalium.logic.data.properties.ReadReceiptsPropertyDataSource
import com.wire.kalium.logic.data.properties.ScreenshotCensoringPropertyDataSource
import com.wire.kalium.logic.data.properties.TypingIndicatorPropertyDataSource
import com.wire.kalium.logic.data.properties.UserPropertiesSyncDataSource
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.analytics.GetAnalyticsContactsDataUseCase
import com.wire.kalium.logic.feature.call.usecase.CreateAndPersistRecentlyEndedCallMetadataUseCase
import com.wire.kalium.logic.feature.call.usecase.CreateAndPersistRecentlyEndedCallMetadataUseCaseImpl
import com.wire.kalium.logic.feature.conversation.LegacyConversationBinding
import com.wire.kalium.logic.feature.conversation.MigrateConversationToMLSUseCase
import com.wire.kalium.logic.feature.conversation.MigrateConversationToMLSUseCaseImpl
import com.wire.kalium.logic.feature.conversation.TypingIndicatorSyncManager
import com.wire.kalium.logic.feature.conversation.delete.DeleteConversationUseCase
import com.wire.kalium.logic.feature.conversation.delete.DeleteConversationUseCaseImpl
import com.wire.kalium.logic.feature.e2ei.CheckCrlRevocationListUseCase
import com.wire.kalium.logic.feature.e2ei.usecase.FetchConversationMLSVerificationStatusUseCase
import com.wire.kalium.logic.feature.e2ei.usecase.FetchConversationMLSVerificationStatusUseCaseImpl
import com.wire.kalium.logic.feature.e2ei.usecase.FetchMLSVerificationStatusUseCase
import com.wire.kalium.logic.feature.e2ei.usecase.FetchMLSVerificationStatusUseCaseImpl
import com.wire.kalium.logic.feature.e2ei.usecase.ObserveE2EIConversationsVerificationStatusesUseCase
import com.wire.kalium.logic.feature.e2ei.usecase.ObserveE2EIConversationsVerificationStatusesUseCaseImpl
import com.wire.kalium.logic.feature.meeting.SyncMeetingsUseCase
import com.wire.kalium.logic.feature.meeting.SyncMeetingsUseCaseImpl
import com.wire.kalium.logic.feature.mlsmigration.MLSMigrator
import com.wire.kalium.logic.feature.server.GetTeamUrlUseCase
import com.wire.kalium.logic.feature.session.GetProxyCredentialsUseCase
import com.wire.kalium.logic.feature.session.GetProxyCredentialsUseCaseImpl
import com.wire.kalium.logic.feature.user.IsE2EIEnabledUseCase
import com.wire.kalium.logic.feature.user.IsMeetingsEnabledUseCase
import com.wire.kalium.logic.feature.user.SyncContactsUseCase
import com.wire.kalium.logic.feature.user.migration.MigrateFromPersonalToTeamUseCase
import com.wire.kalium.logic.feature.user.migration.MigrateFromPersonalToTeamUseCaseImpl
import com.wire.kalium.logic.feature.user.screenshotCensoring.ObserveScreenshotCensoringConfigUseCase
import com.wire.kalium.logic.feature.user.screenshotCensoring.ObserveScreenshotCensoringConfigUseCaseImpl
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.sync.ObserveSyncStateUseCase
import com.wire.kalium.logic.sync.receiver.handler.legalhold.LegalHoldTransitionJournal
import com.wire.kalium.logic.sync.receiver.handler.legalhold.LegalHoldUserTransitionJournal
import com.wire.kalium.messaging.hooks.PersistenceEventHookNotifier
import com.wire.kalium.network.networkContainer.AuthenticatedNetworkContainer
import com.wire.kalium.network.session.SessionManager
import com.wire.kalium.userstorage.di.UserStorage
import com.wire.kalium.util.DelicateKaliumApi
import com.wire.kalium.util.InternalKaliumApi
import dev.zacsweers.metro.Provider
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.sync.Mutex
import okio.Path.Companion.toPath

@Suppress("TooManyFunctions", "LongParameterList", "LargeClass") // Individual qualified construction edges.
internal interface UserSessionBindings9 {
    @Provides
    @UserSessionBinding("observeScreenshotCensoringConfig")
    fun userSessionProvideObserveScreenshotCensoringConfig(
        @UserSessionBinding("userConfigRepository")
        userConfigRepository: Provider<UserConfigRepository>,
    ): ObserveScreenshotCensoringConfigUseCase =
        ObserveScreenshotCensoringConfigUseCaseImpl(userConfigRepository = userConfigRepository())

    @Provides
    @UserSessionBinding("fetchConversationMLSVerificationStatus")
    fun userSessionProvideFetchConversationMLSVerificationStatus(
        @UserSessionBinding("fetchMLSVerificationStatusUseCase")
        fetchMLSVerificationStatusUseCase: Provider<FetchMLSVerificationStatusUseCase>,
        @UserSessionBinding("conversationRepository")
        conversationRepository: Provider<ConversationRepository>,
    ): FetchConversationMLSVerificationStatusUseCase =
        FetchConversationMLSVerificationStatusUseCaseImpl(
            conversationRepository(),
            fetchMLSVerificationStatusUseCase()
        )

    @Provides
    @UserSessionBinding("kaliumFileSystem")
    @SingleIn(UserLifetime::class)
    fun userSessionProvideKaliumFileSystem(
        @UserSessionBinding("dataStoragePaths")
        dataStoragePaths: Provider<DataStoragePaths>,
    ): KaliumFileSystem =
        // Create the cache and asset storage directories
        KaliumFileSystemImpl(dataStoragePaths()).also {
            if (!it.exists(dataStoragePaths().cachePath.value.toPath()))
                it.createDirectories(dataStoragePaths().cachePath.value.toPath())
            if (!it.exists(dataStoragePaths().assetStoragePath.value.toPath()))
                it.createDirectories(dataStoragePaths().assetStoragePath.value.toPath())
        }

    @Provides
    @UserSessionBinding("checkCrlRevocationList")
    fun userSessionProvideCheckCrlRevocationList(
        @UserSessionBinding("userScopedLogger")
        userScopedLogger: Provider<KaliumLogger>,
        @UserSessionBinding("e2eiRepository")
        e2eiRepository: Provider<E2EIRepository>,
        @UserSessionBinding("isE2EIEnabled")
        isE2EIEnabled: Provider<IsE2EIEnabledUseCase>,
    ): CheckCrlRevocationListUseCase =
        CheckCrlRevocationListUseCase(
            e2eiRepository = e2eiRepository(),
            isE2EIEnabledUseCase = isE2EIEnabled(),
            kaliumLogger = userScopedLogger()
        )

    @Provides
    @UserSessionBinding("createAndPersistRecentlyEndedCallMetadata")
    fun userSessionProvideCreateAndPersistRecentlyEndedCallMetadata(
        @UserSessionBinding("conversationRepository")
        conversationRepository: Provider<ConversationRepository>,
        @UserSessionBinding("callRepository")
        callRepository: Provider<CallRepository>,
        @UserSessionBinding("selfTeamId")
        selfTeamId: Provider<SelfTeamIdProvider>,
    ): CreateAndPersistRecentlyEndedCallMetadataUseCase =
        CreateAndPersistRecentlyEndedCallMetadataUseCaseImpl(
            callRepository = callRepository(),
            conversationRepository = conversationRepository(),
            selfTeamIdProvider = selfTeamId()
        )

    @Provides
    @UserSessionBinding("migrateFromPersonalToTeam")
    fun userSessionProvideMigrateFromPersonalToTeam(
        @UserSessionBinding("invalidateTeamId")
        invalidateTeamId: Provider<UserSessionValue<() -> Unit>>,
        @UserSessionBinding("userRepository")
        userRepository: Provider<UserRepository>,
        @UserSessionBinding("syncContacts")
        syncContacts: Provider<SyncContactsUseCase>,
        @UserSessionBinding("userId")
        userId: Provider<UserId>,
    ): MigrateFromPersonalToTeamUseCase =
        MigrateFromPersonalToTeamUseCaseImpl(userId(), userRepository(), syncContacts(), invalidateTeamId().value)

    @Provides
    @UserSessionBinding("getProxyCredentials")
    fun userSessionProvideGetProxyCredentials(
        @UserSessionBinding("sessionManager")
        sessionManager: Provider<SessionManager>,
    ): GetProxyCredentialsUseCase =
        GetProxyCredentialsUseCaseImpl(sessionManager())

    @Provides
    @UserSessionBinding("fetchMLSVerificationStatusUseCase")
    @SingleIn(UserLifetime::class)
    fun userSessionProvideFetchMLSVerificationStatusUseCase(
        @UserSessionBinding("mlsConversationRepository")
        mlsConversationRepository: Provider<MLSConversationRepository>,
        @UserSessionBinding("conversationRepository")
        conversationRepository: Provider<ConversationRepository>,
        @UserSessionBinding("mlsClientProvider")
        mlsClientProvider: Provider<MLSClientProvider>,
        @UserSessionBinding("userScopedLogger")
        userScopedLogger: Provider<KaliumLogger>,
        @UserSessionBinding("persistMessage")
        persistMessage: Provider<PersistMessageUseCase>,
        @UserSessionBinding("userRepository")
        userRepository: Provider<UserRepository>,
        @UserSessionBinding("userId")
        userId: Provider<UserId>,
    ): FetchMLSVerificationStatusUseCase =
        FetchMLSVerificationStatusUseCaseImpl(
            mlsClientProvider(),
            conversationRepository(),
            persistMessage(),
            mlsConversationRepository(),
            userId(),
            userRepository(),
            userScopedLogger(),
        )

    @Provides
    @UserSessionBinding("observeE2EIConversationsVerificationStatuses")
    @SingleIn(UserLifetime::class)
    fun userSessionProvideObserveE2EIConversationsVerificationStatuses(
        @UserSessionBinding("fetchMLSVerificationStatusUseCase")
        fetchMLSVerificationStatusUseCase: Provider<FetchMLSVerificationStatusUseCase>,
        @UserSessionBinding("epochChangesObserver")
        epochChangesObserver: Provider<EpochChangesObserver>,
        @UserSessionBinding("userScopedLogger")
        userScopedLogger: Provider<KaliumLogger>,
    ): ObserveE2EIConversationsVerificationStatusesUseCase =
        ObserveE2EIConversationsVerificationStatusesUseCaseImpl(
            fetchMLSVerificationStatus = fetchMLSVerificationStatusUseCase(),
            epochChangesObserver = epochChangesObserver(),
            kaliumLogger = userScopedLogger()
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
    @UserSessionBinding("analyticsRepository")
    fun userSessionProvideAnalyticsRepository(
        @UserSessionBinding("userStorage")
        userStorage: Provider<UserStorage>,
        @UserSessionBinding("userId")
        userId: Provider<UserId>,
    ): AnalyticsRepository =
        AnalyticsDataSource(
            userDAO = userStorage().database.userDAO,
            selfUserId = userId(),
            metadataDAO = userStorage().database.metadataDAO
        )

    @Provides
    @UserSessionBinding("getTeamUrlUseCase")
    fun userSessionProvideGetTeamUrlUseCase(
        @UserSessionBinding("serverConfigRepository")
        serverConfigRepository: Provider<ServerConfigRepository>,
        @UserSessionBinding("userId")
        userId: Provider<UserId>,
    ): GetTeamUrlUseCase =
        GetTeamUrlUseCase(
            userId(),
            serverConfigRepository(),
        )

    @Provides
    @UserSessionBinding("getAnalyticsContactsData")
    fun userSessionProvideGetAnalyticsContactsData(
        @UserSessionBinding("sessionCoroutineScope")
        sessionCoroutineScope: Provider<CoroutineScope>,
        @UserSessionBinding("userConfigRepository")
        userConfigRepository: Provider<UserConfigRepository>,
        @UserSessionBinding("analyticsRepository")
        analyticsRepository: Provider<AnalyticsRepository>,
        @UserSessionBinding("selfTeamId")
        selfTeamId: Provider<SelfTeamIdProvider>,
    ): GetAnalyticsContactsDataUseCase =
        GetAnalyticsContactsDataUseCase(
            selfTeamIdProvider = selfTeamId(),
            analyticsRepository = analyticsRepository(),
            userConfigRepository = userConfigRepository(),
            coroutineScope = sessionCoroutineScope(),
        )

    @Provides
    @UserSessionBinding("deleteConversationUseCase")
    fun userSessionProvideDeleteConversationUseCase(
        @UserSessionBinding("currentPersistenceEventHookNotifier")
        currentPersistenceEventHookNotifier: Provider<PersistenceEventHookNotifier>,
        @UserSessionBinding("mlsConversationRepository")
        mlsConversationRepository: Provider<MLSConversationRepository>,
        @UserSessionBinding("conversationRepository")
        conversationRepository: Provider<ConversationRepository>,
        @UserSessionBinding("userId")
        userId: Provider<UserId>,
    ): DeleteConversationUseCase =
        DeleteConversationUseCaseImpl(
            conversationRepository = conversationRepository(),
            mlsConversationRepository = mlsConversationRepository(),
            persistenceEventHookNotifier = currentPersistenceEventHookNotifier(),
            selfUserId = userId(),
        )

    @Provides
    @UserSessionBinding("resetMlsConversation")
    fun userSessionProvideResetMlsConversation(
        @UserSessionBinding("cryptoTransactionProvider")
        cryptoTransactionProvider: Provider<CryptoTransactionProvider>,
        @UserSessionBinding("mlsConversationRepository")
        mlsConversationRepository: Provider<MLSConversationRepository>,
        @UserSessionBinding("fetchConversationUseCase")
        fetchConversationUseCase: Provider<FetchConversationUseCase>,
        @UserSessionBinding("conversationRepository")
        conversationRepository: Provider<ConversationRepository>,
        @UserSessionBinding("userConfigRepository")
        userConfigRepository: Provider<UserConfigRepository>,
        @UserSessionBinding("endCallOnMLSReset")
        endCallOnMLSReset: Provider<EndCallOnMLSResetUseCase>,
        @UserSessionBinding("kaliumConfigs")
        kaliumConfigs: Provider<KaliumConfigs>,
        @UserSessionBinding("userId")
        userId: Provider<UserId>,
    ): ResetMLSConversationUseCase =
        ResetMLSConversationUseCaseImpl(
            selfUserId = userId(),
            userConfig = userConfigRepository(),
            transactionProvider = cryptoTransactionProvider(),
            conversationRepository = conversationRepository(),
            mlsConversationRepository = mlsConversationRepository(),
            fetchConversationUseCase = fetchConversationUseCase(),
            endCallOnMLSReset = endCallOnMLSReset(),
            kaliumConfigs = kaliumConfigs(),
        )

    @Provides
    @UserSessionBinding("migrateConversationToMLS")
    fun userSessionProvideMigrateConversationToMLS(
        @UserSessionBinding("cryptoTransactionProvider")
        cryptoTransactionProvider: Provider<CryptoTransactionProvider>,
        @UserSessionBinding("conversationRepository")
        conversationRepository: Provider<ConversationRepository>,
        @UserSessionBinding("mlsMigrator")
        mlsMigrator: Provider<MLSMigrator>,
    ): MigrateConversationToMLSUseCase =
        MigrateConversationToMLSUseCaseImpl(
            mlsMigrator = mlsMigrator(),
            conversationRepository = conversationRepository(),
            coreCryptoTransactionProvider = cryptoTransactionProvider()
        )

    @Provides
    @UserSessionBinding("meetingRepository")
    fun userSessionProvideMeetingRepository(
        @UserSessionBinding("authenticatedNetworkContainer")
        authenticatedNetworkContainer: Provider<AuthenticatedNetworkContainer>,
        @UserSessionBinding("persistConversationsUseCase")
        persistConversationsUseCase: Provider<PersistConversationsUseCase>,
        @UserSessionBinding("mlsConversationRepository")
        mlsConversationRepository: Provider<MLSConversationRepository>,
        @UserSessionBinding("pendingActionsRepository")
        pendingActionsRepository: Provider<PendingActionsRepository>,
        @UserSessionBinding("conversationRepository")
        conversationRepository: Provider<ConversationRepository>,
        @UserSessionBinding("userRepository")
        userRepository: Provider<UserRepository>,
        @UserSessionBinding("userStorage")
        userStorage: Provider<UserStorage>,
        @UserSessionBinding("userId")
        userId: Provider<UserId>,
    ): MeetingRepository =
        MeetingDataSource(
            selfUserId = userId(),
            meetingDAO = userStorage().database.meetingDao,
            meetingApi = authenticatedNetworkContainer().meetingApi,
            mlsConversationRepository = mlsConversationRepository(),
            conversationRepository = conversationRepository(),
            pendingActionsRepository = pendingActionsRepository(),
            userRepository = userRepository(),
            persistConversations = persistConversationsUseCase(),
        )

    @Provides
    @UserSessionBinding("syncMeetingsUseCase")
    fun userSessionProvideSyncMeetingsUseCase(
        @UserSessionBinding("cryptoTransactionProvider")
        cryptoTransactionProvider: Provider<CryptoTransactionProvider>,
        @UserSessionBinding("isMeetingsEnabled")
        isMeetingsEnabled: Provider<IsMeetingsEnabledUseCase>,
        @UserSessionBinding("meetingRepository")
        meetingRepository: Provider<MeetingRepository>,
    ): SyncMeetingsUseCase =
        SyncMeetingsUseCaseImpl(
            meetingRepository = meetingRepository(),
            isMeetingsEnabledUseCase = isMeetingsEnabled(),
            transactionProvider = cryptoTransactionProvider()
        )

    @Provides
    @UserSessionBinding("messageRepositoryFactory")
    fun userSessionProvideMessageRepositoryFactory(
        @UserSessionBinding("mlsMessageApi")
        mlsMessageApi: Provider<com.wire.kalium.network.api.base.authenticated.message.MLSMessageApi>,
        @UserSessionBinding("messageApi")
        messageApi: Provider<com.wire.kalium.network.api.base.authenticated.message.MessageApi>,
        @UserSessionBinding("messageDao")
        messageDao: Provider<com.wire.kalium.persistence.dao.message.MessageDAO>,
        @UserSessionBinding("userId")
        userId: Provider<UserId>,
    ): UserSessionMessageRepositoryFactory =
        UserSessionMessageRepositoryFactory(messageApi, mlsMessageApi, messageDao, userId)

    @Provides
    @UserSessionBinding("messageApi")
    fun userSessionProvideMessageApi(
        @UserSessionBinding("authenticatedNetworkContainer")
        authenticatedNetworkContainer: Provider<AuthenticatedNetworkContainer>,
    ): com.wire.kalium.network.api.base.authenticated.message.MessageApi =
        authenticatedNetworkContainer().messageApi

    @Provides
    @UserSessionBinding("mlsMessageApi")
    fun userSessionProvideMlsMessageApi(
        @UserSessionBinding("authenticatedNetworkContainer")
        authenticatedNetworkContainer: Provider<AuthenticatedNetworkContainer>,
    ): com.wire.kalium.network.api.base.authenticated.message.MLSMessageApi =
        authenticatedNetworkContainer().mlsMessageApi

    @Provides
    @UserSessionBinding("messageDao")
    fun userSessionProvideMessageDao(
        @UserSessionBinding("userStorage")
        userStorage: Provider<UserStorage>,
    ): com.wire.kalium.persistence.dao.message.MessageDAO =
        userStorage().database.messageDAO

    @Provides
    @UserSessionBinding("userPropertyRepositoryReadReceiptsPropertyDataSource")
    fun userSessionProvideUserPropertyRepositoryReadReceiptsPropertyDataSource(
        @UserSessionBinding("authenticatedNetworkContainer")
        authenticatedNetworkContainer: Provider<AuthenticatedNetworkContainer>,
        @UserSessionBinding("userConfigRepository")
        userConfigRepository: Provider<UserConfigRepository>,
    ): ReadReceiptsPropertyDataSource =
        ReadReceiptsPropertyDataSource(authenticatedNetworkContainer().propertiesApi, userConfigRepository())

    @Provides
    @UserSessionBinding("userPropertyRepositoryTypingIndicatorPropertyDataSource")
    fun userSessionProvideUserPropertyRepositoryTypingIndicatorPropertyDataSource(
        @UserSessionBinding("authenticatedNetworkContainer")
        authenticatedNetworkContainer: Provider<AuthenticatedNetworkContainer>,
        @UserSessionBinding("userConfigRepository")
        userConfigRepository: Provider<UserConfigRepository>,
    ): TypingIndicatorPropertyDataSource =
        TypingIndicatorPropertyDataSource(authenticatedNetworkContainer().propertiesApi, userConfigRepository())

    @Provides
    @UserSessionBinding("userPropertyRepositoryLinkPreviewsPropertyDataSource")
    fun userSessionProvideUserPropertyRepositoryLinkPreviewsPropertyDataSource(
        @UserSessionBinding("authenticatedNetworkContainer")
        authenticatedNetworkContainer: Provider<AuthenticatedNetworkContainer>,
        @UserSessionBinding("userConfigRepository")
        userConfigRepository: Provider<UserConfigRepository>,
    ): LinkPreviewsPropertyDataSource =
        LinkPreviewsPropertyDataSource(authenticatedNetworkContainer().propertiesApi, userConfigRepository())

    @Provides
    @UserSessionBinding("userPropertyRepositoryScreenshotCensoringPropertyDataSource")
    fun userSessionProvideUserPropertyRepositoryScreenshotCensoringPropertyDataSource(
        @UserSessionBinding("authenticatedNetworkContainer")
        authenticatedNetworkContainer: Provider<AuthenticatedNetworkContainer>,
        @UserSessionBinding("userConfigRepository")
        userConfigRepository: Provider<UserConfigRepository>,
    ): ScreenshotCensoringPropertyDataSource =
        ScreenshotCensoringPropertyDataSource(authenticatedNetworkContainer().propertiesApi, userConfigRepository())

    @Provides
    @UserSessionBinding("userPropertyRepositoryUserPropertiesSyncDataSource")
    fun userSessionProvideUserPropertyRepositoryUserPropertiesSyncDataSource(
        @UserSessionBinding("authenticatedNetworkContainer")
        authenticatedNetworkContainer: Provider<AuthenticatedNetworkContainer>,
        @UserSessionBinding("userConfigRepository")
        userConfigRepository: Provider<UserConfigRepository>,
    ): UserPropertiesSyncDataSource =
        UserPropertiesSyncDataSource(authenticatedNetworkContainer().propertiesApi, userConfigRepository())

    @Provides
    @UserSessionBinding("userPropertyRepositoryConversationFoldersPropertyDataSource")
    fun userSessionProvideUserPropertyRepositoryConversationFoldersPropertyDataSource(
        @UserSessionBinding("authenticatedNetworkContainer")
        authenticatedNetworkContainer: Provider<AuthenticatedNetworkContainer>,
        @UserSessionBinding("userId")
        userId: Provider<UserId>,
    ): ConversationFoldersPropertyDataSource =
        ConversationFoldersPropertyDataSource(authenticatedNetworkContainer().propertiesApi, userId())

    @Provides
    @UserSessionBinding("mlsConversationRepositoryMLSConversationDataSource")
    fun userSessionProvideMlsConversationRepositoryMLSConversationDataSource(
        @UserSessionBinding("authenticatedNetworkContainer")
        authenticatedNetworkContainer: Provider<AuthenticatedNetworkContainer>,
        @UserSessionBinding("keyPackageLimitsProvider")
        keyPackageLimitsProvider: Provider<KeyPackageLimitsProvider>,
        @UserSessionBinding("mlsPublicKeysRepository")
        mlsPublicKeysRepository: Provider<MLSPublicKeysRepository>,
        @UserSessionBinding("keyPackageRepository")
        keyPackageRepository: Provider<KeyPackageRepository>,
        @UserSessionBinding("proposalTimersFlow")
        proposalTimersFlow: Provider<MutableSharedFlow<ProposalTimer>>,
        @UserSessionBinding("userStorage")
        userStorage: Provider<UserStorage>,
        @UserSessionBinding("mlsMutex")
        mlsMutex: Provider<Mutex>,
        @UserSessionBinding("userId")
        userId: Provider<UserId>,
    ): MLSConversationDataSource =
        MLSConversationDataSource(
            userId(),
            keyPackageRepository(),
            userStorage().database.conversationDAO,
            authenticatedNetworkContainer().clientApi,
            mlsPublicKeysRepository(),
            proposalTimersFlow(),
            keyPackageLimitsProvider(),
            mutex = mlsMutex()
        )

    @Provides
    @UserSessionBinding("legalHoldHandlerLegalHoldTransitionJournal")
    fun userSessionProvideLegalHoldHandlerLegalHoldTransitionJournal(
        @UserSessionBinding("userStorage")
        userStorage: Provider<UserStorage>,
    ): LegalHoldTransitionJournal =
        LegalHoldTransitionJournal(userStorage().database.metadataDAO)

    @Provides
    @UserSessionBinding("legalHoldHandlerLegalHoldUserTransitionJournal")
    fun userSessionProvideLegalHoldHandlerLegalHoldUserTransitionJournal(
        @UserSessionBinding("userStorage")
        userStorage: Provider<UserStorage>,
    ): LegalHoldUserTransitionJournal =
        LegalHoldUserTransitionJournal(userStorage().database.metadataDAO)

    @Provides
    @UserSessionBinding("legalHoldHandlerLegalHoldClientsImpl")
    fun userSessionProvideLegalHoldHandlerLegalHoldClientsImpl(
        @UserSessionBinding("authenticatedNetworkContainer")
        authenticatedNetworkContainer: Provider<AuthenticatedNetworkContainer>,
        @UserSessionBinding("clientRemoteRepository")
        clientRemoteRepository: Provider<ClientRemoteRepository>,
        @UserSessionBinding("userStorage")
        userStorage: Provider<UserStorage>,
        @UserSessionBinding("userId")
        userId: Provider<UserId>,
    ): LegalHoldClientsImpl =
        LegalHoldClientsImpl(
            userId(),
            userStorage().database.clientDAO,
            authenticatedNetworkContainer().clientApi,
            clientRemoteRepository()
        )

}
