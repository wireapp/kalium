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
import com.wire.kalium.logic.data.analytics.AnalyticsRepository
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.asset.KaliumFileSystem
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.client.CryptoTransactionProvider
import com.wire.kalium.logic.data.client.LegalHoldClientsImpl
import com.wire.kalium.logic.data.client.guardedBy
import com.wire.kalium.logic.data.client.remote.ClientRemoteRepository
import com.wire.kalium.logic.data.conversation.ConversationGroupRepository
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.FetchConversationUseCase
import com.wire.kalium.logic.data.conversation.FetchMLSOneToOneConversationUseCase
import com.wire.kalium.logic.data.conversation.FetchMLSOneToOneConversationUseCaseImpl
import com.wire.kalium.logic.data.conversation.JoinExistingMLSConversationUseCase
import com.wire.kalium.logic.data.conversation.JoinExistingMLSConversationUseCaseImpl
import com.wire.kalium.logic.data.conversation.JoinExistingMLSConversationsUseCase
import com.wire.kalium.logic.data.conversation.JoinExistingMLSConversationsUseCaseImpl
import com.wire.kalium.logic.data.conversation.JoinSubconversationUseCase
import com.wire.kalium.logic.data.conversation.JoinSubconversationUseCaseImpl
import com.wire.kalium.logic.data.conversation.LeaveSubconversationUseCase
import com.wire.kalium.logic.data.conversation.LeaveSubconversationUseCaseImpl
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.conversation.NewGroupConversationSystemMessagesCreator
import com.wire.kalium.logic.data.conversation.NewGroupConversationSystemMessagesCreatorImpl
import com.wire.kalium.logic.data.conversation.PersistConversationsUseCase
import com.wire.kalium.logic.data.conversation.PersistConversationsUseCaseImpl
import com.wire.kalium.logic.data.conversation.ResetMLSConversationUseCase
import com.wire.kalium.logic.data.conversation.SubconversationRepositoryImpl
import com.wire.kalium.logic.data.conversation.UpdateConversationProtocolUseCase
import com.wire.kalium.logic.data.conversation.mls.PendingActionsRepository
import com.wire.kalium.logic.data.e2ei.E2EIRepository
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.id.FederatedIdMapper
import com.wire.kalium.logic.data.id.QualifiedIdMapper
import com.wire.kalium.logic.data.id.SelfTeamIdProvider
import com.wire.kalium.logic.data.logout.LogoutRepository
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.message.PendingSubconversationProposals
import com.wire.kalium.logic.data.message.PendingSubconversationProposalsImpl
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.message.PersistReactionUseCase
import com.wire.kalium.logic.data.message.PersistReactionUseCaseImpl
import com.wire.kalium.logic.data.message.SystemMessageInserterImpl
import com.wire.kalium.logic.data.message.paging.NomadMessagePagingCoordinator
import com.wire.kalium.logic.data.message.paging.NomadMessagePagingCoordinatorImpl
import com.wire.kalium.logic.data.message.reaction.ReactionRepositoryImpl
import com.wire.kalium.logic.data.message.receipt.ReceiptRepositoryImpl
import com.wire.kalium.logic.data.mls.ConversationProtocolGetterImpl
import com.wire.kalium.logic.data.notification.PushTokenRepository
import com.wire.kalium.logic.data.prekey.PreKeyRepository
import com.wire.kalium.logic.data.publicuser.UserSearchApiWrapper
import com.wire.kalium.logic.data.publicuser.UserSearchApiWrapperImpl
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.session.token.AccessTokenRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.UserSessionScopeProvider
import com.wire.kalium.logic.feature.analytics.GetAnalyticsContactsDataUseCase
import com.wire.kalium.logic.feature.analytics.GetCurrentAnalyticsTrackingIdentifierUseCase
import com.wire.kalium.logic.feature.analytics.ObserveAnalyticsTrackingIdentifierStatusUseCase
import com.wire.kalium.logic.feature.analytics.SetNewUserTrackingIdentifierUseCase
import com.wire.kalium.logic.feature.asset.ValidateAssetFileTypeUseCase
import com.wire.kalium.logic.feature.asset.ValidateAssetFileTypeUseCaseImpl
import com.wire.kalium.logic.feature.auth.ClearUserDataUseCase
import com.wire.kalium.logic.feature.auth.LogoutCallback
import com.wire.kalium.logic.feature.auth.LogoutUseCase
import com.wire.kalium.logic.feature.auth.LogoutUseCaseImpl
import com.wire.kalium.logic.feature.call.CallManager
import com.wire.kalium.logic.feature.call.usecase.ConversationClientsInCallUpdater
import com.wire.kalium.logic.feature.call.usecase.ConversationClientsInCallUpdaterImpl
import com.wire.kalium.logic.feature.call.usecase.EndCallUseCase
import com.wire.kalium.logic.feature.call.usecase.EpochInfoUpdater
import com.wire.kalium.logic.feature.call.usecase.EpochInfoUpdaterImpl
import com.wire.kalium.logic.feature.call.usecase.ObserveEstablishedCallsUseCase
import com.wire.kalium.logic.feature.client.ClearClientDataUseCase
import com.wire.kalium.logic.feature.client.FetchUsersClientsFromRemoteUseCase
import com.wire.kalium.logic.feature.client.FetchUsersClientsFromRemoteUseCaseImpl
import com.wire.kalium.logic.feature.client.LegacyClientBinding
import com.wire.kalium.logic.feature.conversation.MLSFaultyKeysConversationsRepairUseCaseImpl
import com.wire.kalium.logic.feature.conversation.ObserveOtherUserSecurityClassificationLabelUseCase
import com.wire.kalium.logic.feature.conversation.ObserveOtherUserSecurityClassificationLabelUseCaseImpl
import com.wire.kalium.logic.feature.conversation.mls.MLSOneOnOneConversationResolver
import com.wire.kalium.logic.feature.conversation.mls.OneOnOneMigrator
import com.wire.kalium.logic.feature.conversation.mls.OneOnOneMigratorImpl
import com.wire.kalium.logic.feature.conversation.mls.OneOnOneResolver
import com.wire.kalium.logic.feature.conversation.mls.OneOnOneResolverImpl
import com.wire.kalium.logic.feature.conversation.mls.RecoverPendingMLSGroupJoinsUseCase
import com.wire.kalium.logic.feature.conversation.mls.RecoverPendingMLSGroupJoinsUseCaseImpl
import com.wire.kalium.logic.feature.conversation.mls.RecoverPendingOneOnOneResolutionsUseCase
import com.wire.kalium.logic.feature.conversation.mls.RecoverPendingOneOnOneResolutionsUseCaseImpl
import com.wire.kalium.logic.feature.debug.LegacyDebugBinding
import com.wire.kalium.logic.feature.debug.OptimizeDatabaseUseCaseImpl
import com.wire.kalium.logic.feature.debug.RepairFaultyRemovalKeysUseCase
import com.wire.kalium.logic.feature.e2ei.CheckCrlRevocationListUseCase
import com.wire.kalium.logic.feature.e2ei.usecase.GetMLSClientIdentityUseCase
import com.wire.kalium.logic.feature.message.PendingProposalScheduler
import com.wire.kalium.logic.feature.message.StaleEpochVerifier
import com.wire.kalium.logic.feature.message.StaleEpochVerifierImpl
import com.wire.kalium.logic.feature.message.linkpreview.LinkPreviewImagesResolver
import com.wire.kalium.logic.feature.message.linkpreview.LinkPreviewImagesResolverImpl
import com.wire.kalium.logic.feature.mlsmigration.MLSMigrator
import com.wire.kalium.logic.feature.mlsmigration.MLSMigratorImpl
import com.wire.kalium.logic.feature.proteus.ProteusPreKeyRefiller
import com.wire.kalium.logic.feature.proteus.ProteusPreKeyRefillerImpl
import com.wire.kalium.logic.feature.protocol.OneOnOneProtocolSelector
import com.wire.kalium.logic.feature.publicuser.RefreshUsersWithoutMetadataUseCase
import com.wire.kalium.logic.feature.publicuser.RefreshUsersWithoutMetadataUseCaseImpl
import com.wire.kalium.logic.feature.selfDeletingMessages.ObserveSelfDeletionTimerSettingsForConversationUseCase
import com.wire.kalium.logic.feature.selfDeletingMessages.ObserveSelfDeletionTimerSettingsForConversationUseCaseImpl
import com.wire.kalium.logic.feature.selfDeletingMessages.PersistNewSelfDeletionTimerUseCase
import com.wire.kalium.logic.feature.selfDeletingMessages.PersistNewSelfDeletionTimerUseCaseImpl
import com.wire.kalium.logic.feature.server.GetTeamUrlUseCase
import com.wire.kalium.logic.feature.session.DeregisterTokenUseCase
import com.wire.kalium.logic.feature.session.token.AccessTokenRefresher
import com.wire.kalium.logic.feature.session.token.AccessTokenRefresherFactory
import com.wire.kalium.logic.feature.session.token.AccessTokenRefresherFactoryImpl
import com.wire.kalium.logic.feature.session.token.AccessTokenRefresherImpl
import com.wire.kalium.logic.feature.user.IsE2EIEnabledUseCase
import com.wire.kalium.logic.feature.user.LegacyUserBinding
import com.wire.kalium.logic.feature.user.MarkFileSharingChangeAsNotifiedUseCase
import com.wire.kalium.logic.feature.user.ObserveE2EIRequiredUseCase
import com.wire.kalium.logic.feature.user.ObserveE2EIRequiredUseCaseImpl
import com.wire.kalium.logic.feature.user.SyncContactsUseCase
import com.wire.kalium.logic.feature.user.migration.MigrateFromPersonalToTeamUseCase
import com.wire.kalium.logic.feature.user.migration.MigrateFromPersonalToTeamUseCaseImpl
import com.wire.kalium.logic.featureFlags.FeatureSupport
import com.wire.kalium.logic.featureFlags.FeatureSupportImpl
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.network.guardedBy
import com.wire.kalium.logic.sync.SyncStateObserver
import com.wire.kalium.logic.sync.UserSessionWorkScheduler
import com.wire.kalium.logic.sync.receiver.conversation.message.MLSMessageUnpacker
import com.wire.kalium.logic.sync.receiver.conversation.message.MLSMessageUnpackerImpl
import com.wire.kalium.logic.sync.receiver.conversation.message.ProteusMessageUnpacker
import com.wire.kalium.logic.sync.receiver.conversation.message.ProteusMessageUnpackerImpl
import com.wire.kalium.logic.sync.receiver.handler.legalhold.LegalHoldTransitionJournal
import com.wire.kalium.logic.sync.receiver.handler.legalhold.LegalHoldUserTransitionJournal
import com.wire.kalium.logic.sync.receiving.CallSignalJournal
import com.wire.kalium.logic.sync.receiving.PendingConversationCleanup
import com.wire.kalium.logic.util.MessageContentEncoder
import com.wire.kalium.messaging.hooks.PersistenceEventHookNotifier
import com.wire.kalium.network.networkContainer.AuthenticatedNetworkContainer
import com.wire.kalium.network.session.SessionManager
import com.wire.kalium.persistence.kmmSettings.GlobalPrefProvider
import com.wire.kalium.userstorage.di.UserStorage
import com.wire.kalium.util.DelicateKaliumApi
import com.wire.kalium.util.InternalKaliumApi
import com.wire.kalium.util.KaliumDispatcherImpl
import dev.zacsweers.metro.Provider
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import okio.Path.Companion.toPath

