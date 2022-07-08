package com.wire.kalium.logic.feature

import com.wire.kalium.logic.AuthenticatedDataSourceSet
import com.wire.kalium.logic.configuration.ClientConfig
import com.wire.kalium.logic.configuration.UserConfigDataSource
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.configuration.notification.NotificationTokenDataSource
import com.wire.kalium.logic.data.asset.AssetDataSource
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.call.CallDataSource
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.client.ClientDataSource
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.client.MLSClientProvider
import com.wire.kalium.logic.data.client.MLSClientProviderImpl
import com.wire.kalium.logic.data.client.remote.ClientRemoteDataSource
import com.wire.kalium.logic.data.client.remote.ClientRemoteRepository
import com.wire.kalium.logic.data.connection.ConnectionDataSource
import com.wire.kalium.logic.data.connection.ConnectionRepository
import com.wire.kalium.logic.data.conversation.ConversationDataSource
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.MLSConversationDataSource
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.event.EventDataSource
import com.wire.kalium.logic.data.event.EventRepository
import com.wire.kalium.logic.data.featureConfig.FeatureConfigDataSource
import com.wire.kalium.logic.data.featureConfig.FeatureConfigRepository
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.keypackage.KeyPackageDataSource
import com.wire.kalium.logic.data.keypackage.KeyPackageRepository
import com.wire.kalium.logic.data.logout.LogoutDataSource
import com.wire.kalium.logic.data.logout.LogoutRepository
import com.wire.kalium.logic.data.message.MessageDataSource
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.prekey.PreKeyDataSource
import com.wire.kalium.logic.data.prekey.PreKeyRepository
import com.wire.kalium.logic.data.prekey.remote.PreKeyRemoteDataSource
import com.wire.kalium.logic.data.prekey.remote.PreKeyRemoteRepository
import com.wire.kalium.logic.data.publicuser.SearchUserRepository
import com.wire.kalium.logic.data.publicuser.SearchUserRepositoryImpl
import com.wire.kalium.logic.data.publicuser.UserSearchApiWrapper
import com.wire.kalium.logic.data.publicuser.UserSearchApiWrapperImpl
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.sync.InMemorySyncRepository
import com.wire.kalium.logic.data.sync.SyncRepository
import com.wire.kalium.logic.data.team.TeamDataSource
import com.wire.kalium.logic.data.team.TeamRepository
import com.wire.kalium.logic.data.user.UserDataSource
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.auth.LogoutUseCase
import com.wire.kalium.logic.feature.call.CallManager
import com.wire.kalium.logic.feature.call.CallsScope
import com.wire.kalium.logic.feature.call.GlobalCallManager
import com.wire.kalium.logic.feature.client.ClientScope
import com.wire.kalium.logic.feature.connection.ConnectionScope
import com.wire.kalium.logic.feature.conversation.ConversationScope
import com.wire.kalium.logic.feature.featureConfig.GetFeatureConfigStatusUseCaseImpl
import com.wire.kalium.logic.feature.featureConfig.GetRemoteFeatureConfigStatusAndPersistUseCase
import com.wire.kalium.logic.feature.message.MLSMessageCreator
import com.wire.kalium.logic.feature.message.MLSMessageCreatorImpl
import com.wire.kalium.logic.feature.message.MessageEnvelopeCreator
import com.wire.kalium.logic.feature.message.MessageEnvelopeCreatorImpl
import com.wire.kalium.logic.feature.message.MessageScope
import com.wire.kalium.logic.feature.message.MessageSendFailureHandler
import com.wire.kalium.logic.feature.message.MessageSendFailureHandlerImpl
import com.wire.kalium.logic.feature.message.MessageSender
import com.wire.kalium.logic.feature.message.MessageSenderImpl
import com.wire.kalium.logic.feature.message.MessageSendingScheduler
import com.wire.kalium.logic.feature.message.SessionEstablisher
import com.wire.kalium.logic.feature.message.SessionEstablisherImpl
import com.wire.kalium.logic.feature.team.TeamScope
import com.wire.kalium.logic.feature.user.IsFileSharingEnabledUseCase
import com.wire.kalium.logic.feature.user.IsFileSharingEnabledUseCaseImpl
import com.wire.kalium.logic.feature.user.UserScope
import com.wire.kalium.logic.sync.ConversationEventReceiverImpl
import com.wire.kalium.logic.sync.EventGatherer
import com.wire.kalium.logic.sync.EventGathererImpl
import com.wire.kalium.logic.sync.ObserveSyncStateUseCase
import com.wire.kalium.logic.sync.SyncManager
import com.wire.kalium.logic.sync.SyncManagerImpl
import com.wire.kalium.logic.sync.UserEventReceiverImpl
import com.wire.kalium.logic.sync.ConversationEventReceiver
import com.wire.kalium.logic.sync.event.EventProcessor
import com.wire.kalium.logic.sync.event.EventProcessorImpl
import com.wire.kalium.logic.sync.UserEventReceiver
import com.wire.kalium.logic.sync.handler.MessageTextEditHandler
import com.wire.kalium.logic.util.TimeParser
import com.wire.kalium.logic.util.TimeParserImpl
import com.wire.kalium.persistence.client.ClientRegistrationStorage
import com.wire.kalium.persistence.client.ClientRegistrationStorageImpl
import com.wire.kalium.persistence.client.TokenStorage
import com.wire.kalium.persistence.client.TokenStorageImpl
import com.wire.kalium.persistence.client.UserConfigStorage
import com.wire.kalium.persistence.client.UserConfigStorageImpl
import com.wire.kalium.persistence.db.UserDatabaseProvider
import com.wire.kalium.persistence.event.EventInfoStorage
import com.wire.kalium.persistence.event.EventInfoStorageImpl
import com.wire.kalium.persistence.kmm_settings.EncryptedSettingsHolder
import com.wire.kalium.persistence.kmm_settings.KaliumPreferences

