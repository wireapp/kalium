package com.wire.kalium.logic.data.connection

import com.wire.kalium.logic.data.conversation.Conversation.ProtocolInfo
import com.wire.kalium.logic.data.conversation.ConversationDetails
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.data.publicuser.PublicUserMapper
import com.wire.kalium.logic.data.user.Connection
import com.wire.kalium.logic.data.user.type.DomainUserTypeMapper
import com.wire.kalium.logic.data.user.type.UserType
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.network.api.base.authenticated.connection.ConnectionDTO
import com.wire.kalium.persistence.dao.ConnectionEntity
import com.wire.kalium.util.DateTimeUtil.toIsoDateTimeString
import com.wire.kalium.util.time.UNIX_FIRST_DATE
import kotlinx.datetime.Instant
import kotlinx.datetime.toInstant

interface ConnectionMapper {
    fun fromApiToDao(state: ConnectionDTO): ConnectionEntity
    fun fromDaoToModel(connection: ConnectionEntity): Connection
    fun fromDaoToConversationDetails(connection: ConnectionEntity): ConversationDetails
    fun fromApiToModel(state: ConnectionDTO): Connection
    fun modelToDao(state: Connection): ConnectionEntity
}

internal class ConnectionMapperImpl(
    private val idMapper: IdMapper = MapperProvider.idMapper(),
    private val statusMapper: ConnectionStatusMapper = MapperProvider.connectionStatusMapper(),
    private val publicUserMapper: PublicUserMapper = MapperProvider.publicUserMapper(),
    private val userTypeMapper: DomainUserTypeMapper = MapperProvider.userTypeMapper(),
) : ConnectionMapper {
    override fun fromApiToDao(state: ConnectionDTO): ConnectionEntity = ConnectionEntity(
        conversationId = state.conversationId,
        from = state.from,
        lastUpdateDate = state.lastUpdate.safeDateToInstant(),
        qualifiedConversationId = idMapper.fromApiToDao(state.qualifiedConversationId),
        qualifiedToId = idMapper.fromApiToDao(state.qualifiedToId),
        status = statusMapper.fromApiToDao(state.status),
        toId = state.toId,
    )

    override fun fromDaoToModel(connection: ConnectionEntity) = with(connection) {
        Connection(
            conversationId = conversationId,
            from = from,
            lastUpdate = lastUpdateDate.toIsoDateTimeString(),
            qualifiedConversationId = qualifiedConversationId.toModel(),
            qualifiedToId = qualifiedToId.toModel(),
            status = statusMapper.fromDaoModel(status),
            toId = toId,
            fromUser = otherUser?.let { publicUserMapper.fromDaoModelToPublicUser(it) }
        )
    }

    override fun fromDaoToConversationDetails(connection: ConnectionEntity): ConversationDetails = with(connection) {
        ConversationDetails.Connection(
            conversationId = qualifiedConversationId.toModel(),
            otherUser = otherUser?.let { publicUserMapper.fromDaoModelToPublicUser(it) },
            userType = otherUser?.let { userTypeMapper.fromUserTypeEntity(it.userType) } ?: UserType.GUEST,
            lastModifiedDate = lastUpdateDate.toIsoDateTimeString(),
            connection = fromDaoToModel(this),
            protocolInfo = ProtocolInfo.Proteus,
            // TODO(qol): need to be refactored
            access = emptyList(),
            accessRole = emptyList()
        )
    }

    override fun fromApiToModel(state: ConnectionDTO): Connection = Connection(
        conversationId = state.conversationId,
        from = state.from,
        lastUpdate = state.lastUpdate,
        qualifiedConversationId = state.qualifiedConversationId.toModel(),
        qualifiedToId = state.qualifiedToId.toModel(),
        status = statusMapper.fromApiModel(state.status),
        toId = state.toId
    )

    override fun modelToDao(state: Connection): ConnectionEntity = ConnectionEntity(
        conversationId = state.conversationId,
        from = state.from,
        lastUpdateDate = state.lastUpdate.safeDateToInstant(),
        qualifiedConversationId = state.qualifiedConversationId.toDao(),
        qualifiedToId = state.qualifiedToId.toDao(),
        status = statusMapper.toDaoModel(state.status),
        toId = state.toId,
    )

    private fun String.safeDateToInstant() = takeIf { it.isNotBlank() }?.toInstant() ?: Instant.UNIX_FIRST_DATE
}