/** Explicit factory calls preserve omitted defaults, platform contributions and nontrivial construction arguments. */
@Suppress("TooManyFunctions", "LongParameterList", "LargeClass")
internal interface UserSessionFactoryBindings {
    @Provides
    @UserSessionBinding("accessTokenRefresher")
    fun userSessionProvideAccessTokenRefresher(
        @UserSessionBinding("accessTokenRepository")
        accessTokenRepository: Provider<AccessTokenRepository>,
        @UserSessionBinding("resourceAccess")
        resourceAccess: Provider<com.wire.kalium.util.AccountResourceAccess?>,
        @UserSessionBinding("sessionManager")
        sessionManager: Provider<SessionManager>,
    ): AccessTokenRefresher =
        AccessTokenRefresherImpl(
            repository = accessTokenRepository()
        ).guardedBy(resourceAccess()) { sessionManager().session()?.refreshToken }

    @Provides
    @UserSessionBinding("accessTokenRefresherFactory")
    fun userSessionProvideAccessTokenRefresherFactory(
        @UserSessionBinding("globalPreferences")
        globalPreferences: Provider<GlobalPrefProvider>,
        @UserSessionBinding("userId")
        userId: Provider<UserId>,
    ): AccessTokenRefresherFactory =
        AccessTokenRefresherFactoryImpl(
            userId = userId(),
            tokenStorage = globalPreferences().authTokenStorage
        )

