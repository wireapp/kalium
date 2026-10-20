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
import com.wire.kalium.logic.configuration.ClientConfig
import com.wire.kalium.logic.configuration.UserConfigDataSource
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.configuration.notification.NotificationTokenDataSource
import com.wire.kalium.logic.data.analytics.AnalyticsDataSource
import com.wire.kalium.logic.data.analytics.AnalyticsRepository
import com.wire.kalium.logic.data.app.AppDataSource
import com.wire.kalium.logic.data.app.AppRepository
import com.wire.kalium.logic.data.asset.AssetAuditFeatureHandler
import com.wire.kalium.logic.data.asset.AssetDataSource
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.asset.DataStoragePaths
import com.wire.kalium.logic.data.asset.KaliumFileSystem
import com.wire.kalium.logic.data.asset.KaliumFileSystemImpl
import com.wire.kalium.logic.data.backup.BackupDataSource
import com.wire.kalium.logic.data.backup.BackupRepository
import com.wire.kalium.logic.data.backup.CryptoStateBackupRemoteDataSource
import com.wire.kalium.logic.data.backup.CryptoStateBackupRemoteRepository
import com.wire.kalium.logic.data.call.CallDataSource
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.call.mapper.CallMapper
import com.wire.kalium.logic.data.client.ClientDataSource
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.client.CryptoTransactionProvider
import com.wire.kalium.logic.data.client.MLSClientProvider
import com.wire.kalium.logic.data.client.MLSTransportProvider
import com.wire.kalium.logic.data.client.MLSTransportProviderImpl
import com.wire.kalium.logic.data.client.ProteusClientProvider
import com.wire.kalium.logic.data.client.X509CredentialAcquisitionConfigProvider
import com.wire.kalium.logic.data.client.remote.ClientRemoteDataSource
import com.wire.kalium.logic.data.client.remote.ClientRemoteRepository
import com.wire.kalium.logic.data.connection.ConnectionDataSource
import com.wire.kalium.logic.data.connection.ConnectionRepository
import com.wire.kalium.logic.data.conversation.ConversationDataSource
import com.wire.kalium.logic.data.conversation.ConversationGroupRepository
import com.wire.kalium.logic.data.conversation.ConversationGroupRepositoryImpl
import com.wire.kalium.logic.data.conversation.ConversationMetaDataDataSource
import com.wire.kalium.logic.data.conversation.ConversationMetaDataRepository
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.EpochChangesObserver
import com.wire.kalium.logic.data.conversation.JoinSubconversationUseCase
import com.wire.kalium.logic.data.conversation.LeaveSubconversationUseCase
import com.wire.kalium.logic.data.conversation.MLSConversationDataSource
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.conversation.NewConversationMembersRepository
import com.wire.kalium.logic.data.conversation.NewConversationMembersRepositoryImpl
import com.wire.kalium.logic.data.conversation.NewGroupConversationSystemMessagesCreator
import com.wire.kalium.logic.data.conversation.ObservableMLSConversationRepository
import com.wire.kalium.logic.data.conversation.PersistConversationsUseCase
import com.wire.kalium.logic.data.conversation.ProposalTimer
import com.wire.kalium.logic.data.conversation.SubconversationRepositoryImpl
import com.wire.kalium.logic.data.conversation.folders.ConversationFolderDataSource
import com.wire.kalium.logic.data.conversation.folders.ConversationFolderRepository
import com.wire.kalium.logic.data.conversation.mls.PendingActionsRepository
import com.wire.kalium.logic.data.conversation.mls.PersistentPendingActionsRepository
import com.wire.kalium.logic.data.e2ei.CertificateRevocationListRepository
import com.wire.kalium.logic.data.e2ei.CertificateRevocationListRepositoryDataSource
import com.wire.kalium.logic.data.e2ei.E2EIRepository
import com.wire.kalium.logic.data.e2ei.E2EIRepositoryImpl
import com.wire.kalium.logic.data.event.EventDataSource
import com.wire.kalium.logic.data.event.EventRepository
import com.wire.kalium.logic.data.featureConfig.FeatureConfigDataSource
import com.wire.kalium.logic.data.featureConfig.FeatureConfigRepository
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.id.FederatedIdMapper
import com.wire.kalium.logic.data.id.QualifiedIdMapper
import com.wire.kalium.logic.data.id.SelfTeamIdProvider
import com.wire.kalium.logic.data.keypackage.KeyPackageDataSource
import com.wire.kalium.logic.data.keypackage.KeyPackageLimitsProvider
import com.wire.kalium.logic.data.keypackage.KeyPackageRepository
import com.wire.kalium.logic.data.logout.LogoutDataSource
import com.wire.kalium.logic.data.logout.LogoutRepository
import com.wire.kalium.logic.data.meeting.MeetingDataSource
import com.wire.kalium.logic.data.meeting.MeetingRepository
import com.wire.kalium.logic.data.message.CompositeMessageDataSource
import com.wire.kalium.logic.data.message.CompositeMessageRepository
import com.wire.kalium.logic.data.message.MessageMetadataRepository
import com.wire.kalium.logic.data.message.MessageMetadataSource
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.message.PendingSubconversationProposals
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.message.PollMessageDataSource
import com.wire.kalium.logic.data.message.PollMessageRepository
import com.wire.kalium.logic.data.message.ProtoContentMapper
import com.wire.kalium.logic.data.message.ProtoContentMapperImpl
import com.wire.kalium.logic.data.message.draft.MessageDraftDataSource
import com.wire.kalium.logic.data.message.draft.MessageDraftRepository
import com.wire.kalium.logic.data.message.paging.NomadMessagePagingCoordinator
import com.wire.kalium.logic.data.mlspublickeys.MLSPublicKeysRepository
import com.wire.kalium.logic.data.mlspublickeys.MLSPublicKeysRepositoryImpl
import com.wire.kalium.logic.data.notification.PushTokenDataSource
import com.wire.kalium.logic.data.notification.PushTokenRepository
import com.wire.kalium.logic.data.prekey.PreKeyDataSource
import com.wire.kalium.logic.data.prekey.PreKeyRepository
import com.wire.kalium.logic.data.properties.ConversationFoldersPropertyDataSource
import com.wire.kalium.logic.data.properties.LinkPreviewsPropertyDataSource
import com.wire.kalium.logic.data.properties.ReadReceiptsPropertyDataSource
import com.wire.kalium.logic.data.properties.ScreenshotCensoringPropertyDataSource
import com.wire.kalium.logic.data.properties.TypingIndicatorPropertyDataSource
import com.wire.kalium.logic.data.properties.UserPropertiesSyncDataSource
import com.wire.kalium.logic.data.properties.UserPropertyDataSource
import com.wire.kalium.logic.data.properties.UserPropertyRepository
import com.wire.kalium.logic.data.publicuser.SearchUserRepository
import com.wire.kalium.logic.data.publicuser.SearchUserRepositoryImpl
import com.wire.kalium.logic.data.publicuser.UserSearchApiWrapper
import com.wire.kalium.logic.data.service.ServiceDataSource
import com.wire.kalium.logic.data.service.ServiceRepository
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.session.token.AccessTokenRepository
import com.wire.kalium.logic.data.session.token.AccessTokenRepositoryImpl
import com.wire.kalium.logic.data.team.TeamDataSource
import com.wire.kalium.logic.data.team.TeamRepository
import com.wire.kalium.logic.data.user.AccountRepository
import com.wire.kalium.logic.data.user.AccountRepositoryImpl
import com.wire.kalium.logic.data.user.UserDataSource
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.user.LegacyUserBinding
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.sync.local.LocalEventRepository
import com.wire.kalium.logic.sync.receiver.conversation.ConversationMessageTimerEventHandler
import com.wire.kalium.logic.sync.receiver.handler.legalhold.LegalHoldHandlerImpl
import com.wire.kalium.logic.sync.receiver.handler.legalhold.LegalHoldRequestHandlerImpl
import com.wire.kalium.logic.sync.slow.RestartSlowSyncProcessForRecoveryUseCase
import com.wire.kalium.messaging.hooks.CryptoStateChangeHookNotifier
import com.wire.kalium.network.networkContainer.AuthenticatedNetworkContainer
import com.wire.kalium.network.networkContainer.UnboundNetworkContainer
import com.wire.kalium.persistence.client.ClientRegistrationStorage
import com.wire.kalium.persistence.client.ClientRegistrationStorageImpl
import com.wire.kalium.persistence.kmmSettings.GlobalPrefProvider
import com.wire.kalium.userstorage.di.UserStorage
import com.wire.kalium.util.DelicateKaliumApi
import com.wire.kalium.util.InternalKaliumApi
import dev.zacsweers.metro.Provider
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.sync.Mutex
import okio.Path.Companion.toPath

