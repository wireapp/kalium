package com.wire.kalium.logic.feature

import com.wire.kalium.logic.AuthenticatedDataSourceSet
import com.wire.kalium.logic.configuration.ClientConfig
import com.wire.kalium.logic.configuration.notification.NotificationTokenDataSource
import com.wire.kalium.logic.data.asset.AssetDataSource
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.call.CallDataSource
import com.wire.kalium.logic.data.call.CallMapper
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
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.keypackage.KeyPackageDataSource
import com.wire.kalium.logic.data.keypackage.KeyPackageRepository
import com.wire.kalium.logic.data.logout.LogoutDataSource
import com.wire.kalium.logic.data.logout.LogoutRepository
import com.wire.kalium.logic.data.message.MessageDataSource
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.message.ProtoContentMapper
import com.wire.kalium.logic.data.prekey.PreKeyDataSource
import com.wire.kalium.logic.data.prekey.PreKeyRepository
import com.wire.kalium.logic.data.prekey.remote.PreKeyRemoteDataSource
import com.wire.kalium.logic.data.prekey.remote.PreKeyRemoteRepository
import com.wire.kalium.logic.data.publicuser.SearchUserRepository
import com.wire.kalium.logic.data.publicuser.SearchUserRepositoryImpl
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.team.TeamDataSource
import com.wire.kalium.logic.data.team.TeamRepository
import com.wire.kalium.logic.data.user.UserDataSource
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.auth.LogoutUseCase
import com.wire.kalium.logic.feature.call.CallsScope
import com.wire.kalium.logic.feature.call.GlobalCallManager
import com.wire.kalium.logic.feature.client.ClientScope
import com.wire.kalium.logic.feature.connection.ConnectionScope
import com.wire.kalium.logic.feature.conversation.ConversationScope
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
import com.wire.kalium.logic.feature.user.UserScope
import com.wire.kalium.logic.sync.ConversationEventReceiver
import com.wire.kalium.logic.sync.ListenToEventsUseCase
import com.wire.kalium.logic.sync.SyncManager
import com.wire.kalium.logic.sync.SyncPendingEventsUseCase
import com.wire.kalium.logic.util.TimeParser
import com.wire.kalium.logic.util.TimeParserImpl
import com.wire.kalium.persistence.client.ClientRegistrationStorage
import com.wire.kalium.persistence.client.ClientRegistrationStorageImpl
import com.wire.kalium.persistence.client.TokenStorage
import com.wire.kalium.persistence.client.TokenStorageImpl
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
            authenticatedDataSourceSet.authenticatedNetworkContainer.connectionApi
        )

    private val publicUserRepository: SearchUserRepository
        get() = SearchUserRepositoryImpl(
            userDatabaseProvider.userDAO,
            authenticatedDataSourceSet.authenticatedNetworkContainer.userSearchApi,
            authenticatedDataSourceSet.authenticatedNetworkContainer.userDetailsApi
        )

    private val callRepository: CallRepository
        get() = CallDataSource(
            callApi = authenticatedDataSourceSet.authenticatedNetworkContainer.callApi
        )

    protected abstract val clientConfig: ClientConfig

    private val tokenStorage: TokenStorage
        get() = TokenStorageImpl(globalPreferences)

    private val clientRemoteRepository: ClientRemoteRepository
        get() = ClientRemoteDataSource(
            authenticatedDataSourceSet.authenticatedNetworkContainer.clientApi, clientConfig
        )

    private val clientRegistrationStorage: ClientRegistrationStorage
        get() = ClientRegistrationStorageImpl(userPreferencesSettings)

    private val clientRepository: ClientRepository
        get() = ClientDataSource(clientRemoteRepository, clientRegistrationStorage, userDatabaseProvider.clientDAO)

    private val messageSendFailureHandler: MessageSendFailureHandler
        get() = MessageSendFailureHandlerImpl(userRepository, clientRepository)

    private val sessionEstablisher: SessionEstablisher
        get() = SessionEstablisherImpl(authenticatedDataSourceSet.proteusClient, preKeyRepository)

    private val messageEnvelopeCreator: MessageEnvelopeCreator
        get() = MessageEnvelopeCreatorImpl(authenticatedDataSourceSet.proteusClient, protoContentMapper)

    private val mlsMessageCreator: MLSMessageCreator
        get() = MLSMessageCreatorImpl(mlsClientProvider, protoContentMapper)

    private val messageSendingScheduler: MessageSendingScheduler
        get() = authenticatedDataSourceSet.workScheduler

    // TODO code duplication, can't we get the MessageSender from the message scope?
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

    val syncManager: SyncManager get() = authenticatedDataSourceSet.syncManager

    private val timeParser : TimeParser = TimeParserImpl()

    private val eventRepository: EventRepository
        get() = EventDataSource(
            authenticatedDataSourceSet.authenticatedNetworkContainer.notificationApi, eventInfoStorage, clientRepository
        )

    private val callMapper: CallMapper
        get() = CallMapper()

    private val callManager by lazy {
        globalCallManager.getCallManagerForClient(
            userId = userId,
            callRepository = callRepository,
            userRepository = userRepository,
            clientRepository = clientRepository,
            callMapper = callMapper,
            messageSender = messageSender
        )
    }
    protected abstract val protoContentMapper: ProtoContentMapper
    private val conversationEventReceiver: ConversationEventReceiver
        get() = ConversationEventReceiver(
            authenticatedDataSourceSet.proteusClient,
            messageRepository,
            conversationRepository,
            mlsConversationRepository,
            userRepository,
            protoContentMapper,
            callManager
        )

    private val preKeyRemoteRepository: PreKeyRemoteRepository get() = PreKeyRemoteDataSource(authenticatedDataSourceSet.authenticatedNetworkContainer.preKeyApi)
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
    val listenToEvents: ListenToEventsUseCase
        get() = ListenToEventsUseCase(syncManager, eventRepository, conversationEventReceiver)
    val syncPendingEvents: SyncPendingEventsUseCase
        get() = SyncPendingEventsUseCase(syncManager, eventRepository, conversationEventReceiver)
    val client: ClientScope get() = ClientScope(clientRepository, preKeyRepository, keyPackageRepository, mlsClientProvider,notificationTokenRepository)
    val conversations: ConversationScope get() = ConversationScope(conversationRepository, userRepository, syncManager)
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
    val users: UserScope get() = UserScope(userRepository, publicUserRepository, syncManager, assetRepository)
    val logout: LogoutUseCase
        get() = LogoutUseCase(
            logoutRepository,
            sessionRepository,
            userId,
            authenticatedDataSourceSet,
            clientRepository,
            mlsClientProvider
        )

    val team: TeamScope get() = TeamScope(userRepository, teamRepository, syncManager)

    val calls: CallsScope get() = CallsScope(callManager, syncManager)

    val connection: ConnectionScope get() = ConnectionScope(connectionRepository)

}