    @Provides
    @UserSessionBinding("callSignalJournal")
    fun userSessionProvideCallSignalJournal(
        @UserSessionBinding("userStorage")
        userStorage: Provider<UserStorage>,
    ): CallSignalJournal =
        CallSignalJournal(userStorage().database)

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
    @UserSessionBinding("featureSupport")
    @SingleIn(UserLifetime::class)
    fun userSessionProvideFeatureSupport(
        @UserSessionBinding("sessionManager")
        sessionManager: Provider<SessionManager>,
    ): FeatureSupport =
        FeatureSupportImpl(
            sessionManager().serverConfig().metaData.commonApiVersion.version
        )

    @Provides
    @UserSessionBinding("fetchMLSOneToOneConversationUseCase")
    fun userSessionProvideFetchMLSOneToOneConversationUseCase(
        @UserSessionBinding("persistConversationsUseCase")
        persistConversationsUseCase: Provider<PersistConversationsUseCase>,
        @UserSessionBinding("conversationRepository")
        conversationRepository: Provider<ConversationRepository>,
        @UserSessionBinding("userId")
        userId: Provider<UserId>,
    ): FetchMLSOneToOneConversationUseCase =
        FetchMLSOneToOneConversationUseCaseImpl(
            selfUserId = userId(),
            conversationRepository = conversationRepository(),
            persistConversations = persistConversationsUseCase()
        )

