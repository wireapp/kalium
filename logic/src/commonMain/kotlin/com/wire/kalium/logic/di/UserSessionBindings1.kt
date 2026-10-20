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

import com.wire.kalium.logic.cache.MLSSelfConversationIdProvider
import com.wire.kalium.logic.cache.MLSSelfConversationIdProviderImpl
import com.wire.kalium.logic.cache.ProteusSelfConversationIdProvider
import com.wire.kalium.logic.cache.ProteusSelfConversationIdProviderImpl
import com.wire.kalium.logic.cache.SelfConversationIdProvider
import com.wire.kalium.logic.cache.SelfConversationIdProviderImpl
import com.wire.kalium.logic.configuration.UserConfigDataSource
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.configuration.notification.NotificationTokenDataSource
import com.wire.kalium.logic.data.call.mapper.CallMapper
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.client.CryptoTransactionProvider
import com.wire.kalium.logic.data.client.IsClientAsyncNotificationsCapableProvider
import com.wire.kalium.logic.data.client.IsClientAsyncNotificationsCapableProviderImpl
import com.wire.kalium.logic.data.client.MLSClientProvider
import com.wire.kalium.logic.data.client.MLSTransportProvider
import com.wire.kalium.logic.data.client.MLSTransportProviderImpl
import com.wire.kalium.logic.data.client.ProteusMigrationRecoveryHandler
import com.wire.kalium.logic.data.client.X509CredentialAcquisitionConfigProvider
import com.wire.kalium.logic.data.client.X509CredentialAcquisitionConfigProviderImpl
import com.wire.kalium.logic.data.client.guardedBy
import com.wire.kalium.logic.data.conversation.ConversationDataSource
import com.wire.kalium.logic.data.conversation.ConversationGroupRepository
import com.wire.kalium.logic.data.conversation.ConversationGroupRepositoryImpl
import com.wire.kalium.logic.data.conversation.ConversationMetaDataDataSource
import com.wire.kalium.logic.data.conversation.ConversationMetaDataRepository
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.MLSConversationDataSource
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.conversation.NewConversationMembersRepository
import com.wire.kalium.logic.data.conversation.NewConversationMembersRepositoryImpl
import com.wire.kalium.logic.data.conversation.NewGroupConversationSystemMessagesCreator
import com.wire.kalium.logic.data.conversation.ObservableMLSConversationRepository
import com.wire.kalium.logic.data.conversation.SubconversationRepositoryImpl
import com.wire.kalium.logic.data.conversation.folders.ConversationFolderDataSource
import com.wire.kalium.logic.data.conversation.folders.ConversationFolderRepository
import com.wire.kalium.logic.data.conversation.mls.PendingActionsRepository
import com.wire.kalium.logic.data.e2ei.E2EIRepository
import com.wire.kalium.logic.data.e2ei.E2EIRepositoryImpl
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.id.FederatedIdMapper
import com.wire.kalium.logic.data.id.QualifiedIdMapper
import com.wire.kalium.logic.data.id.SelfTeamIdProvider
import com.wire.kalium.logic.data.keypackage.KeyPackageLimitsProvider
import com.wire.kalium.logic.data.keypackage.KeyPackageLimitsProviderImpl
import com.wire.kalium.logic.data.message.MessageMetadataRepository
import com.wire.kalium.logic.data.message.MessageMetadataSource
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.message.paging.NomadMessagePagingCoordinator
import com.wire.kalium.logic.data.message.paging.NomadMessagePagingCoordinatorImpl
import com.wire.kalium.logic.data.mls.MLSMissingUsersMessageRejectionHandler
import com.wire.kalium.logic.data.mls.MLSMissingUsersMessageRejectionHandlerImpl
import com.wire.kalium.logic.data.properties.ConversationFoldersPropertyDataSource
import com.wire.kalium.logic.data.properties.LinkPreviewsPropertyDataSource
import com.wire.kalium.logic.data.properties.ReadReceiptsPropertyDataSource
import com.wire.kalium.logic.data.properties.ScreenshotCensoringPropertyDataSource
import com.wire.kalium.logic.data.properties.TypingIndicatorPropertyDataSource
import com.wire.kalium.logic.data.properties.UserPropertiesSyncDataSource
import com.wire.kalium.logic.data.properties.UserPropertyDataSource
import com.wire.kalium.logic.data.properties.UserPropertyRepository
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.session.token.AccessTokenRepository
import com.wire.kalium.logic.data.session.token.AccessTokenRepositoryImpl
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.auth.LogoutUseCase
import com.wire.kalium.logic.feature.client.ProteusMigrationRecoveryHandlerImpl
import com.wire.kalium.logic.feature.session.token.AccessTokenRefresher
import com.wire.kalium.logic.feature.session.token.AccessTokenRefresherFactory
import com.wire.kalium.logic.feature.session.token.AccessTokenRefresherFactoryImpl
import com.wire.kalium.logic.feature.session.token.AccessTokenRefresherImpl
import com.wire.kalium.logic.featureFlags.FeatureSupport
import com.wire.kalium.logic.featureFlags.FeatureSupportImpl
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.network.guardedBy
import com.wire.kalium.logic.sync.local.LocalEventRepository
import com.wire.kalium.logic.sync.receiver.conversation.ConversationMessageTimerEventHandler
import com.wire.kalium.logic.sync.receiver.handler.legalhold.LegalHoldHandlerImpl
import com.wire.kalium.messaging.hooks.CryptoStateChangeHookNotifier
import com.wire.kalium.network.networkContainer.AuthenticatedNetworkContainer
import com.wire.kalium.network.networkContainer.UnboundNetworkContainer
import com.wire.kalium.network.session.SessionManager
import com.wire.kalium.persistence.client.ClientRegistrationStorage
import com.wire.kalium.persistence.kmmSettings.GlobalPrefProvider
import com.wire.kalium.userstorage.di.UserStorage
import com.wire.kalium.util.DelicateKaliumApi
import com.wire.kalium.util.InternalKaliumApi
import dev.zacsweers.metro.Provider
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope

@Suppress("TooManyFunctions", "LongParameterList", "LargeClass") // Individual qualified construction edges.
internal interface UserSessionBindings1 {
    @Provides
    @UserSessionBinding("callMapper")
    fun userSessionProvideCallMapper(
        @UserSessionBinding("userId")
        userId: Provider<UserId>,
    ): CallMapper =
        MapperProvider.callMapper(userId())

    @Provides
    @UserSessionBinding("qualifiedIdMapper")
    fun userSessionProvideQualifiedIdMapper(
        @UserSessionBinding("userId")
        userId: Provider<UserId>,
    ): QualifiedIdMapper =
        MapperProvider.qualifiedIdMapper(userId())

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
    @UserSessionBinding("isClientAsyncNotificationsCapableProvider")
    fun userSessionProvideIsClientAsyncNotificationsCapableProvider(
        @UserSessionBinding("clientRegistrationStorage")
        clientRegistrationStorage: Provider<ClientRegistrationStorage>,
        @UserSessionBinding("sessionCoroutineScope")
        sessionCoroutineScope: Provider<CoroutineScope>,
    ): IsClientAsyncNotificationsCapableProvider =
        IsClientAsyncNotificationsCapableProviderImpl(clientRegistrationStorage(), sessionCoroutineScope())

    @Provides
    @UserSessionBinding("mlsSelfConversationIdProvider")
    @SingleIn(UserLifetime::class)
    fun userSessionProvideMlsSelfConversationIdProvider(
        @UserSessionBinding("conversationRepository")
        conversationRepository: Provider<ConversationRepository>,
    ): MLSSelfConversationIdProvider =
        MLSSelfConversationIdProviderImpl(
            conversationRepository()
        )

    @Provides
    @UserSessionBinding("proteusSelfConversationIdProvider")
    @SingleIn(UserLifetime::class)
    fun userSessionProvideProteusSelfConversationIdProvider(
        @UserSessionBinding("conversationRepository")
        conversationRepository: Provider<ConversationRepository>,
    ): ProteusSelfConversationIdProvider =
        ProteusSelfConversationIdProviderImpl(
            conversationRepository()
        )

    @Provides
    @UserSessionBinding("selfConversationIdProvider")
    @SingleIn(UserLifetime::class)
    fun userSessionProvideSelfConversationIdProvider(
        @UserSessionBinding("proteusSelfConversationIdProvider")
        proteusSelfConversationIdProvider: Provider<ProteusSelfConversationIdProvider>,
        @UserSessionBinding("mlsSelfConversationIdProvider")
        mlsSelfConversationIdProvider: Provider<MLSSelfConversationIdProvider>,
        @UserSessionBinding("clientRepository")
        clientRepository: Provider<ClientRepository>,
    ): SelfConversationIdProvider =
        SelfConversationIdProviderImpl(
            clientRepository(),
            mlsSelfConversationIdProvider(),
            proteusSelfConversationIdProvider()
        )

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
    @UserSessionBinding("userScopedNomadHookFactory")
    @SingleIn(UserLifetime::class)
    fun userSessionProvideUserScopedNomadHookFactory(): UserScopedNomadHookFactory =
        UserScopedNomadHookFactory()

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
    @UserSessionBinding("keyPackageLimitsProvider")
    fun userSessionProvideKeyPackageLimitsProvider(
        @UserSessionBinding("kaliumConfigs")
        kaliumConfigs: Provider<KaliumConfigs>,
    ): KeyPackageLimitsProvider =
        KeyPackageLimitsProviderImpl(kaliumConfigs())