/** Resource projections and repository factories preserve repeated getter reads and external provider ownership. */
@Suppress("TooManyFunctions", "LongParameterList", "LargeClass")
internal interface UserSessionResourceBindings {
    @Provides
    @UserSessionBinding("accessTokenRepository")
    fun userSessionProvideAccessTokenRepository(
        @UserSessionBinding("authenticatedNetworkContainer")
        authenticatedNetworkContainer: Provider<AuthenticatedNetworkContainer>,
        @UserSessionBinding("globalPreferences")
        globalPreferences: Provider<GlobalPrefProvider>,
        @UserSessionBinding("userId")
        userId: Provider<UserId>,
    ): AccessTokenRepository =
        AccessTokenRepositoryImpl(
            userId = userId(),
            accessTokenApi = authenticatedNetworkContainer().accessTokenApi,
            authTokenStorage = globalPreferences().authTokenStorage
        )

    @Provides
    @UserSessionBinding("accountRepository")
    fun userSessionProvideAccountRepository(
        @UserSessionBinding("authenticatedNetworkContainer")
        authenticatedNetworkContainer: Provider<AuthenticatedNetworkContainer>,
        @UserSessionBinding("userStorage")
        userStorage: Provider<UserStorage>,
        @UserSessionBinding("userId")
        userId: Provider<UserId>,
    ): AccountRepository =
        AccountRepositoryImpl(
            userDAO = userStorage().database.userDAO,
            selfUserId = userId(),
            selfApi = authenticatedNetworkContainer().selfApi
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
    @UserSessionBinding("appRepository")
    fun userSessionProvideAppRepository(
        @UserSessionBinding("authenticatedNetworkContainer")
        authenticatedNetworkContainer: Provider<AuthenticatedNetworkContainer>,
        @UserSessionBinding("userStorage")
        userStorage: Provider<UserStorage>,
        @UserSessionBinding("userId")
        userId: Provider<UserId>,
    ): AppRepository =
        AppDataSource(
            selfUserId = userId(),
            appDAO = userStorage().database.appDAO,
            teamDAO = userStorage().database.teamDAO,
            teamsApi = authenticatedNetworkContainer().teamsApi,
            userDetailsApi = authenticatedNetworkContainer().userDetailsApi,
        )

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
    @UserSessionBinding("backupRepository")
    fun userSessionProvideBackupRepository(
        @UserSessionBinding("userStorage")
        userStorage: Provider<UserStorage>,
        @UserSessionBinding("userId")
        userId: Provider<UserId>,
    ): BackupRepository =
        BackupDataSource(
            selfUserId = userId(),
            userDAO = userStorage().database.userDAO,
            conversationDAO = userStorage().database.conversationDAO,
            messageDAO = userStorage().database.messageDAO,
            reactionDAO = userStorage().database.reactionDAO,
        )

    @Provides
    @UserSessionBinding("callMapper")
    fun userSessionProvideCallMapper(
        @UserSessionBinding("userId")
        userId: Provider<UserId>,
    ): CallMapper =
        MapperProvider.callMapper(userId())

    @Provides
    @UserSessionBinding("callRepository")
    @SingleIn(UserLifetime::class)
    fun userSessionProvideCallRepository(
        @UserSessionBinding("pendingSubconversationProposals")
        pendingSubconversationProposals: Provider<PendingSubconversationProposals>,
        @UserSessionBinding("authenticatedNetworkContainer")
        authenticatedNetworkContainer: Provider<AuthenticatedNetworkContainer>,
        @UserSessionBinding("leaveSubconversationUseCase")
        leaveSubconversationUseCase: Provider<LeaveSubconversationUseCase>,
        @UserSessionBinding("joinSubconversationUseCase")
        joinSubconversationUseCase: Provider<JoinSubconversationUseCase>,
        @UserSessionBinding("cryptoTransactionProvider")
        cryptoTransactionProvider: Provider<CryptoTransactionProvider>,
        @UserSessionBinding("mlsConversationRepository")
        mlsConversationRepository: Provider<MLSConversationRepository>,
        @UserSessionBinding("subconversationRepository")
        subconversationRepository: Provider<SubconversationRepositoryImpl>,
        @UserSessionBinding("conversationRepository")
        conversationRepository: Provider<ConversationRepository>,
        @UserSessionBinding("epochChangesObserver")
        epochChangesObserver: Provider<EpochChangesObserver>,
        @UserSessionBinding("federatedIdMapper")
        federatedIdMapper: Provider<FederatedIdMapper>,
        @UserSessionBinding("qualifiedIdMapper")
        qualifiedIdMapper: Provider<QualifiedIdMapper>,
        @UserSessionBinding("persistMessage")
        persistMessage: Provider<PersistMessageUseCase>,
        @UserSessionBinding("teamRepository")
        teamRepository: Provider<TeamRepository>,
        @UserSessionBinding("userRepository")
        userRepository: Provider<UserRepository>,
        @UserSessionBinding("userStorage")
        userStorage: Provider<UserStorage>,
        @UserSessionBinding("callMapper")
        callMapper: Provider<CallMapper>,
    ): CallRepository =
        CallDataSource(
            callApi = authenticatedNetworkContainer().callApi,
            sftApi = authenticatedNetworkContainer().sftApi,
            serverTimeApi = authenticatedNetworkContainer().serverTimeApi,
            qualifiedIdMapper = qualifiedIdMapper(),
            callDAO = userStorage().database.callDAO,
            conversationRepository = conversationRepository(),
            mlsConversationRepository = mlsConversationRepository(),
            subconversationRepository = subconversationRepository(),
            joinSubconversation = joinSubconversationUseCase(),
            leaveSubconversation = leaveSubconversationUseCase(),
            subgroupProposals = pendingSubconversationProposals(),
            userRepository = userRepository(),
            epochChangesObserver = epochChangesObserver(),
            teamRepository = teamRepository(),
            persistMessage = persistMessage(),
            callMapper = callMapper(),
            federatedIdMapper = federatedIdMapper(),
            transactionProvider = cryptoTransactionProvider()
        )

    @Provides
    @UserSessionBinding("certificateRevocationListRepository")
    fun userSessionProvideCertificateRevocationListRepository(
        @UserSessionBinding("unboundNetworkContainer")
        unboundNetworkContainer: Provider<UnboundNetworkContainer>,
        @UserSessionBinding("userConfigRepository")
        userConfigRepository: Provider<UserConfigRepository>,
        @UserSessionBinding("userStorage")
        userStorage: Provider<UserStorage>,
    ): CertificateRevocationListRepository =
        CertificateRevocationListRepositoryDataSource(
            acmeApi = unboundNetworkContainer().acmeApi,
            metadataDAO = userStorage().database.metadataDAO,
            userConfigRepository = userConfigRepository()
        )

    @Provides
    @UserSessionBinding("clientRegistrationStorage")
    fun userSessionProvideClientRegistrationStorage(
        @UserSessionBinding("userStorage")
        userStorage: Provider<UserStorage>,
    ): ClientRegistrationStorage =
        ClientRegistrationStorageImpl(userStorage().database.metadataDAO)

    @Provides
    @UserSessionBinding("clientRemoteRepository")
    fun userSessionProvideClientRemoteRepository(
        @UserSessionBinding("authenticatedNetworkContainer")
        authenticatedNetworkContainer: Provider<AuthenticatedNetworkContainer>,
        @UserSessionBinding("clientConfig")
        clientConfig: Provider<ClientConfig>,
    ): ClientRemoteRepository =
        ClientRemoteDataSource(
            authenticatedNetworkContainer().clientApi,
            clientConfig()
        )

    @Provides
    @UserSessionBinding("clientRepository")
    fun userSessionProvideClientRepository(
        @UserSessionBinding("authenticatedNetworkContainer")
        authenticatedNetworkContainer: Provider<AuthenticatedNetworkContainer>,
        @UserSessionBinding("clientRegistrationStorage")
        clientRegistrationStorage: Provider<ClientRegistrationStorage>,
        @UserSessionBinding("clientRemoteRepository")
        clientRemoteRepository: Provider<ClientRemoteRepository>,
        @UserSessionBinding("userStorage")
        userStorage: Provider<UserStorage>,
        @UserSessionBinding("userId")
        userId: Provider<UserId>,
    ): ClientRepository =
        ClientDataSource(
            clientRemoteRepository(),
            clientRegistrationStorage(),
            userStorage().database.clientDAO,
            userStorage().database.newClientDAO,
            userId(),
            authenticatedNetworkContainer().clientApi,
        )

    @Provides
    @UserSessionBinding("compositeMessageRepository")
    fun userSessionProvideCompositeMessageRepository(
        @UserSessionBinding("userStorage")
        userStorage: Provider<UserStorage>,
    ): CompositeMessageRepository =
        CompositeMessageDataSource(compositeMessageDAO = userStorage().database.compositeMessageDAO)

    @Provides
    @UserSessionBinding("connectionRepository")
    fun userSessionProvideConnectionRepository(
        @UserSessionBinding("authenticatedNetworkContainer")
        authenticatedNetworkContainer: Provider<AuthenticatedNetworkContainer>,
        @UserSessionBinding("persistConversationsUseCase")
        persistConversationsUseCase: Provider<PersistConversationsUseCase>,
        @UserSessionBinding("conversationRepository")
        conversationRepository: Provider<ConversationRepository>,
        @UserSessionBinding("userStorage")
        userStorage: Provider<UserStorage>,
    ): ConnectionRepository =
        ConnectionDataSource(
            userStorage().database.conversationDAO,
            userStorage().database.memberDAO,
            userStorage().database.connectionDAO,
            authenticatedNetworkContainer().connectionApi,
            userStorage().database.userDAO,
            conversationRepository(),
            persistConversationsUseCase()
        )

    @Provides
    @UserSessionBinding("conversationFolderRepository")
    fun userSessionProvideConversationFolderRepository(
        @UserSessionBinding("authenticatedNetworkContainer")
        authenticatedNetworkContainer: Provider<AuthenticatedNetworkContainer>,
        @UserSessionBinding("userStorage")
        userStorage: Provider<UserStorage>,
        @UserSessionBinding("userId")
        userId: Provider<UserId>,
    ): ConversationFolderRepository =
        ConversationFolderDataSource(
            userStorage().database.conversationFolderDAO,
            authenticatedNetworkContainer().propertiesApi,
            userId()
        )

    @Provides
    @UserSessionBinding("conversationGroupRepository")
    fun userSessionProvideConversationGroupRepository(
        @UserSessionBinding("newGroupConversationSystemMessagesCreator")
        newGroupConversationSystemMessagesCreator: Provider<NewGroupConversationSystemMessagesCreator>,
        @UserSessionBinding("conversationMessageTimerEventHandler")
        conversationMessageTimerEventHandler: Provider<ConversationMessageTimerEventHandler>,
        @UserSessionBinding("newConversationMembersRepository")
        newConversationMembersRepository: Provider<NewConversationMembersRepository>,
        @UserSessionBinding("authenticatedNetworkContainer")
        authenticatedNetworkContainer: Provider<AuthenticatedNetworkContainer>,
        @UserSessionBinding("cryptoTransactionProvider")
        cryptoTransactionProvider: Provider<CryptoTransactionProvider>,
        @UserSessionBinding("mlsConversationRepository")
        mlsConversationRepository: Provider<MLSConversationRepository>,
        @UserSessionBinding("pendingActionsRepository")
        pendingActionsRepository: Provider<PendingActionsRepository>,
        @UserSessionBinding("localEventRepository")
        localEventRepository: Provider<LocalEventRepository>,
        @UserSessionBinding("legalHoldHandler")
        legalHoldHandler: Provider<LegalHoldHandlerImpl>,
        @UserSessionBinding("userRepository")
        userRepository: Provider<UserRepository>,
        @UserSessionBinding("userStorage")
        userStorage: Provider<UserStorage>,
        @UserSessionBinding("selfTeamId")
        selfTeamId: Provider<SelfTeamIdProvider>,
        @UserSessionBinding("userId")
        userId: Provider<UserId>,
    ): ConversationGroupRepository =
        ConversationGroupRepositoryImpl(
            mlsConversationRepository(),
            localEventRepository(),
            conversationMessageTimerEventHandler(),
            userStorage().database.conversationDAO,
            authenticatedNetworkContainer().conversationApi,
            newConversationMembersRepository(),
            userRepository(),
            lazy { newGroupConversationSystemMessagesCreator() },
            userId(),
            selfTeamId(),
            legalHoldHandler(),
            cryptoTransactionProvider(),
            pendingActionsRepository(),
        )

    @Provides
    @UserSessionBinding("conversationMetaDataRepository")
    fun userSessionProvideConversationMetaDataRepository(
        @UserSessionBinding("userStorage")
        userStorage: Provider<UserStorage>,
    ): ConversationMetaDataRepository =
        ConversationMetaDataDataSource(
            userStorage().database.conversationMetaDataDAO,
        )

    @Provides
    @UserSessionBinding("conversationRepository")
    fun userSessionProvideConversationRepository(
        @UserSessionBinding("authenticatedNetworkContainer")
        authenticatedNetworkContainer: Provider<AuthenticatedNetworkContainer>,
        @UserSessionBinding("userStorage")
        userStorage: Provider<UserStorage>,
        @UserSessionBinding("userId")
        userId: Provider<UserId>,
    ): ConversationRepository =
        ConversationDataSource(
            userId(),
            userStorage().database.conversationDAO,
            userStorage().database.memberDAO,
            authenticatedNetworkContainer().conversationApi,
            userStorage().database.messageDAO,
            userStorage().database.messageDraftDAO,
            userStorage().database.clientDAO,
            authenticatedNetworkContainer().clientApi,
            userStorage().database.conversationMetaDataDAO,
        )

    @Provides
    @UserSessionBinding("cryptoStateBackupRemoteRepository")
    fun userSessionProvideCryptoStateBackupRemoteRepository(
        @UserSessionBinding("authenticatedNetworkContainer")
        authenticatedNetworkContainer: Provider<AuthenticatedNetworkContainer>,
    ): CryptoStateBackupRemoteRepository =
        CryptoStateBackupRemoteDataSource(authenticatedNetworkContainer().nomadDeviceSyncApi)

    @Provides
    @UserSessionBinding("e2eiRepository")
    fun userSessionProvideE2eiRepository(
        @UserSessionBinding("x509CredentialAcquisitionConfigProvider")
        x509CredentialAcquisitionConfigProvider: Provider<X509CredentialAcquisitionConfigProvider>,
        @UserSessionBinding("currentCryptoStateChangeHookNotifier")
        currentCryptoStateChangeHookNotifier: Provider<CryptoStateChangeHookNotifier>,
        @UserSessionBinding("authenticatedNetworkContainer")
        authenticatedNetworkContainer: Provider<AuthenticatedNetworkContainer>,
        @UserSessionBinding("mlsConversationRepository")
        mlsConversationRepository: Provider<MLSConversationRepository>,
        @UserSessionBinding("unboundNetworkContainer")
        unboundNetworkContainer: Provider<UnboundNetworkContainer>,
        @UserSessionBinding("userConfigRepository")
        userConfigRepository: Provider<UserConfigRepository>,
        @UserSessionBinding("mlsClientProvider")
        mlsClientProvider: Provider<MLSClientProvider>,
        @UserSessionBinding("clientIdProvider")
        clientIdProvider: Provider<CurrentClientIdProvider>,
        @UserSessionBinding("userId")
        userId: Provider<UserId>,
    ): E2EIRepository =
        E2EIRepositoryImpl(
            authenticatedNetworkContainer().e2eiApi,
            unboundNetworkContainer().acmeApi,
            unboundNetworkContainer().cellsClient,
            x509CredentialAcquisitionConfigProvider(),
            mlsClientProvider(),
            clientIdProvider(),
            mlsConversationRepository(),
            userConfigRepository(),
            userId(),
            currentCryptoStateChangeHookNotifier()
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
    @UserSessionBinding("featureConfigRepository")
    fun userSessionProvideFeatureConfigRepository(
        @UserSessionBinding("authenticatedNetworkContainer")
        authenticatedNetworkContainer: Provider<AuthenticatedNetworkContainer>,
    ): FeatureConfigRepository =
        FeatureConfigDataSource(
            featureConfigApi = authenticatedNetworkContainer().featureConfigApi
        )

    @Provides
    @UserSessionBinding("federatedIdMapper")
    fun userSessionProvideFederatedIdMapper(
        @UserSessionBinding("globalSessionRepository")
        globalSessionRepository: Provider<SessionRepository>,
        @UserSessionBinding("qualifiedIdMapper")
        qualifiedIdMapper: Provider<QualifiedIdMapper>,
        @UserSessionBinding("userId")
        userId: Provider<UserId>,
    ): FederatedIdMapper =
        MapperProvider.federatedIdMapper(
            userId(),
            qualifiedIdMapper(),
            globalSessionRepository()
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
    @UserSessionBinding("keyPackageRepository")
    fun userSessionProvideKeyPackageRepository(
        @UserSessionBinding("authenticatedNetworkContainer")
        authenticatedNetworkContainer: Provider<AuthenticatedNetworkContainer>,
        @UserSessionBinding("clientIdProvider")
        clientIdProvider: Provider<CurrentClientIdProvider>,
        @UserSessionBinding("userId")
        userId: Provider<UserId>,
    ): KeyPackageRepository =
        KeyPackageDataSource(
            clientIdProvider(),
            authenticatedNetworkContainer().keyPackageApi,
            userId()
        )

    @Provides
    @UserSessionBinding("logoutRepository")
    @SingleIn(UserLifetime::class)
    fun userSessionProvideLogoutRepository(
        @UserSessionBinding("authenticatedNetworkContainer")
        authenticatedNetworkContainer: Provider<AuthenticatedNetworkContainer>,
        @UserSessionBinding("userStorage")
        userStorage: Provider<UserStorage>,
    ): LogoutRepository =
        LogoutDataSource(
            authenticatedNetworkContainer().logoutApi,
            userStorage().database.metadataDAO,
            userStorage().database.pendingSubconversationProposalDAO,
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
    @UserSessionBinding("messageApi")
    fun userSessionProvideMessageApi(
        @UserSessionBinding("authenticatedNetworkContainer")
        authenticatedNetworkContainer: Provider<AuthenticatedNetworkContainer>,
    ): com.wire.kalium.network.api.base.authenticated.message.MessageApi =
        authenticatedNetworkContainer().messageApi

    @Provides
    @UserSessionBinding("messageDao")
    fun userSessionProvideMessageDao(
        @UserSessionBinding("userStorage")
        userStorage: Provider<UserStorage>,
    ): com.wire.kalium.persistence.dao.message.MessageDAO =
        userStorage().database.messageDAO

    @Provides
    @UserSessionBinding("messageDraftRepository")
    @SingleIn(UserLifetime::class)
    fun userSessionProvideMessageDraftRepository(
        @UserSessionBinding("userStorage")
        userStorage: Provider<UserStorage>,
    ): MessageDraftRepository =
        MessageDraftDataSource(messageDraftDAO = userStorage().database.messageDraftDAO)

    @Provides
    @UserSessionBinding("messageMetadataRepository")
    fun userSessionProvideMessageMetadataRepository(
        @UserSessionBinding("userStorage")
        userStorage: Provider<UserStorage>,
    ): MessageMetadataRepository =
        MessageMetadataSource(messageMetaDataDAO = userStorage().database.messageMetaDataDAO)

    @Provides
    @UserSessionBinding("messageRepository")
    fun userSessionProvideMessageRepository(
        @UserSessionBinding("nomadMessagePagingCoordinator")
        nomadMessagePagingCoordinator: Provider<NomadMessagePagingCoordinator?>,
        @UserSessionBinding("messageRepositoryFactory")
        messageRepositoryFactory: Provider<UserSessionMessageRepositoryFactory>,
    ): MessageRepository =
        messageRepositoryFactory().create(nomadMessagePagingCoordinator())

    @Provides
    @UserSessionBinding("mlsConversationRepository")
    fun userSessionProvideMlsConversationRepository(
        @UserSessionBinding("mlsConversationRepositoryMLSConversationDataSource")
        mlsConversationRepositoryMLSConversationDataSource: Provider<MLSConversationDataSource>,
        @UserSessionBinding("currentCryptoStateChangeHookNotifier")
        currentCryptoStateChangeHookNotifier: Provider<CryptoStateChangeHookNotifier>,
        @UserSessionBinding("userId")
        userId: Provider<UserId>,
    ): MLSConversationRepository =
        ObservableMLSConversationRepository(
            delegate = mlsConversationRepositoryMLSConversationDataSource(),
            userId = userId(),
            hookNotifier = currentCryptoStateChangeHookNotifier()
        )

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
    @UserSessionBinding("mlsMessageApi")
    fun userSessionProvideMlsMessageApi(
        @UserSessionBinding("authenticatedNetworkContainer")
        authenticatedNetworkContainer: Provider<AuthenticatedNetworkContainer>,
    ): com.wire.kalium.network.api.base.authenticated.message.MLSMessageApi =
        authenticatedNetworkContainer().mlsMessageApi

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
    @UserSessionBinding("mlsTransportProvider")
    @SingleIn(UserLifetime::class)
    fun userSessionProvideMlsTransportProvider(
        @UserSessionBinding("authenticatedNetworkContainer")
        authenticatedNetworkContainer: Provider<AuthenticatedNetworkContainer>,
        @UserSessionBinding("localEventRepository")
        localEventRepository: Provider<LocalEventRepository>,
        @UserSessionBinding("userId")
        userId: Provider<UserId>,
    ): MLSTransportProvider =
        MLSTransportProviderImpl(
            selfUserId = userId(),
            mlsMessageApi = authenticatedNetworkContainer().mlsMessageApi,
            localEventRepository = localEventRepository()
        )

    @Provides
    @UserSessionBinding("newConversationMembersRepository")
    fun userSessionProvideNewConversationMembersRepository(
        @UserSessionBinding("newGroupConversationSystemMessagesCreator")
        newGroupConversationSystemMessagesCreator: Provider<NewGroupConversationSystemMessagesCreator>,
        @UserSessionBinding("userStorage")
        userStorage: Provider<UserStorage>,
    ): NewConversationMembersRepository =
        NewConversationMembersRepositoryImpl(
            userStorage().database.memberDAO,
            lazy { newGroupConversationSystemMessagesCreator() }
        )

    @Provides
    @UserSessionBinding("notificationTokenRepository")
    fun userSessionProvideNotificationTokenRepository(
        @UserSessionBinding("globalPreferences")
        globalPreferences: Provider<GlobalPrefProvider>,
    ): NotificationTokenDataSource =
        NotificationTokenDataSource(globalPreferences().tokenStorage)

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
    @UserSessionBinding("pollMessageRepository")
    fun userSessionProvidePollMessageRepository(
        @UserSessionBinding("userStorage")
        userStorage: Provider<UserStorage>,
    ): PollMessageRepository =
        PollMessageDataSource(pollMessageDAO = userStorage().database.pollMessageDAO)

    @Provides
    @UserSessionBinding("preKeyRepository")
    fun userSessionProvidePreKeyRepository(
        @UserSessionBinding("authenticatedNetworkContainer")
        authenticatedNetworkContainer: Provider<AuthenticatedNetworkContainer>,
        @UserSessionBinding("proteusClientProvider")
        proteusClientProvider: Provider<ProteusClientProvider>,
        @UserSessionBinding("clientIdProvider")
        clientIdProvider: Provider<CurrentClientIdProvider>,
        @UserSessionBinding("userStorage")
        userStorage: Provider<UserStorage>,
    ): PreKeyRepository =
        PreKeyDataSource(
            authenticatedNetworkContainer().preKeyApi,
            proteusClientProvider(),
            clientIdProvider(),
            userStorage().database.prekeyDAO,
            userStorage().database.clientDAO,
            userStorage().database.metadataDAO,
        )

    @Provides
    @UserSessionBinding("protoContentMapper")
    fun userSessionProvideProtoContentMapper(
        @UserSessionBinding("userId")
        userId: Provider<UserId>,
    ): ProtoContentMapper =
        ProtoContentMapperImpl(selfUserId = userId())

    @Provides
    @UserSessionBinding("pushTokenRepository")
    fun userSessionProvidePushTokenRepository(
        @UserSessionBinding("userStorage")
        userStorage: Provider<UserStorage>,
    ): PushTokenRepository =
        PushTokenDataSource(userStorage().database.metadataDAO)

    @Provides
    @UserSessionBinding("qualifiedIdMapper")
    fun userSessionProvideQualifiedIdMapper(
        @UserSessionBinding("userId")
        userId: Provider<UserId>,
    ): QualifiedIdMapper =
        MapperProvider.qualifiedIdMapper(userId())

    @Provides
    @UserSessionBinding("searchUserRepository")
    fun userSessionProvideSearchUserRepository(
        @UserSessionBinding("authenticatedNetworkContainer")
        authenticatedNetworkContainer: Provider<AuthenticatedNetworkContainer>,
        @UserSessionBinding("userSearchApiWrapper")
        userSearchApiWrapper: Provider<UserSearchApiWrapper>,
        @UserSessionBinding("userStorage")
        userStorage: Provider<UserStorage>,
        @UserSessionBinding("selfTeamId")
        selfTeamId: Provider<SelfTeamIdProvider>,
        @UserSessionBinding("userId")
        userId: Provider<UserId>,
    ): SearchUserRepository =
        SearchUserRepositoryImpl(
            userStorage().database.userDAO,
            userStorage().database.searchDAO,
            userStorage().database.appDAO,
            authenticatedNetworkContainer().userDetailsApi,
            userSearchApiWrapper(),
            userId(),
            selfTeamId()
        )

    @Provides
    @UserSessionBinding("serviceRepository")
    fun userSessionProvideServiceRepository(
        @UserSessionBinding("userStorage")
        userStorage: Provider<UserStorage>,
    ): ServiceRepository =
        ServiceDataSource(
            serviceDAO = userStorage().database.serviceDAO,
        )

    @Provides
    @UserSessionBinding("teamRepository")
    fun userSessionProvideTeamRepository(
        @UserSessionBinding("authenticatedNetworkContainer")
        authenticatedNetworkContainer: Provider<AuthenticatedNetworkContainer>,
        @UserSessionBinding("legalHoldRequestHandler")
        legalHoldRequestHandler: Provider<LegalHoldRequestHandlerImpl>,
        @UserSessionBinding("legalHoldHandler")
        legalHoldHandler: Provider<LegalHoldHandlerImpl>,
        @UserSessionBinding("userStorage")
        userStorage: Provider<UserStorage>,
        @UserSessionBinding("userId")
        userId: Provider<UserId>,
    ): TeamRepository =
        TeamDataSource(
            userStorage().database.userDAO,
            userStorage().database.userConfigDAO,
            userStorage().database.teamDAO,
            authenticatedNetworkContainer().teamsApi,
            userId(),
            userStorage().database.serviceDAO,
            userStorage().database.appDAO,
            legalHoldHandler(),
            legalHoldRequestHandler(),
        )

    @Provides
    @UserSessionBinding("userConfigRepository")
    fun userSessionProvideUserConfigRepository(
        @UserSessionBinding("kaliumConfigs")
        kaliumConfigs: Provider<KaliumConfigs>,
        @UserSessionBinding("userStorage")
        userStorage: Provider<UserStorage>,
    ): UserConfigRepository =
        UserConfigDataSource(
            userStorage().database.userPrefsDAO,
            userStorage().database.userConfigDAO,
            kaliumConfigs().fileRestrictionState
        )

    @Provides
    @UserSessionBinding("userPropertyRepository")
    fun userSessionProvideUserPropertyRepository(
        @UserSessionBinding("userPropertyRepositoryConversationFoldersPropertyDataSource")
        userPropertyRepositoryConversationFoldersPropertyDataSource: Provider<ConversationFoldersPropertyDataSource>,
        @UserSessionBinding("userPropertyRepositoryScreenshotCensoringPropertyDataSource")
        userPropertyRepositoryScreenshotCensoringPropertyDataSource: Provider<ScreenshotCensoringPropertyDataSource>,
        @UserSessionBinding("userPropertyRepositoryTypingIndicatorPropertyDataSource")
        userPropertyRepositoryTypingIndicatorPropertyDataSource: Provider<TypingIndicatorPropertyDataSource>,
        @UserSessionBinding("userPropertyRepositoryLinkPreviewsPropertyDataSource")
        userPropertyRepositoryLinkPreviewsPropertyDataSource: Provider<LinkPreviewsPropertyDataSource>,
        @UserSessionBinding("userPropertyRepositoryReadReceiptsPropertyDataSource")
        userPropertyRepositoryReadReceiptsPropertyDataSource: Provider<ReadReceiptsPropertyDataSource>,
        @UserSessionBinding("userPropertyRepositoryUserPropertiesSyncDataSource")
        userPropertyRepositoryUserPropertiesSyncDataSource: Provider<UserPropertiesSyncDataSource>,
    ): UserPropertyRepository =
        UserPropertyDataSource(
            readReceipts = userPropertyRepositoryReadReceiptsPropertyDataSource(),
            typingIndicator = userPropertyRepositoryTypingIndicatorPropertyDataSource(),
            linkPreviews = userPropertyRepositoryLinkPreviewsPropertyDataSource(),
            screenshotCensoring = userPropertyRepositoryScreenshotCensoringPropertyDataSource(),
            userPropertiesSync = userPropertyRepositoryUserPropertiesSyncDataSource(),
            conversationFolders = userPropertyRepositoryConversationFoldersPropertyDataSource(),
        )

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
    @UserSessionBinding("userPropertyRepositoryLinkPreviewsPropertyDataSource")
    fun userSessionProvideUserPropertyRepositoryLinkPreviewsPropertyDataSource(
        @UserSessionBinding("authenticatedNetworkContainer")
        authenticatedNetworkContainer: Provider<AuthenticatedNetworkContainer>,
        @UserSessionBinding("userConfigRepository")
        userConfigRepository: Provider<UserConfigRepository>,
    ): LinkPreviewsPropertyDataSource =
        LinkPreviewsPropertyDataSource(authenticatedNetworkContainer().propertiesApi, userConfigRepository())

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
    @UserSessionBinding("userPropertyRepositoryScreenshotCensoringPropertyDataSource")
    fun userSessionProvideUserPropertyRepositoryScreenshotCensoringPropertyDataSource(
        @UserSessionBinding("authenticatedNetworkContainer")
        authenticatedNetworkContainer: Provider<AuthenticatedNetworkContainer>,
        @UserSessionBinding("userConfigRepository")
        userConfigRepository: Provider<UserConfigRepository>,
    ): ScreenshotCensoringPropertyDataSource =
        ScreenshotCensoringPropertyDataSource(authenticatedNetworkContainer().propertiesApi, userConfigRepository())

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
    @UserSessionBinding("userRepository")
    fun userSessionProvideUserRepository(
        @UserSessionBinding("authenticatedNetworkContainer")
        authenticatedNetworkContainer: Provider<AuthenticatedNetworkContainer>,
        @UserSessionBinding("globalSessionRepository")
        globalSessionRepository: Provider<SessionRepository>,
        @UserSessionBinding("legalHoldHandler")
        legalHoldHandler: Provider<LegalHoldHandlerImpl>,
        @UserSessionBinding("userStorage")
        userStorage: Provider<UserStorage>,
        @UserSessionBinding("selfTeamId")
        selfTeamId: Provider<SelfTeamIdProvider>,
        @UserSessionBinding("userId")
        userId: Provider<UserId>,
    ): UserRepository =
        UserDataSource(
            userDAO = userStorage().database.userDAO,
            clientDAO = userStorage().database.clientDAO,
            memberDAO = userStorage().database.memberDAO,
            appDAO = userStorage().database.appDAO,
            selfApi = authenticatedNetworkContainer().selfApi,
            userDetailsApi = authenticatedNetworkContainer().userDetailsApi,
            upgradePersonalToTeamApi = authenticatedNetworkContainer().upgradePersonalToTeamApi,
            teamsApi = authenticatedNetworkContainer().teamsApi,
            sessionRepository = globalSessionRepository(),
            selfUserId = userId(),
            selfTeamIdProvider = selfTeamId(),
            legalHoldHandler = legalHoldHandler()
        )
}