    @Provides
    @UserSessionBinding("fetchUsersClientsFromRemote")
    fun userSessionProvideFetchUsersClientsFromRemote(
        @UserSessionBinding("clientRemoteRepository")
        clientRemoteRepository: Provider<ClientRemoteRepository>,
        @UserSessionBinding("clientRepository")
        clientRepository: Provider<ClientRepository>,
    ): FetchUsersClientsFromRemoteUseCase =
        FetchUsersClientsFromRemoteUseCaseImpl(
            clientRemoteRepository = clientRemoteRepository(),
            clientRepository = clientRepository()
        )

    @Provides
    @UserSessionBinding("getCurrentAnalyticsTrackingIdentifier")
    fun userSessionProvideGetCurrentAnalyticsTrackingIdentifier(
        @UserSessionBinding("userConfigRepository")
        userConfigRepository: Provider<UserConfigRepository>,
    ): GetCurrentAnalyticsTrackingIdentifierUseCase =
        GetCurrentAnalyticsTrackingIdentifierUseCase(userConfigRepository())

    @Provides
    @UserSessionBinding("getOtherUserSecurityClassificationLabel")
    fun userSessionProvideGetOtherUserSecurityClassificationLabel(
        @UserSessionBinding("userConfigRepository")
        userConfigRepository: Provider<UserConfigRepository>,
        @UserSessionBinding("userId")
        userId: Provider<UserId>,
    ): ObserveOtherUserSecurityClassificationLabelUseCase =
        ObserveOtherUserSecurityClassificationLabelUseCaseImpl(userConfigRepository(), userId())

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
    @UserSessionBinding("linkPreviewsResolver")
    @SingleIn(UserLifetime::class)
    fun userSessionProvideLinkPreviewsResolver(
        @UserSessionBinding("sessionCoroutineScope")
        sessionCoroutineScope: Provider<CoroutineScope>,
        @UserSessionBinding("messageRepository")
        messageRepository: Provider<MessageRepository>,
        @UserSessionBinding("assetRepository")
        assetRepository: Provider<AssetRepository>,
        @UserSessionBinding("kaliumConfigs")
        kaliumConfigs: Provider<KaliumConfigs>,
    ): LinkPreviewImagesResolver =
        LinkPreviewImagesResolverImpl(
            messageRepository = messageRepository(),
            assetRepository = assetRepository(),
            scope = sessionCoroutineScope(),
            dispatcher = KaliumDispatcherImpl,
            linkPreviewEnabled = kaliumConfigs().linkPreviewEnabled
        )

