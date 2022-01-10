package com.wire.kalium.logic.feature

import com.wire.kalium.logic.AuthenticatedDataSourceSet
import com.wire.kalium.logic.configuration.ClientConfig
import com.wire.kalium.logic.data.client.ClientMapper
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.client.ClientRepositoryImpl
import com.wire.kalium.logic.data.client.remote.ClientRemoteDataSource
import com.wire.kalium.logic.data.client.remote.ClientRemoteDataSourceImpl
import com.wire.kalium.logic.data.conversation.ConversationMapper
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.location.LocationMapper
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.prekey.PreKeyMapper
import com.wire.kalium.logic.data.session.SessionMapper
import com.wire.kalium.logic.data.session.SessionMapperImpl
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.session.SessionRepositoryImpl
import com.wire.kalium.logic.feature.auth.AuthSession
import com.wire.kalium.logic.feature.client.ClientScope
import com.wire.kalium.logic.feature.conversation.ConversationScope
import com.wire.kalium.logic.feature.message.MessageScope
import com.wire.kalium.logic.feature.session.SessionScope
import com.wire.kalium.persistence.client.SessionLocalDataSource

class UserSessionScope(
    private val userSession: AuthSession,
    private val authenticatedDataSourceSet: AuthenticatedDataSourceSet,
    // TODO: change this class so there is no need to pass clientConfig & sessionLocalDataSource
    private val clientConfig: ClientConfig,
    private val sessionLocalDataSource: SessionLocalDataSource
) {

    private val sessionMapper: SessionMapper get() = SessionMapperImpl()
    private val sessionRepository: SessionRepository
        get() = SessionRepositoryImpl(
            sessionMapper = sessionMapper,
            sessionLocalDataSource = sessionLocalDataSource
        )

    private val conversationMapper: ConversationMapper get() = ConversationMapper()

    private val conversationRepository: ConversationRepository
        get() = ConversationRepository(authenticatedDataSourceSet.authenticatedNetworkContainer.conversationApi, conversationMapper)

    private val messageRepository: MessageRepository
        get() = MessageRepository(authenticatedDataSourceSet.authenticatedNetworkContainer.messageApi)

    private val preyKeyMapper: PreKeyMapper get() = PreKeyMapper()
    private val locationMapper: LocationMapper get() = LocationMapper()
    private val clientMapper: ClientMapper get() = ClientMapper(preyKeyMapper, locationMapper, clientConfig)

    private val clientRemoteDataSource: ClientRemoteDataSource
        get() = ClientRemoteDataSourceImpl(
            authenticatedDataSourceSet.authenticatedNetworkContainer.clientApi,
            clientMapper
        )

    private val clientRepository: ClientRepository
        get() = ClientRepositoryImpl(clientRemoteDataSource)

    val client: ClientScope get() = ClientScope(clientRepository)
    val conversations: ConversationScope get() = ConversationScope(conversationRepository)
    val messages: MessageScope get() = MessageScope(messageRepository)
    val session: SessionScope get() = SessionScope(sessionRepository)
}
