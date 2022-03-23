package com.wire.kalium.logic.feature

import com.wire.kalium.logic.AuthenticatedDataSourceSet
import com.wire.kalium.logic.configuration.ClientConfig
import com.wire.kalium.logic.data.asset.AssetDataSource
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.call.CallDataSource
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.client.ClientDataSource
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.client.remote.ClientRemoteDataSource
import com.wire.kalium.logic.data.client.remote.ClientRemoteRepository
import com.wire.kalium.logic.data.conversation.ConversationDataSource
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.event.EventDataSource
import com.wire.kalium.logic.data.event.EventRepository
import com.wire.kalium.logic.data.logout.LogoutDataSource
import com.wire.kalium.logic.data.logout.LogoutRepository
import com.wire.kalium.logic.data.message.MessageDataSource
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.message.ProtoContentMapper
import com.wire.kalium.logic.data.prekey.PreKeyDataSource
import com.wire.kalium.logic.data.prekey.PreKeyRepository
import com.wire.kalium.logic.data.prekey.remote.PreKeyRemoteDataSource
import com.wire.kalium.logic.data.prekey.remote.PreKeyRemoteRepository
import com.wire.kalium.logic.data.publicuser.PublicUserRepository
import com.wire.kalium.logic.data.publicuser.PublicUserRepositoryImpl
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.team.TeamDataSource
import com.wire.kalium.logic.data.team.TeamRepository
import com.wire.kalium.logic.data.user.UserDataSource
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.auth.AuthSession
import com.wire.kalium.logic.feature.auth.LogoutUseCase
import com.wire.kalium.logic.feature.call.CallManager
import com.wire.kalium.logic.feature.client.ClientScope
import com.wire.kalium.logic.feature.conversation.ConversationScope
import com.wire.kalium.logic.feature.message.MessageScope
import com.wire.kalium.logic.feature.team.TeamScope
import com.wire.kalium.logic.feature.user.UserScope
import com.wire.kalium.logic.sync.ConversationEventReceiver
import com.wire.kalium.logic.sync.ListenToEventsUseCase
import com.wire.kalium.logic.sync.SyncManager
import com.wire.kalium.persistence.client.ClientRegistrationStorage
import com.wire.kalium.persistence.client.ClientRegistrationStorageImpl
import com.wire.kalium.persistence.db.Database
import com.wire.kalium.persistence.event.EventInfoStorage
import com.wire.kalium.persistence.event.EventInfoStorageImpl
import com.wire.kalium.persistence.kmm_settings.EncryptedSettingsHolder

expect class UserSessionScope : UserSessionScopeCommon

abstract class UserSessionScopeCommon(
    private val session: AuthSession,
    private val authenticatedDataSourceSet: AuthenticatedDataSourceSet,
    private val sessionRepository: SessionRepository
) {

    private val encryptedSettingsHolder: EncryptedSettingsHolder = authenticatedDataSourceSet.encryptedSettingsHolder
    private val userPreferencesSettings = authenticatedDataSourceSet.kaliumPreferencesSettings
    private val eventInfoStorage: EventInfoStorage
        get() = EventInfoStorageImpl(userPreferencesSettings)

    private val database: Database = authenticatedDataSourceSet.database

    private val conversationRepository: ConversationRepository
        get() = ConversationDataSource(
            userRepository,
            database.conversationDAO,
            authenticatedDataSourceSet.authenticatedNetworkContainer.conversationApi,
            authenticatedDataSourceSet.authenticatedNetworkContainer.clientApi
        )


    private val messageRepository: MessageRepository
        get() = MessageDataSource(
            authenticatedDataSourceSet.authenticatedNetworkContainer.messageApi, database.messageDAO
        )

    private val userRepository: UserRepository
        get() = UserDataSource(
            database.userDAO,
            database.metadataDAO,
            authenticatedDataSourceSet.authenticatedNetworkContainer.selfApi,
            authenticatedDataSourceSet.authenticatedNetworkContainer.userDetailsApi,
            assetRepository
        )

    private val teamRepository: TeamRepository
        get() = TeamDataSource(
            database.userDAO,
            database.teamDAO,
            authenticatedDataSourceSet.authenticatedNetworkContainer.teamsApi
        )

    private val publicUserRepository: PublicUserRepository
        get() = PublicUserRepositoryImpl(
            authenticatedDataSourceSet.authenticatedNetworkContainer.contactSearchApi,
            authenticatedDataSourceSet.authenticatedNetworkContainer.userDetailsApi
        )

    private val callRepository: CallRepository
        get() = CallDataSource(
            callApi = authenticatedDataSourceSet.authenticatedNetworkContainer.callApi
        )

    protected abstract val clientConfig: ClientConfig

    private val clientRemoteRepository: ClientRemoteRepository
        get() = ClientRemoteDataSource(
            authenticatedDataSourceSet.authenticatedNetworkContainer.clientApi, clientConfig
        )

    private val clientRegistrationStorage: ClientRegistrationStorage
        get() = ClientRegistrationStorageImpl(userPreferencesSettings)

    private val clientRepository: ClientRepository
        get() = ClientDataSource(clientRemoteRepository, clientRegistrationStorage, database.clientDAO)

    private val assetRepository: AssetRepository
        get() = AssetDataSource(authenticatedDataSourceSet.authenticatedNetworkContainer.assetApi, database.assetDAO)

    val syncManager: SyncManager get() = authenticatedDataSourceSet.syncManager

    private val eventRepository: EventRepository
        get() = EventDataSource(
            authenticatedDataSourceSet.authenticatedNetworkContainer.notificationApi, eventInfoStorage, clientRepository
        )

    private val callManager by lazy {
        CallManager(
            userRepository = userRepository,
            clientRepository = clientRepository,
            callRepository = callRepository
        )
    }
    protected abstract val protoContentMapper: ProtoContentMapper
    private val conversationEventReceiver: ConversationEventReceiver
        get() = ConversationEventReceiver(
            authenticatedDataSourceSet.proteusClient,
            messageRepository,
            conversationRepository,
            userRepository,
            protoContentMapper,
            callManager
        )

    private val preKeyRemoteRepository: PreKeyRemoteRepository get() = PreKeyRemoteDataSource(authenticatedDataSourceSet.authenticatedNetworkContainer.preKeyApi)
    private val preKeyRepository: PreKeyRepository
        get() = PreKeyDataSource(
            preKeyRemoteRepository, authenticatedDataSourceSet.proteusClient
        )

    private val logoutRepository: LogoutRepository = LogoutDataSource(authenticatedDataSourceSet.authenticatedNetworkContainer.logoutApi)
    val listenToEvents: ListenToEventsUseCase
        get() = ListenToEventsUseCase(syncManager, eventRepository, conversationEventReceiver)
    val client: ClientScope get() = ClientScope(clientRepository, preKeyRepository)
    val conversations: ConversationScope get() = ConversationScope(conversationRepository, syncManager)
    val messages: MessageScope
        get() = MessageScope(
            messageRepository,
            conversationRepository,
            clientRepository,
            authenticatedDataSourceSet.proteusClient,
            preKeyRepository,
            userRepository,
            syncManager
        )
    val users: UserScope get() = UserScope(userRepository, publicUserRepository, syncManager, assetRepository)
    val logout: LogoutUseCase get() = LogoutUseCase(logoutRepository, sessionRepository, session.userId, authenticatedDataSourceSet)

    val team: TeamScope get() = TeamScope(userRepository, teamRepository)
}
