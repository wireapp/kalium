package com.wire.kalium.logic.feature

import com.wire.kalium.logic.AuthenticatedDataSourceSet
import com.wire.kalium.logic.configuration.ClientConfig
import com.wire.kalium.logic.configuration.UserConfigDataSource
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.configuration.notification.NotificationTokenDataSource
import com.wire.kalium.logic.data.asset.AssetDataSource
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.asset.DataStoragePaths
import com.wire.kalium.logic.data.asset.KaliumFileSystem
import com.wire.kalium.logic.data.asset.KaliumFileSystemImpl
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
import com.wire.kalium.logic.data.id.FederatedIdMapper
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.id.QualifiedIdMapper
import com.wire.kalium.logic.data.keypackage.KeyPackageDataSource
import com.wire.kalium.logic.data.keypackage.KeyPackageLimitsProvider
import com.wire.kalium.logic.data.keypackage.KeyPackageLimitsProviderImpl
import com.wire.kalium.logic.data.keypackage.KeyPackageRepository
import com.wire.kalium.logic.data.logout.LogoutDataSource
import com.wire.kalium.logic.data.logout.LogoutRepository
import com.wire.kalium.logic.data.message.MessageDataSource
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.message.PersistMessageUseCaseImpl
import com.wire.kalium.logic.data.prekey.PreKeyDataSource
import com.wire.kalium.logic.data.prekey.PreKeyRepository
import com.wire.kalium.logic.data.prekey.remote.PreKeyRemoteDataSource
import com.wire.kalium.logic.data.prekey.remote.PreKeyRemoteRepository
import com.wire.kalium.logic.data.publicuser.SearchUserRepository
import com.wire.kalium.logic.data.publicuser.SearchUserRepositoryImpl
import com.wire.kalium.logic.data.publicuser.UserSearchApiWrapper
import com.wire.kalium.logic.data.publicuser.UserSearchApiWrapperImpl
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.sync.InMemoryIncrementalSyncRepository
import com.wire.kalium.logic.data.sync.InMemorySlowSyncRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncRepository
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.data.team.TeamDataSource
import com.wire.kalium.logic.data.team.TeamRepository
import com.wire.kalium.logic.data.user.UserDataSource
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.feature.auth.LogoutUseCase
import com.wire.kalium.logic.feature.auth.LogoutUseCaseImpl
import com.wire.kalium.logic.feature.call.CallManager
import com.wire.kalium.logic.feature.call.CallsScope
import com.wire.kalium.logic.feature.call.GlobalCallManager
import com.wire.kalium.logic.feature.client.ClientScope
import com.wire.kalium.logic.feature.connection.ConnectionScope
import com.wire.kalium.logic.feature.connection.SyncConnectionsUseCase
import com.wire.kalium.logic.feature.connection.SyncConnectionsUseCaseImpl
import com.wire.kalium.logic.feature.conversation.ConversationScope
import com.wire.kalium.logic.feature.conversation.JoinExistingMLSConversationsUseCase
import com.wire.kalium.logic.feature.conversation.SyncConversationsUseCase
import com.wire.kalium.logic.feature.featureConfig.SyncFeatureConfigsUseCase
import com.wire.kalium.logic.feature.featureConfig.SyncFeatureConfigsUseCaseImpl
import com.wire.kalium.logic.feature.keypackage.KeyPackageManager
import com.wire.kalium.logic.feature.keypackage.KeyPackageManagerImpl
import com.wire.kalium.logic.feature.message.EphemeralNotificationsManager
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
import com.wire.kalium.logic.feature.team.SyncSelfTeamUseCase
import com.wire.kalium.logic.feature.team.SyncSelfTeamUseCaseImpl
import com.wire.kalium.logic.feature.team.TeamScope
import com.wire.kalium.logic.feature.user.IsFileSharingEnabledUseCase
import com.wire.kalium.logic.feature.user.IsFileSharingEnabledUseCaseImpl
import com.wire.kalium.logic.feature.user.IsMLSEnabledUseCase
import com.wire.kalium.logic.feature.user.IsMLSEnabledUseCaseImpl
import com.wire.kalium.logic.feature.user.ObserveFileSharingStatusUseCase
import com.wire.kalium.logic.feature.user.ObserveFileSharingStatusUseCaseImpl
import com.wire.kalium.logic.feature.user.SyncContactsUseCase
import com.wire.kalium.logic.feature.user.SyncContactsUseCaseImpl
import com.wire.kalium.logic.feature.user.SyncSelfUserUseCase
import com.wire.kalium.logic.feature.user.UserScope
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.sync.ObserveSyncStateUseCase
import com.wire.kalium.logic.sync.SetConnectionPolicyUseCase
import com.wire.kalium.logic.sync.SyncCriteriaProvider
import com.wire.kalium.logic.sync.SyncCriteriaProviderImpl
import com.wire.kalium.logic.sync.SyncManager
import com.wire.kalium.logic.sync.SyncManagerImpl
import com.wire.kalium.logic.sync.full.SlowSyncManager
import com.wire.kalium.logic.sync.full.SlowSyncWorker
import com.wire.kalium.logic.sync.full.SlowSyncWorkerImpl
import com.wire.kalium.logic.sync.incremental.EventGatherer
import com.wire.kalium.logic.sync.incremental.EventGathererImpl
import com.wire.kalium.logic.sync.incremental.EventProcessor
import com.wire.kalium.logic.sync.incremental.EventProcessorImpl
import com.wire.kalium.logic.sync.incremental.IncrementalSyncManager
import com.wire.kalium.logic.sync.incremental.IncrementalSyncWorker
import com.wire.kalium.logic.sync.incremental.IncrementalSyncWorkerImpl
import com.wire.kalium.logic.sync.receiver.ConversationEventReceiver
import com.wire.kalium.logic.sync.receiver.ConversationEventReceiverImpl
import com.wire.kalium.logic.sync.receiver.FeatureConfigEventReceiver
import com.wire.kalium.logic.sync.receiver.FeatureConfigEventReceiverImpl
import com.wire.kalium.logic.sync.receiver.UserEventReceiver
import com.wire.kalium.logic.sync.receiver.UserEventReceiverImpl
import com.wire.kalium.logic.sync.receiver.message.MessageTextEditHandler
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
import okio.Path.Companion.toPath