    @Provides
    @UserSessionBinding("logout")
    fun userSessionProvideLogout(
        @LegacyClientBinding("deregisterNativePushToken")
        clientDeregisterNativePushToken: Provider<DeregisterTokenUseCase>,
        @LegacyClientBinding("clearClientData")
        clientClearClientData: Provider<ClearClientDataUseCase>,
        @UserSessionBinding("freshEstablishedCalls")
        freshEstablishedCalls: Provider<ObserveEstablishedCallsUseCase>,
        @UserSessionBinding("freshEndCall")
        freshEndCall: Provider<EndCallUseCase>,
        @UserSessionBinding("userSessionScopeProvider")
        userSessionScopeProvider: Provider<UserSessionScopeProvider>,
        @UserSessionBinding("userSessionWorkScheduler")
        userSessionWorkScheduler: Provider<UserSessionWorkScheduler>,
        @UserSessionBinding("globalSessionRepository")
        globalSessionRepository: Provider<SessionRepository>,
        @UserSessionBinding("sessionCoroutineScope")
        sessionCoroutineScope: Provider<CoroutineScope>,
        @UserSessionBinding("globalCoroutineScope")
        globalCoroutineScope: Provider<CoroutineScope>,
        @UserSessionBinding("userConfigRepository")
        userConfigRepository: Provider<UserConfigRepository>,
        @UserSessionBinding("pushTokenRepository")
        pushTokenRepository: Provider<PushTokenRepository>,
        @UserSessionBinding("clientRepository")
        clientRepository: Provider<ClientRepository>,
        @UserSessionBinding("logoutRepository")
        logoutRepository: Provider<LogoutRepository>,
        @UserSessionBinding("nomadServiceUrl")
        nomadServiceUrl: Provider<String?>,
        @UserSessionBinding("logoutCallback")
        logoutCallback: Provider<LogoutCallback>,
        @UserSessionBinding("clearUserData")
        clearUserData: Provider<ClearUserDataUseCase>,
        @UserSessionBinding("kaliumConfigs")
        kaliumConfigs: Provider<KaliumConfigs>,
        @UserSessionBinding("userId")
        userId: Provider<UserId>,
    ): LogoutUseCase =
        LogoutUseCaseImpl(
            logoutRepository = logoutRepository(),
            sessionScope = sessionCoroutineScope(),
            sessionRepository = globalSessionRepository(),
            clientRepository = clientRepository(),
            userConfigRepository = userConfigRepository(),
            userId = userId(),
            deregisterTokenUseCase = clientDeregisterNativePushToken(),
            clearClientDataUseCase = clientClearClientData(),
            clearUserDataUseCase = clearUserData(),
            userSessionScopeProvider = userSessionScopeProvider(),
            pushTokenRepository = pushTokenRepository(),
            globalCoroutineScope = globalCoroutineScope(),
            userSessionWorkScheduler = userSessionWorkScheduler(),
            getEstablishedCallsUseCase = freshEstablishedCalls(),
            endCallUseCase = freshEndCall(),
            logoutCallback = logoutCallback(),
            kaliumConfigs = kaliumConfigs(),
            isNomadEnabled = { nomadServiceUrl()?.isNotBlank() == true },
        )

    @Provides
    @UserSessionBinding("messageApiPagingCoordinator")
    @SingleIn(UserLifetime::class)
    fun userSessionProvideMessageApiPagingCoordinator(
        @UserSessionBinding("nomadMessagePagingCoordinator")
        nomadMessagePagingCoordinator: Provider<NomadMessagePagingCoordinator?>,
    ): NomadMessagePagingCoordinator? =
        nomadMessagePagingCoordinator()

    @Provides
    @UserSessionBinding("messageEncoder")
    fun userSessionProvideMessageEncoder(): MessageContentEncoder =
        MessageContentEncoder()

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
    @UserSessionBinding("mlsRejectionConversationProtocolGetter")
    fun userSessionProvideMlsRejectionConversationProtocolGetter(
        @UserSessionBinding("userStorage")
        userStorage: Provider<UserStorage>,
    ): ConversationProtocolGetterImpl =
        ConversationProtocolGetterImpl(userStorage().database.conversationDAO)

    @Provides
    @UserSessionBinding("mlsUnpacker")
    fun userSessionProvideMlsUnpacker(
        @UserSessionBinding("mlsConversationRepository")
        mlsConversationRepository: Provider<MLSConversationRepository>,
        @UserSessionBinding("subconversationRepository")
        subconversationRepository: Provider<SubconversationRepositoryImpl>,
        @UserSessionBinding("pendingProposalScheduler")
        pendingProposalScheduler: Provider<PendingProposalScheduler>,
        @UserSessionBinding("conversationRepository")
        conversationRepository: Provider<ConversationRepository>,
        @UserSessionBinding("userId")
        userId: Provider<UserId>,
    ): MLSMessageUnpacker =
        MLSMessageUnpackerImpl(
            conversationRepository = conversationRepository(),
            subconversationRepository = subconversationRepository(),
            mlsConversationRepository = mlsConversationRepository(),
            pendingProposalScheduler = pendingProposalScheduler(),
            selfUserId = userId()
        )