    @Provides
    @UserSessionBinding("proteusMigrationRecoveryHandler")
    @SingleIn(UserLifetime::class)
    fun userSessionProvideProteusMigrationRecoveryHandler(
        @UserSessionBinding("logout")
        logout: Provider<LogoutUseCase>,
    ): ProteusMigrationRecoveryHandler =
        ProteusMigrationRecoveryHandlerImpl(lazy { logout() })

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
    @UserSessionBinding("mlsMissingUsersRejectionHandlerProvider")
    @SingleIn(UserLifetime::class)
    fun userSessionProvideMlsMissingUsersRejectionHandlerProvider(
        @UserSessionBinding("newMlsMissingUsersRejectionHandler")
        newMlsMissingUsersRejectionHandler: Provider<MLSMissingUsersMessageRejectionHandlerImpl>,
    ): UserSessionValue<() -> MLSMissingUsersMessageRejectionHandler> =
        UserSessionValue({
            newMlsMissingUsersRejectionHandler()
        })

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
    @UserSessionBinding("x509CredentialAcquisitionConfigProvider")
    @SingleIn(UserLifetime::class)
    fun userSessionProvideX509CredentialAcquisitionConfigProvider(
        @UserSessionBinding("mlsClientProvider")
        mlsClientProvider: Provider<MLSClientProvider>,
        @UserSessionBinding("clientIdProvider")
        clientIdProvider: Provider<CurrentClientIdProvider>,
        @UserSessionBinding("userRepository")
        userRepository: Provider<UserRepository>,
    ): X509CredentialAcquisitionConfigProvider =
        X509CredentialAcquisitionConfigProviderImpl(
            currentClientIdProvider = clientIdProvider(),
            mlsClientProvider = mlsClientProvider(),
            userRepository = userRepository()
        )

    @Provides
    @UserSessionBinding("notificationTokenRepository")
    fun userSessionProvideNotificationTokenRepository(
        @UserSessionBinding("globalPreferences")
        globalPreferences: Provider<GlobalPrefProvider>,
    ): NotificationTokenDataSource =
        NotificationTokenDataSource(globalPreferences().tokenStorage)

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
    @UserSessionBinding("conversationMetaDataRepository")
    fun userSessionProvideConversationMetaDataRepository(
        @UserSessionBinding("userStorage")
        userStorage: Provider<UserStorage>,
    ): ConversationMetaDataRepository =
        ConversationMetaDataDataSource(
            userStorage().database.conversationMetaDataDAO,
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
    @UserSessionBinding("messageRepository")
    fun userSessionProvideMessageRepository(
        @UserSessionBinding("nomadMessagePagingCoordinator")
        nomadMessagePagingCoordinator: Provider<NomadMessagePagingCoordinator?>,
        @UserSessionBinding("messageRepositoryFactory")
        messageRepositoryFactory: Provider<UserSessionMessageRepositoryFactory>,
    ): MessageRepository =
        messageRepositoryFactory().create(nomadMessagePagingCoordinator())

    @Provides
    @UserSessionBinding("messageApiPagingCoordinator")
    @SingleIn(UserLifetime::class)
    fun userSessionProvideMessageApiPagingCoordinator(
        @UserSessionBinding("nomadMessagePagingCoordinator")
        nomadMessagePagingCoordinator: Provider<NomadMessagePagingCoordinator?>,
    ): NomadMessagePagingCoordinator? =
        nomadMessagePagingCoordinator()

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
    @UserSessionBinding("messageMetadataRepository")
    fun userSessionProvideMessageMetadataRepository(
        @UserSessionBinding("userStorage")
        userStorage: Provider<UserStorage>,
    ): MessageMetadataRepository =
        MessageMetadataSource(messageMetaDataDAO = userStorage().database.messageMetaDataDAO)

}