expect class UserSessionScope : UserSessionScopeCommon

@Suppress("LongParameterList")
abstract class UserSessionScopeCommon(
    private val userId: QualifiedID,
    private val authenticatedDataSourceSet: AuthenticatedDataSourceSet,
    private val sessionRepository: SessionRepository,
    private val globalCallManager: GlobalCallManager,
    private val globalPreferences: KaliumPreferences,
    dataStoragePaths: DataStoragePaths,
    private val kaliumConfigs: KaliumConfigs
) {
    // we made this lazy, so it will have a single instance for the storage
    private val userConfigStorage: UserConfigStorage by lazy { UserConfigStorageImpl(globalPreferences) }

    private val userConfigRepository: UserConfigRepository get() = UserConfigDataSource(userConfigStorage)

    private val encryptedSettingsHolder: EncryptedSettingsHolder = authenticatedDataSourceSet.encryptedSettingsHolder
    private val userPreferencesSettings = authenticatedDataSourceSet.kaliumPreferencesSettings
    private val eventInfoStorage: EventInfoStorage
        get() = EventInfoStorageImpl(userPreferencesSettings)

    private val userDatabaseProvider: UserDatabaseProvider = authenticatedDataSourceSet.userDatabaseProvider

    private val keyPackageLimitsProvider: KeyPackageLimitsProvider
        get() = KeyPackageLimitsProviderImpl(kaliumConfigs)

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
            mlsClientProvider,
            authenticatedDataSourceSet.authenticatedNetworkContainer.mlsMessageApi,
            userDatabaseProvider.conversationDAO,
            authenticatedDataSourceSet.authenticatedNetworkContainer.clientApi
        )

    private val notificationTokenRepository get() = NotificationTokenDataSource(tokenStorage)

    private val conversationRepository: ConversationRepository
        get() = ConversationDataSource(
            userRepository,
            mlsConversationRepository,
            userDatabaseProvider.conversationDAO,
            authenticatedDataSourceSet.authenticatedNetworkContainer.conversationApi,
            authenticatedDataSourceSet.authenticatedNetworkContainer.clientApi,
            timeParser
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
            userDatabaseProvider.clientDAO,
            authenticatedDataSourceSet.authenticatedNetworkContainer.selfApi,
            authenticatedDataSourceSet.authenticatedNetworkContainer.userDetailsApi,
            sessionRepository
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

    private val userSearchApiWrapper: UserSearchApiWrapper = UserSearchApiWrapperImpl(
        authenticatedDataSourceSet.authenticatedNetworkContainer.userSearchApi,
        userDatabaseProvider.conversationDAO,
        userDatabaseProvider.userDAO,
        userDatabaseProvider.metadataDAO
    )

    private val publicUserRepository: SearchUserRepository
        get() = SearchUserRepositoryImpl(
            userDatabaseProvider.userDAO,
            userDatabaseProvider.metadataDAO,
            authenticatedDataSourceSet.authenticatedNetworkContainer.userDetailsApi,
            userSearchApiWrapper
        )

    val persistMessage: PersistMessageUseCase
        get() = PersistMessageUseCaseImpl(messageRepository, conversationRepository, userId)

    private val callRepository: CallRepository by lazy {
        CallDataSource(
            callApi = authenticatedDataSourceSet.authenticatedNetworkContainer.callApi,
            qualifiedIdMapper = qualifiedIdMapper,
            callDAO = userDatabaseProvider.callDAO,
            conversationRepository = conversationRepository,
            userRepository = userRepository,
            teamRepository = teamRepository,
            timeParser = timeParser,
            persistMessage = persistMessage
        )
    }

    protected abstract val clientConfig: ClientConfig

    private val tokenStorage: TokenStorage
        get() = TokenStorageImpl(globalPreferences)

    private val clientRemoteRepository: ClientRemoteRepository
        get() = ClientRemoteDataSource(authenticatedDataSourceSet.authenticatedNetworkContainer.clientApi, clientConfig)

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
        get() = AssetDataSource(
            assetApi = authenticatedDataSourceSet.authenticatedNetworkContainer.assetApi,
            assetDao = userDatabaseProvider.assetDAO,
            kaliumFileSystem = kaliumFileSystem
        )

    private val incrementalSyncRepository: IncrementalSyncRepository by lazy { InMemoryIncrementalSyncRepository() }

    private val slowSyncRepository: SlowSyncRepository by lazy { InMemorySlowSyncRepository() }

    private val eventGatherer: EventGatherer get() = EventGathererImpl(eventRepository, incrementalSyncRepository)

    private val eventProcessor: EventProcessor
        get() = EventProcessorImpl(
            eventRepository,
            conversationEventReceiver, userEventReceiver, featureConfigEventReceiver
        )

    private val syncCriteriaProvider: SyncCriteriaProvider
        get() = SyncCriteriaProviderImpl(clientRepository, logoutRepository)

    val syncManager: SyncManager by lazy {
        incrementalSyncManager
        slowSyncManager
        SyncManagerImpl(
            slowSyncRepository,
            incrementalSyncRepository
        )
    }

    private val syncConversations: SyncConversationsUseCase
        get() = SyncConversationsUseCase(conversationRepository)

    internal val syncConnections: SyncConnectionsUseCase
        get() = SyncConnectionsUseCaseImpl(
            connectionRepository = connectionRepository
        )

    private val syncSelfUser: SyncSelfUserUseCase get() = SyncSelfUserUseCase(userRepository)
    private val syncContacts: SyncContactsUseCase get() = SyncContactsUseCaseImpl(userRepository)

    private val syncSelfTeamUseCase: SyncSelfTeamUseCase
        get() = SyncSelfTeamUseCaseImpl(
            userRepository = userRepository,
            teamRepository = teamRepository
        )

    val joinExistingMLSConversations: JoinExistingMLSConversationsUseCase
        get() = JoinExistingMLSConversationsUseCase(conversationRepository)

    private val slowSyncWorker: SlowSyncWorker by lazy {
        SlowSyncWorkerImpl(
            syncSelfUser,
            syncFeatureConfigsUseCase,
            syncConversations,
            syncConnections,
            syncSelfTeamUseCase,
            syncContacts,
            joinExistingMLSConversations
        )
    }

    private val slowSyncManager: SlowSyncManager by lazy {
        SlowSyncManager(syncCriteriaProvider, slowSyncRepository, slowSyncWorker)
    }

    private val incrementalSyncWorker: IncrementalSyncWorker by lazy {
        IncrementalSyncWorkerImpl(eventGatherer, eventProcessor)
    }

    private val incrementalSyncManager by lazy {
        IncrementalSyncManager(slowSyncRepository, incrementalSyncWorker, incrementalSyncRepository)
    }

    private val timeParser: TimeParser = TimeParserImpl()

    private val eventRepository: EventRepository
        get() = EventDataSource(
            authenticatedDataSourceSet.authenticatedNetworkContainer.notificationApi, eventInfoStorage, clientRepository
        )

    internal val keyPackageManager: KeyPackageManager =
        KeyPackageManagerImpl(
            incrementalSyncRepository,
            lazy { keyPackageRepository },
            lazy { client.refillKeyPackages }
        )

    val qualifiedIdMapper: QualifiedIdMapper get() = MapperProvider.qualifiedIdMapper(userRepository)

    val federatedIdMapper: FederatedIdMapper get() = MapperProvider.federatedIdMapper(userRepository, qualifiedIdMapper, globalPreferences)

    private val callManager: Lazy<CallManager> = lazy {
        globalCallManager.getCallManagerForClient(
            userId = userId,
            callRepository = callRepository,
            userRepository = userRepository,
            clientRepository = clientRepository,
            conversationRepository = conversationRepository,
            messageSender = messageSender,
            federatedIdMapper = federatedIdMapper,
            qualifiedIdMapper = qualifiedIdMapper
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
            persistMessage,
            messageRepository,
            assetRepository,
            conversationRepository,
            mlsConversationRepository,
            userRepository,
            callManager,
            messageTextEditHandler,
            userConfigRepository,
            EphemeralNotificationsManager
        )
    }

    private val userEventReceiver: UserEventReceiver
        get() = UserEventReceiverImpl(
            connectionRepository,
            logout,
            clientRepository,
            sessionRepository
        )

    private val featureConfigEventReceiver: FeatureConfigEventReceiver
        get() = FeatureConfigEventReceiverImpl(userConfigRepository, userRepository, kaliumConfigs)

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
            mlsClientProvider,
            authenticatedDataSourceSet.userDatabaseProvider.metadataDAO,
        )

    private val logoutRepository: LogoutRepository = LogoutDataSource(authenticatedDataSourceSet.authenticatedNetworkContainer.logoutApi)

    val observeSyncState: ObserveSyncStateUseCase
        get() = ObserveSyncStateUseCase(slowSyncRepository, incrementalSyncRepository)

    val setConnectionPolicy: SetConnectionPolicyUseCase
        get() = SetConnectionPolicyUseCase(incrementalSyncRepository)

    val client: ClientScope
        get() = ClientScope(
            clientRepository,
            preKeyRepository,
            keyPackageRepository,
            keyPackageLimitsProvider,
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
            connectionRepository,
            userId,
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
            timeParser,
            kaliumFileSystem
        )
    val users: UserScope
        get() = UserScope(
            userRepository,
            publicUserRepository,
            syncManager,
            assetRepository,
            teamRepository,
            connectionRepository,
            qualifiedIdMapper,
            sessionRepository,
            userId,
        )
    val logout: LogoutUseCase
        get() = LogoutUseCaseImpl(
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
    val observeFileSharingStatus: ObserveFileSharingStatusUseCase get() = ObserveFileSharingStatusUseCaseImpl(userConfigRepository)
    val isMLSEnabled: IsMLSEnabledUseCase get() = IsMLSEnabledUseCaseImpl(userConfigRepository)

    internal val syncFeatureConfigsUseCase: SyncFeatureConfigsUseCase
        get() = SyncFeatureConfigsUseCaseImpl(
            userConfigRepository,
            featureConfigRepository,
            userRepository,
            isFileSharingEnabled,
            kaliumConfigs
        )

    val team: TeamScope get() = TeamScope(userRepository, teamRepository)

    val calls: CallsScope
        get() = CallsScope(
            callManager,
            callRepository,
            conversationRepository,
            userRepository,
            flowManagerService,
            mediaManagerService,
        )

    val connection: ConnectionScope get() = ConnectionScope(connectionRepository, conversationRepository)

    val kaliumFileSystem: KaliumFileSystem by lazy {
        // Create the cache and asset storage directories
        KaliumFileSystemImpl(dataStoragePaths).also {
            if (!it.exists(dataStoragePaths.cachePath.value.toPath()))
                it.createDirectories(dataStoragePaths.cachePath.value.toPath())
            if (!it.exists(dataStoragePaths.assetStoragePath.value.toPath()))
                it.createDirectories(dataStoragePaths.assetStoragePath.value.toPath())
        }
    }
}