    @Provides
    @UserSessionBinding("newGroupConversationSystemMessagesCreator")
    fun userSessionProvideNewGroupConversationSystemMessagesCreator(
        @UserSessionBinding("qualifiedIdMapper")
        qualifiedIdMapper: Provider<QualifiedIdMapper>,
        @UserSessionBinding("persistMessage")
        persistMessage: Provider<PersistMessageUseCase>,
        @UserSessionBinding("selfTeamId")
        selfTeamId: Provider<SelfTeamIdProvider>,
        @UserSessionBinding("userId")
        userId: Provider<UserId>,
    ): NewGroupConversationSystemMessagesCreator =
        NewGroupConversationSystemMessagesCreatorImpl(
            persistMessage = persistMessage(),
            selfTeamIdProvider = selfTeamId(),
            qualifiedIdMapper = qualifiedIdMapper(),
            selfUserId = userId()
        )

    @Provides
    @UserSessionBinding("nomadMessagePagingCoordinator")
    fun userSessionProvideNomadMessagePagingCoordinator(
        @UserSessionBinding("authenticatedNetworkContainer")
        authenticatedNetworkContainer: Provider<AuthenticatedNetworkContainer>,
        @UserSessionBinding("nomadServiceUrl")
        nomadServiceUrl: Provider<String?>,
        @UserSessionBinding("userStorage")
        userStorage: Provider<UserStorage>,
        @UserSessionBinding("userId")
        userId: Provider<UserId>,
    ): NomadMessagePagingCoordinator? =
        if (nomadServiceUrl().isNullOrBlank()) {
            null
        } else {
            NomadMessagePagingCoordinatorImpl(
                selfUserId = userId(),
                isNomadEnabled = { (nomadServiceUrl()?.isNotBlank() == true) },
                nomadDeviceSyncApi = authenticatedNetworkContainer().nomadDeviceSyncApi,
                nomadMessagesDAO = userStorage().database.nomadMessagesDAO,
            )
        }

    @Provides
    @UserSessionBinding("observeAnalyticsTrackingIdentifierStatus")
    fun userSessionProvideObserveAnalyticsTrackingIdentifierStatus(
        @UserSessionBinding("userConfigRepository")
        userConfigRepository: Provider<UserConfigRepository>,
        @UserSessionBinding("userScopedLogger")
        userScopedLogger: Provider<KaliumLogger>,
    ): ObserveAnalyticsTrackingIdentifierStatusUseCase =
        ObserveAnalyticsTrackingIdentifierStatusUseCase(userConfigRepository(), userScopedLogger())

    @Provides
    @UserSessionBinding("observeE2EIRequired")
    fun userSessionProvideObserveE2EIRequired(
        @LegacyUserBinding("getE2EICertificate")
        userGetE2EICertificate: Provider<GetMLSClientIdentityUseCase>,
        @UserSessionBinding("userConfigRepository")
        userConfigRepository: Provider<UserConfigRepository>,
        @UserSessionBinding("clientIdProvider")
        clientIdProvider: Provider<CurrentClientIdProvider>,
        @UserSessionBinding("featureSupport")
        featureSupport: Provider<FeatureSupport>,
    ): ObserveE2EIRequiredUseCase =
        ObserveE2EIRequiredUseCaseImpl(
            userConfigRepository(),
            featureSupport(),
            userGetE2EICertificate(),
            clientIdProvider()
        )

    @Provides
    @UserSessionBinding("observeSelfDeletingMessages")
    fun userSessionProvideObserveSelfDeletingMessages(
        @UserSessionBinding("conversationRepository")
        conversationRepository: Provider<ConversationRepository>,
        @UserSessionBinding("userConfigRepository")
        userConfigRepository: Provider<UserConfigRepository>,
    ): ObserveSelfDeletionTimerSettingsForConversationUseCase =
        ObserveSelfDeletionTimerSettingsForConversationUseCaseImpl(userConfigRepository(), conversationRepository())

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
    @UserSessionBinding("pendingConversationCleanup")
    fun userSessionProvidePendingConversationCleanup(
        @UserSessionBinding("kaliumFileSystem")
        kaliumFileSystem: Provider<KaliumFileSystem>,
        @UserSessionBinding("userStorage")
        userStorage: Provider<UserStorage>,
    ): PendingConversationCleanup =
        PendingConversationCleanup(userStorage().database) {
            kaliumFileSystem().delete(it.toPath(), mustExist = false)
        }

