package com.wire.kalium.logic.feature

import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.AuthenticatedDataSourceSet
import com.wire.kalium.logic.configuration.ClientConfig
import com.wire.kalium.logic.data.asset.AssetDataSource
import com.wire.kalium.logic.data.asset.AssetMapper
import com.wire.kalium.logic.data.asset.AssetMapperImpl
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.client.ClientDataSource
import com.wire.kalium.logic.data.client.ClientMapper
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.client.remote.ClientRemoteDataSource
import com.wire.kalium.logic.data.client.remote.ClientRemoteRepository
import com.wire.kalium.logic.data.conversation.ConversationDataSource
import com.wire.kalium.logic.data.conversation.ConversationMapper
import com.wire.kalium.logic.data.conversation.ConversationMapperImpl
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.MemberMapper
import com.wire.kalium.logic.data.conversation.MemberMapperImpl
import com.wire.kalium.logic.data.event.EventDataSource
import com.wire.kalium.logic.data.event.EventMapper
import com.wire.kalium.logic.data.event.EventRepository
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.id.IdMapperImpl
import com.wire.kalium.logic.data.location.LocationMapper
import com.wire.kalium.logic.data.message.MessageDataSource
import com.wire.kalium.logic.data.message.MessageMapper
import com.wire.kalium.logic.data.message.MessageMapperImpl
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.message.ProtoContentMapper
import com.wire.kalium.logic.data.message.SendMessageFailureMapper
import com.wire.kalium.logic.data.message.SendMessageFailureMapperImpl
import com.wire.kalium.logic.data.prekey.PreKeyDataSource
import com.wire.kalium.logic.data.prekey.PreKeyMapper
import com.wire.kalium.logic.data.prekey.PreKeyMapperImpl
import com.wire.kalium.logic.data.prekey.PreKeyRepository
import com.wire.kalium.logic.data.prekey.remote.PreKeyListMapper
import com.wire.kalium.logic.data.prekey.remote.PreKeyRemoteDataSource
import com.wire.kalium.logic.data.prekey.remote.PreKeyRemoteRepository
import com.wire.kalium.logic.data.user.UserDataSource
import com.wire.kalium.logic.data.user.UserMapperImpl
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.auth.AuthSession
import com.wire.kalium.logic.feature.client.ClientScope
import com.wire.kalium.logic.feature.conversation.ConversationScope
import com.wire.kalium.logic.feature.message.MessageScope
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
    private val kaliumLogger: KaliumLogger
) {

    private val encryptedSettingsHolder: EncryptedSettingsHolder = authenticatedDataSourceSet.encryptedSettingsHolder
    private val userPreferencesSettings = authenticatedDataSourceSet.kaliumPreferencesSettings
    private val eventInfoStorage: EventInfoStorage
        get() = EventInfoStorageImpl(userPreferencesSettings)

    private val idMapper: IdMapper get() = IdMapperImpl()
    private val memberMapper: MemberMapper get() = MemberMapperImpl(idMapper)
    private val conversationMapper: ConversationMapper get() = ConversationMapperImpl(idMapper, memberMapper)
    private val userMapper = UserMapperImpl(idMapper)
    private val database: Database = authenticatedDataSourceSet.database

    private val conversationRepository: ConversationRepository
        get() = ConversationDataSource(
            database.conversationDAO,
            authenticatedDataSourceSet.authenticatedNetworkContainer.conversationApi,
            authenticatedDataSourceSet.authenticatedNetworkContainer.clientApi, idMapper, conversationMapper, memberMapper
        )

    private val messageMapper: MessageMapper get() = MessageMapperImpl(idMapper)

    private val sendMessageFailureMapper: SendMessageFailureMapper get() = SendMessageFailureMapperImpl()

    private val messageRepository: MessageRepository
        get() = MessageDataSource(
            authenticatedDataSourceSet.authenticatedNetworkContainer.messageApi,
            database.messageDAO,
            messageMapper,
            idMapper,
            sendMessageFailureMapper
        )

    private val userRepository: UserRepository
        get() = UserDataSource(
            database.userDAO,
            database.metadataDAO,
            authenticatedDataSourceSet.authenticatedNetworkContainer.selfApi,
            authenticatedDataSourceSet.authenticatedNetworkContainer.userDetailsApi,
            idMapper,
            userMapper
        )

    protected abstract val clientConfig: ClientConfig

    private val preyKeyMapper: PreKeyMapper get() = PreKeyMapperImpl()
    private val preKeyListMapper: PreKeyListMapper get() = PreKeyListMapper(preyKeyMapper)
    private val locationMapper: LocationMapper get() = LocationMapper()
    private val clientMapper: ClientMapper get() = ClientMapper(preyKeyMapper, locationMapper, clientConfig)

    private val clientRemoteRepository: ClientRemoteRepository
        get() = ClientRemoteDataSource(
            authenticatedDataSourceSet.authenticatedNetworkContainer.clientApi,
            clientMapper
        )

    private val clientRegistrationStorage: ClientRegistrationStorage
        get() = ClientRegistrationStorageImpl(userPreferencesSettings)

    private val clientRepository: ClientRepository
        get() = ClientDataSource(clientRemoteRepository, clientRegistrationStorage, database.clientDAO, userMapper)

    private val assetMapper: AssetMapper get() = AssetMapperImpl()
    private val assetRepository: AssetRepository
        get() = AssetDataSource(authenticatedDataSourceSet.authenticatedNetworkContainer.assetApi, assetMapper)

    val syncManager: SyncManager get() = authenticatedDataSourceSet.syncManager

    private val eventMapper: EventMapper get() = EventMapper(idMapper)
    private val eventRepository: EventRepository
        get() = EventDataSource(
            authenticatedDataSourceSet.authenticatedNetworkContainer.notificationApi,
            eventInfoStorage,
            clientRepository,
            eventMapper
        )

    protected abstract val protoContentMapper: ProtoContentMapper
    private val conversationEventReceiver: ConversationEventReceiver
        get() = ConversationEventReceiver(
            authenticatedDataSourceSet.proteusClient,
            messageRepository,
            protoContentMapper
        )

    private val preKeyRemoteRepository: PreKeyRemoteRepository
        get() = PreKeyRemoteDataSource(
            authenticatedDataSourceSet.authenticatedNetworkContainer.preKeyApi,
            preKeyListMapper
        )
    private val preKeyRepository: PreKeyRepository
        get() = PreKeyDataSource(
            preKeyRemoteRepository,
            authenticatedDataSourceSet.proteusClient
        )
    val listenToEvents: ListenToEventsUseCase get() = ListenToEventsUseCase(
        syncManager = syncManager,
        eventRepository = eventRepository,
        conversationEventReceiver = conversationEventReceiver,
        kaliumLogger = kaliumLogger
    )
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
    val users: UserScope get() = UserScope(userRepository, syncManager, assetRepository)
}
