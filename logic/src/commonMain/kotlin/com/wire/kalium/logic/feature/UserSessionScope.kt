package com.wire.kalium.logic.feature

import com.wire.kalium.logic.AuthenticatedDataSourceSet
import com.wire.kalium.logic.configuration.ClientConfig
import com.wire.kalium.logic.data.client.ClientMapper
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.client.ClientRepositoryImpl
import com.wire.kalium.logic.data.client.remote.ClientRemoteDataSource
import com.wire.kalium.logic.data.client.remote.ClientRemoteDataSourceImpl
import com.wire.kalium.logic.data.conversation.ConversationDataSource
import com.wire.kalium.logic.data.conversation.ConversationMapper
import com.wire.kalium.logic.data.conversation.ConversationMapperImpl
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.MemberMapper
import com.wire.kalium.logic.data.conversation.MemberMapperImpl
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.id.IdMapperImpl
import com.wire.kalium.logic.data.location.LocationMapper
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.prekey.PreKeyMapper
import com.wire.kalium.logic.feature.auth.AuthSession
import com.wire.kalium.logic.feature.client.ClientScope
import com.wire.kalium.logic.feature.conversation.ConversationScope
import com.wire.kalium.logic.feature.message.MessageScope
import com.wire.kalium.persistence.client.ClientRegistrationStorage
import com.wire.kalium.persistence.client.ClientRegistrationStorageImpl
import com.wire.kalium.persistence.event.EventInfoStorage
import com.wire.kalium.persistence.kmm_settings.EncryptedSettingsHolder
import com.wire.kalium.persistence.kmm_settings.KaliumPreferencesSettings

expect class UserSessionScope : UserSessionScopeCommon

abstract class UserSessionScopeCommon(
    private val session: AuthSession,
    private val authenticatedDataSourceSet: AuthenticatedDataSourceSet
) {

    protected abstract val encryptedSettingsHolder: EncryptedSettingsHolder
    private val userPreferencesSettings = KaliumPreferencesSettings(encryptedSettingsHolder.encryptedSettings)
    private val eventInfoStorage = EventInfoStorage(userPreferencesSettings)

    private val idMapper: IdMapper get() = IdMapperImpl()
    private val memberMapper: MemberMapper get() = MemberMapperImpl(idMapper)
    private val conversationMapper: ConversationMapper get() = ConversationMapperImpl(idMapper, memberMapper)

    private val conversationRepository: ConversationRepository
        get() = ConversationDataSource(
            authenticatedDataSourceSet.authenticatedNetworkContainer.conversationApi,
            authenticatedDataSourceSet.authenticatedNetworkContainer.clientApi, idMapper, conversationMapper, memberMapper
        )

    private val messageRepository: MessageRepository
        get() = MessageRepository(authenticatedDataSourceSet.authenticatedNetworkContainer.messageApi)

    protected abstract val clientConfig: ClientConfig

    private val preyKeyMapper: PreKeyMapper get() = PreKeyMapper()
    private val locationMapper: LocationMapper get() = LocationMapper()
    private val clientMapper: ClientMapper get() = ClientMapper(preyKeyMapper, locationMapper, clientConfig)

    private val clientRemoteDataSource: ClientRemoteDataSource
        get() = ClientRemoteDataSourceImpl(
            authenticatedDataSourceSet.authenticatedNetworkContainer.clientApi,
            clientMapper
        )

    private val clientRegistrationStorage: ClientRegistrationStorage = ClientRegistrationStorageImpl(userPreferencesSettings)

    private val clientRepository: ClientRepository
        get() = ClientRepositoryImpl(clientRemoteDataSource, clientRegistrationStorage)

    val client: ClientScope get() = ClientScope(clientRepository)
    val conversations: ConversationScope get() = ConversationScope(conversationRepository)
    val messages: MessageScope get() = MessageScope(messageRepository)
}