    @Provides
    @UserSessionBinding("pendingSubconversationProposals")
    @SingleIn(UserLifetime::class)
    fun userSessionProvidePendingSubconversationProposals(
        @UserSessionBinding("userStorage")
        userStorage: Provider<UserStorage>,
    ): PendingSubconversationProposals =
        PendingSubconversationProposalsImpl(userStorage().database.pendingSubconversationProposalDAO)

    @Provides
    @UserSessionBinding("persistConversationsUseCase")
    fun userSessionProvidePersistConversationsUseCase(
        @UserSessionBinding("conversationRepository")
        conversationRepository: Provider<ConversationRepository>,
        @UserSessionBinding("selfTeamId")
        selfTeamId: Provider<SelfTeamIdProvider>,
        @UserSessionBinding("userId")
        userId: Provider<UserId>,
    ): PersistConversationsUseCase =
        PersistConversationsUseCaseImpl(
            selfUserId = userId(),
            conversationRepository = conversationRepository(),
            selfTeamIdProvider = selfTeamId()
        )

    @Provides
    @UserSessionBinding("persistNewSelfDeletionStatus")
    fun userSessionProvidePersistNewSelfDeletionStatus(
        @UserSessionBinding("conversationRepository")
        conversationRepository: Provider<ConversationRepository>,
    ): PersistNewSelfDeletionTimerUseCase =
        PersistNewSelfDeletionTimerUseCaseImpl(conversationRepository())

    @Provides
    @UserSessionBinding("persistReaction")
    fun userSessionProvidePersistReaction(
        @UserSessionBinding("currentPersistenceEventHookNotifier")
        currentPersistenceEventHookNotifier: Provider<PersistenceEventHookNotifier>,
        @UserSessionBinding("reactionRepository")
        reactionRepository: Provider<ReactionRepositoryImpl>,
        @UserSessionBinding("userId")
        userId: Provider<UserId>,
    ): PersistReactionUseCase =
        PersistReactionUseCaseImpl(
            reactionRepository = reactionRepository(),
            selfUserId = userId(),
            persistenceEventHookNotifier = currentPersistenceEventHookNotifier(),
        )

    @Provides
    @UserSessionBinding("proteusPreKeyRefiller")
    fun userSessionProvideProteusPreKeyRefiller(
        @UserSessionBinding("preKeyRepository")
        preKeyRepository: Provider<PreKeyRepository>,
    ): ProteusPreKeyRefiller =
        ProteusPreKeyRefillerImpl(preKeyRepository())

    @Provides
    @UserSessionBinding("proteusUnpacker")
    fun userSessionProvideProteusUnpacker(
        @UserSessionBinding("userId")
        userId: Provider<UserId>,
    ): ProteusMessageUnpacker =
        ProteusMessageUnpackerImpl(
            selfUserId = userId()
        )

    @Provides
    @UserSessionBinding("reactionRepository")
    @SingleIn(UserLifetime::class)
    fun userSessionProvideReactionRepository(
        @UserSessionBinding("userStorage")
        userStorage: Provider<UserStorage>,
        @UserSessionBinding("userId")
        userId: Provider<UserId>,
    ): ReactionRepositoryImpl =
        ReactionRepositoryImpl(userId(), userStorage().database.reactionDAO)

    @Provides
    @UserSessionBinding("receiptRepository")
    @SingleIn(UserLifetime::class)
    fun userSessionProvideReceiptRepository(
        @UserSessionBinding("userStorage")
        userStorage: Provider<UserStorage>,
    ): ReceiptRepositoryImpl =
        ReceiptRepositoryImpl(userStorage().database.receiptDAO)

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
    @UserSessionBinding("refreshUsersWithoutMetadata")
    fun userSessionProvideRefreshUsersWithoutMetadata(
        @UserSessionBinding("userRepository")
        userRepository: Provider<UserRepository>,
    ): RefreshUsersWithoutMetadataUseCase =
        RefreshUsersWithoutMetadataUseCaseImpl(
            userRepository()
        )

