package com.wire.kalium.logic.di

import com.wire.kalium.logic.configuration.ClientConfig
import com.wire.kalium.logic.configuration.ServerConfigMapper
import com.wire.kalium.logic.configuration.ServerConfigMapperImpl
import com.wire.kalium.logic.data.asset.AssetMapper
import com.wire.kalium.logic.data.asset.AssetMapperImpl
import com.wire.kalium.logic.data.call.CallMapper
import com.wire.kalium.logic.data.client.ClientMapper
import com.wire.kalium.logic.data.connection.ConnectionMapper
import com.wire.kalium.logic.data.connection.ConnectionMapperImpl
import com.wire.kalium.logic.data.connection.ConnectionStatusMapper
import com.wire.kalium.logic.data.connection.ConnectionStatusMapperImpl
import com.wire.kalium.logic.data.conversation.ConversationMapper
import com.wire.kalium.logic.data.conversation.ConversationMapperImpl
import com.wire.kalium.logic.data.conversation.ConversationStatusMapper
import com.wire.kalium.logic.data.conversation.ConversationStatusMapperImpl
import com.wire.kalium.logic.data.conversation.MemberMapper
import com.wire.kalium.logic.data.conversation.MemberMapperImpl
import com.wire.kalium.logic.data.event.EventMapper
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.id.IdMapperImpl
import com.wire.kalium.logic.data.location.LocationMapper
import com.wire.kalium.logic.data.message.MessageMapper
import com.wire.kalium.logic.data.message.MessageMapperImpl
import com.wire.kalium.logic.data.message.SendMessageFailureMapper
import com.wire.kalium.logic.data.message.SendMessageFailureMapperImpl
import com.wire.kalium.logic.data.prekey.PreKeyMapper
import com.wire.kalium.logic.data.prekey.PreKeyMapperImpl
import com.wire.kalium.logic.data.prekey.remote.PreKeyListMapper
import com.wire.kalium.logic.data.user.other.OtherUserMapper
import com.wire.kalium.logic.data.user.other.OtherUserMapperImpl
import com.wire.kalium.logic.data.session.SessionMapper
import com.wire.kalium.logic.data.session.SessionMapperImpl
import com.wire.kalium.logic.data.team.TeamMapper
import com.wire.kalium.logic.data.team.TeamMapperImpl
import com.wire.kalium.logic.data.user.AvailabilityStatusMapper
import com.wire.kalium.logic.data.user.AvailabilityStatusMapperImpl
import com.wire.kalium.logic.data.user.ConnectionStateMapper
import com.wire.kalium.logic.data.user.ConnectionStateMapperImpl
import com.wire.kalium.logic.data.user.mapper.UserMapper
import com.wire.kalium.logic.data.user.mapper.UserMapperImpl
import com.wire.kalium.logic.data.user.mapper.UserTypeMapperImpl

internal object MapperProvider {
    fun idMapper(): IdMapper = IdMapperImpl()
    fun serverConfigMapper(): ServerConfigMapper = ServerConfigMapperImpl()
    fun sessionMapper(): SessionMapper = SessionMapperImpl(serverConfigMapper(), idMapper())
    fun availabilityStatusMapper(): AvailabilityStatusMapper = AvailabilityStatusMapperImpl()
    fun connectionStateMapper(): ConnectionStateMapper = ConnectionStateMapperImpl()
    fun userMapper(): UserMapper = UserMapperImpl(idMapper())
    fun teamMapper(): TeamMapper = TeamMapperImpl()
    fun messageMapper(): MessageMapper = MessageMapperImpl(idMapper(), memberMapper())
    fun memberMapper(): MemberMapper = MemberMapperImpl(idMapper())
    fun conversationMapper(): ConversationMapper = ConversationMapperImpl(idMapper(), ConversationStatusMapperImpl(), UserTypeMapperImpl())
    fun publicUserMapper(): OtherUserMapper = OtherUserMapperImpl(idMapper())
    fun sendMessageFailureMapper(): SendMessageFailureMapper = SendMessageFailureMapperImpl()
    fun assetMapper(): AssetMapper = AssetMapperImpl()
    fun eventMapper(): EventMapper = EventMapper(idMapper(), memberMapper(), connectionMapper())
    fun preyKeyMapper(): PreKeyMapper = PreKeyMapperImpl()
    fun preKeyListMapper(): PreKeyListMapper = PreKeyListMapper(preyKeyMapper())
    fun locationMapper(): LocationMapper = LocationMapper()
    fun clientMapper(clientConfig: ClientConfig): ClientMapper = ClientMapper(preyKeyMapper(), locationMapper(), clientConfig)
    fun conversationStatusMapper(): ConversationStatusMapper = ConversationStatusMapperImpl()
    fun callMapper(): CallMapper = CallMapper()
    fun connectionStatusMapper(): ConnectionStatusMapper = ConnectionStatusMapperImpl()
    fun connectionMapper(): ConnectionMapper = ConnectionMapperImpl(idMapper(), connectionStatusMapper(), publicUserMapper())
}