expect class UserSessionScope : UserSessionScopeCommon

abstract class UserSessionScopeCommon(
    private val userId: QualifiedID,
    private val authenticatedDataSourceSet: AuthenticatedDataSourceSet,
    private val sessionRepository: SessionRepository,
    private val globalCallManager: GlobalCallManager,
    private val globalPreferences: KaliumPreferences
) {
    private val userConfigStorage: UserConfigStorage get() = UserConfigStorageImpl(globalPreferences)

    private val userConfigRepository: UserConfigRepository get() = UserConfigDataSource(userConfigStorage)

    private val encryptedSettingsHolder: EncryptedSettingsHolder = authenticatedDataSourceSet.encryptedSettingsHolder
    private val userPreferencesSettings = authenticatedDataSourceSet.kaliumPreferencesSettings
    private val eventInfoStorage: EventInfoStorage
        get() = EventInfoStorageImpl(userPreferencesSettings)

    private val userDatabaseProvider: UserDatabaseProvider = authenticatedDataSourceSet.userDatabaseProvider

    private val mlsClientProvider: MLSClientProvider
        get() = MLSClientProviderImpl(
            "${authenticatedDataSourceSet.authenticatedRootDir}/mls",
            userId,
            clientRepository,
            authenticatedDataSourceSet.kaliumPreferencesSettings
        )

    private val mlsConversationRepository: MLSConversationRepository
        get() = MLSConversationDataSource(
            keyPackageRepository,
            mlsClientProvider, authenticatedDataSourceSet.authenticatedNetworkContainer.mlsMessageApi,
            userDatabaseProvider.conversationDAO
        )

    private val notificationTokenRepository get() = NotificationTokenDataSource(tokenStorage)

    private val conversationRepository: ConversationRepository
        get() = ConversationDataSource(
            userRepository,
            mlsConversationRepository,
            userDatabaseProvider.conversationDAO,
            authenticatedDataSourceSet.authenticatedNetworkContainer.conversationApi,
            authenticatedDataSourceSet.authenticatedNetworkContainer.clientApi
        )

    private val messageRepository: MessageRepository
        get() = MessageDataSource(
            authenticatedDataSourceSet.authenticatedNetworkContainer.messageApi,
            authenticatedDataSourceSet.authenticatedNetworkContainer.mlsMessageApi,
            userDatabaseProvider.messageDAO
        )

    private val userRepository: UserRepository
        get() = UserDataSource(
            userDatabaseProvider.userDAO,
            userDatabaseProvider.metadataDAO,
            authenticatedDataSourceSet.authenticatedNetworkContainer.selfApi,
            authenticatedDataSourceSet.authenticatedNetworkContainer.userDetailsApi,
            assetRepository
        )

    private val teamRepository: TeamRepository
        get() = TeamDataSource(
            userDatabaseProvider.userDAO,
            userDatabaseProvider.teamDAO,
            authenticatedDataSourceSet.authenticatedNetworkContainer.teamsApi
        )

    private val connectionRepository: ConnectionRepository
        get() = ConnectionDataSource(
            userDatabaseProvider.conversationDAO,
            userDatabaseProvider.connectionDAO,
            authenticatedDataSourceSet.authenticatedNetworkContainer.connectionApi,
            authenticatedDataSourceSet.authenticatedNetworkContainer.userDetailsApi,
            userDatabaseProvider.userDAO,
            userDatabaseProvider.metadataDAO
        )

    private val userSearchApiWrapper : UserSearchApiWrapper = UserSearchApiWrapperImpl(
        authenticatedDataSourceSet.authenticatedNetworkContainer.userSearchApi,
        userDatabaseProvider.conversationDAO,
    )

    private val publicUserRepository: SearchUserRepository
        get() = SearchUserRepositoryImpl(
            userDatabaseProvider.userDAO,
            userDatabaseProvider.metadataDAO,
            authenticatedDataSourceSet.authenticatedNetworkContainer.userDetailsApi,
            userSearchApiWrapper
        )

    private val callRepository: CallRepository by lazy {
        CallDataSource(
            callApi = authenticatedDataSourceSet.authenticatedNetworkContainer.callApi,
            conversationRepository = conversationRepository,
            userRepository = userRepository,
            teamRepository = teamRepository,
            timeParser = timeParser,
            messageRepository = messageRepository
        )
    }

    protected abstract val clientConfig: ClientConfig

    private val tokenStorage: TokenStorage
        get() = TokenStorageImpl(globalPreferences)

    private val clientRemoteRepository: ClientRemoteRepository
        get() = ClientRemoteDataSource(
            authenticatedDataSourceSet.authenticatedNetworkContainer.clientApi, clientConfig
        )

    private val clientRegistrationStorage: ClientRegistrationStorage
        get() = ClientRegistrationStorageImpl(userDatabaseProvider.metadataDAO)

    private val clientRepository: ClientRepository
        get() = ClientDataSource(clientRemoteRepository, clientRegistrationStorage, userDatabaseProvider.clientDAO)

    private val messageSendFailureHandler: MessageSendFailureHandler
        get() = MessageSendFailureHandlerImpl(userRepository, clientRepository)

    private val sessionEstablisher: SessionEstablisher
        get() = SessionEstablisherImpl(authenticatedDataSourceSet.proteusClient, preKeyRepository)

    private val messageEnvelopeCreator: MessageEnvelopeCreator
        get() = MessageEnvelopeCreatorImpl(authenticatedDataSourceSet.proteusClient)

    private val mlsMessageCreator: MLSMessageCreator
        get() = MLSMessageCreatorImpl(mlsClientProvider)

    private val messageSendingScheduler: MessageSendingScheduler
        get() = authenticatedDataSourceSet.userSessionWorkScheduler

    // TODO(optimization) code duplication, can't we get the MessageSender from the message scope?
    private val messageSender: MessageSender
        get() = MessageSenderImpl(
            messageRepository,
            conversationRepository,
            syncManager,
            messageSendFailureHandler,
            sessionEstablisher,
            messageEnvelopeCreator,
            mlsMessageCreator,
            messageSendingScheduler,
            timeParser
        )

    private val assetRepository: AssetRepository
        get() = AssetDataSource(authenticatedDataSourceSet.authenticatedNetworkContainer.assetApi, userDatabaseProvider.assetDAO)

    private val syncRepository: SyncRepository by lazy { InMemorySyncRepository() }

    private val eventGatherer: EventGatherer get() = EventGathererImpl(eventRepository, syncRepository)

    private val eventProcessor: EventProcessor get() = EventProcessorImpl(eventRepository, conversationEventReceiver, userEventReceiver)

    val syncManager: SyncManager by lazy {
        SyncManagerImpl(
            authenticatedDataSourceSet.userSessionWorkScheduler,
            syncRepository,
            eventProcessor,
            eventGatherer
        )
    }

    private val timeParser: TimeParser = TimeParserImpl()

    private val eventRepository: EventRepository
        get() = EventDataSource(
            authenticatedDataSourceSet.authenticatedNetworkContainer.notificationApi, eventInfoStorage, clientRepository
        )

    private val callManager: Lazy<CallManager> = lazy {
        globalCallManager.getCallManagerForClient(
            userId = userId,
            callRepository = callRepository,
            userRepository = userRepository,
            clientRepository = clientRepository,
            conversationRepository = conversationRepository,
            messageSender = messageSender
        )
    }

    private val flowManagerService by lazy {
        globalCallManager.getFlowManager()
    }

    private val mediaManagerService by lazy {
        globalCallManager.getMediaManager()
    }

    private val messageTextEditHandler = MessageTextEditHandler(messageRepository)

    private val conversationEventReceiver: ConversationEventReceiver by lazy {
        ConversationEventReceiverImpl(
            authenticatedDataSourceSet.proteusClient,
            messageRepository,
            conversationRepository,
            mlsConversationRepository,
            userRepository,
            callManager,
            messageTextEditHandler,
            userConfigRepository
        )
    }

    private val userEventReceiver: UserEventReceiver
        get() = UserEventReceiverImpl(
            connectionRepository,
        )

    private val preKeyRemoteRepository: PreKeyRemoteRepository
        get() = PreKeyRemoteDataSource(authenticatedDataSourceSet.authenticatedNetworkContainer.preKeyApi)
    private val preKeyRepository: PreKeyRepository
        get() = PreKeyDataSource(
            preKeyRemoteRepository, authenticatedDataSourceSet.proteusClient
        )

    private val keyPackageRepository: KeyPackageRepository
        get() = KeyPackageDataSource(
            clientRepository,
            authenticatedDataSourceSet.authenticatedNetworkContainer.keyPackageApi,
            mlsClientProvider
        )

    private val logoutRepository: LogoutRepository = LogoutDataSource(authenticatedDataSourceSet.authenticatedNetworkContainer.logoutApi)

    val observeSyncState: ObserveSyncStateUseCase
        get() = ObserveSyncStateUseCase(syncRepository)
    val client: ClientScope
        get() = ClientScope(
            clientRepository,
            preKeyRepository,
            keyPackageRepository,
            mlsClientProvider,
            notificationTokenRepository
        )
    val conversations: ConversationScope
        get() = ConversationScope(
            conversationRepository,
            connectionRepository,
            userRepository,
            callRepository,
            syncManager,
            mlsConversationRepository,
            clientRepository
        )
    val messages: MessageScope
        get() = MessageScope(
            messageRepository,
            conversationRepository,
            clientRepository,
            authenticatedDataSourceSet.proteusClient,
            mlsClientProvider,
            preKeyRepository,
            userRepository,
            assetRepository,
            syncManager,
            messageSendingScheduler,
            timeParser
        )
    val users: UserScope
        get() = UserScope(
            userRepository,
            publicUserRepository,
            syncManager,
            assetRepository,
            teamRepository,
            connectionRepository
        )
    val logout: LogoutUseCase
        get() = LogoutUseCase(
            logoutRepository,
            sessionRepository,
            userId,
            authenticatedDataSourceSet,
            clientRepository,
            mlsClientProvider,
            client.deregisterNativePushToken
        )
    private val featureConfigRepository: FeatureConfigRepository
        get() = FeatureConfigDataSource(featureConfigApi = authenticatedDataSourceSet.authenticatedNetworkContainer.featureConfigApi)
    val isFileSharingEnabled: IsFileSharingEnabledUseCase get() = IsFileSharingEnabledUseCaseImpl(userConfigRepository)

    val getRemoteFeatureConfigsStatusAndPersist: GetRemoteFeatureConfigStatusAndPersistUseCase
        get() = GetFeatureConfigStatusUseCaseImpl(
            userConfigRepository,
            featureConfigRepository,
            isFileSharingEnabled
        )

    val team: TeamScope get() = TeamScope(userRepository, teamRepository, syncManager)

    val calls: CallsScope
        get() = CallsScope(
            callManager,
            callRepository,
            conversationRepository,
            userRepository,
            flowManagerService,
            mediaManagerService,
            syncManager
        )

    val connection: ConnectionScope get() = ConnectionScope(connectionRepository, conversationRepository)

}