    @Provides
    @UserSessionBinding("setNewUserTrackingIdentifier")
    fun userSessionProvideSetNewUserTrackingIdentifier(
        @UserSessionBinding("userConfigRepository")
        userConfigRepository: Provider<UserConfigRepository>,
    ): SetNewUserTrackingIdentifierUseCase =
        SetNewUserTrackingIdentifierUseCase(userConfigRepository())

    @Provides
    @UserSessionBinding("slowSyncWorkerOptimizeDatabaseUseCaseImpl")
    fun userSessionProvideSlowSyncWorkerOptimizeDatabaseUseCaseImpl(
        @UserSessionBinding("userStorage")
        userStorage: Provider<UserStorage>,
    ): OptimizeDatabaseUseCaseImpl =
        OptimizeDatabaseUseCaseImpl(userStorage().database.databaseOptimizer)

    @Provides
    @UserSessionBinding("staleEpochVerifier")
    fun userSessionProvideStaleEpochVerifier(
        @UserSessionBinding("joinExistingMLSConversationUseCase")
        joinExistingMLSConversationUseCase: Provider<JoinExistingMLSConversationUseCase>,
        @UserSessionBinding("mlsConversationRepository")
        mlsConversationRepository: Provider<MLSConversationRepository>,
        @UserSessionBinding("subconversationRepository")
        subconversationRepository: Provider<SubconversationRepositoryImpl>,
        @UserSessionBinding("fetchConversationUseCase")
        fetchConversationUseCase: Provider<FetchConversationUseCase>,
        @UserSessionBinding("conversationRepository")
        conversationRepository: Provider<ConversationRepository>,
        @UserSessionBinding("systemMessageInserter")
        systemMessageInserter: Provider<SystemMessageInserterImpl>,
    ): StaleEpochVerifier =
        StaleEpochVerifierImpl(
            systemMessageInserter = systemMessageInserter(),
            fetchConversationUseCase = fetchConversationUseCase(),
            conversationRepository = conversationRepository(),
            mlsConversationRepository = mlsConversationRepository(),
            joinExistingMLSConversation = joinExistingMLSConversationUseCase(),
            subconversationRepository = subconversationRepository()
        )

    @Provides
    @UserSessionBinding("subconversationRepository")
    @SingleIn(UserLifetime::class)
    fun userSessionProvideSubconversationRepository(
        @UserSessionBinding("authenticatedNetworkContainer")
        authenticatedNetworkContainer: Provider<AuthenticatedNetworkContainer>,
        @UserSessionBinding("userStorage")
        userStorage: Provider<UserStorage>,
    ): SubconversationRepositoryImpl =
        SubconversationRepositoryImpl(
            conversationApi = authenticatedNetworkContainer().conversationApi,
            subconversations = userStorage().database.subconversationDAO,
        )

    @Provides
    @UserSessionBinding("userScopedNomadHookFactory")
    @SingleIn(UserLifetime::class)
    fun userSessionProvideUserScopedNomadHookFactory(): UserScopedNomadHookFactory =
        UserScopedNomadHookFactory()

    @Provides
    @UserSessionBinding("userSearchApiWrapper")
    @SingleIn(UserLifetime::class)
    fun userSessionProvideUserSearchApiWrapper(
        @UserSessionBinding("authenticatedNetworkContainer")
        authenticatedNetworkContainer: Provider<AuthenticatedNetworkContainer>,
        @UserSessionBinding("userStorage")
        userStorage: Provider<UserStorage>,
        @UserSessionBinding("userId")
        userId: Provider<UserId>,
    ): UserSearchApiWrapper =
        UserSearchApiWrapperImpl(
            authenticatedNetworkContainer().userSearchApi,
            userStorage().database.memberDAO,
            userId()
        )

    @Provides
    @UserSessionBinding("validateAssetMimeType")
    fun userSessionProvideValidateAssetMimeType(): ValidateAssetFileTypeUseCase =
        ValidateAssetFileTypeUseCaseImpl()

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
    @UserSessionBinding("markFileSharingStatusAsNotified")
    fun userSessionProvideMarkFileSharingStatusAsNotified(
        @UserSessionBinding("userConfigRepository")
        userConfigRepository: Provider<UserConfigRepository>,
    ): MarkFileSharingChangeAsNotifiedUseCase =
        MarkFileSharingChangeAsNotifiedUseCase(userConfigRepository())

}
