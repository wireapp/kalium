package com.wire.kalium.logic.data.connection

import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.user.Connection
import com.wire.kalium.network.api.user.connection.ConnectionDTO
import com.wire.kalium.persistence.dao.ConnectionEntity

interface ConnectionMapper {
    fun fromApiToDao(state: ConnectionDTO): ConnectionEntity
    fun fromDaoToModel(state: ConnectionEntity): Connection
    fun fromApiToModel(state: ConnectionDTO): Connection
    fun modelToDao(state: Connection): ConnectionEntity
}

internal class ConnectionMapperImpl(private val idMapper: IdMapper, private val statusMapper: ConnectionStatusMapper) : ConnectionMapper {
    override fun fromApiToDao(state: ConnectionDTO): ConnectionEntity = ConnectionEntity(
        conversationId = state.conversationId,
        from = state.from,
        lastUpdate = state.lastUpdate,
        qualifiedConversationId = idMapper.fromApiToDao(state.qualifiedConversationId),
        qualifiedToId = idMapper.fromApiToDao(state.qualifiedToId),
        status = statusMapper.fromApiToDao(state.status),
        toId = state.toId,
    )

    override fun fromDaoToModel(state: ConnectionEntity): Connection = Connection(
        conversationId = state.conversationId,
        from = state.from,
        lastUpdate = state.lastUpdate,
        qualifiedConversationId = idMapper.fromDaoModel(state.qualifiedConversationId),
        qualifiedToId = idMapper.fromDaoModel(state.qualifiedToId),
        status = statusMapper.fromDaoModel(state.status),
        toId = state.toId,
    )

    override fun fromApiToModel(state: ConnectionDTO): Connection = Connection(
        conversationId = state.conversationId,
        from = state.from,
        lastUpdate = state.lastUpdate,
        qualifiedConversationId = idMapper.fromApiModel(state.qualifiedConversationId),
        qualifiedToId = idMapper.fromApiModel(state.qualifiedToId),
        status = statusMapper.fromApiModel(state.status),
        toId = state.toId,
    )

    override fun modelToDao(state: Connection): ConnectionEntity = ConnectionEntity(
        conversationId = state.conversationId,
        from = state.from,
        lastUpdate = state.lastUpdate,
        qualifiedConversationId = idMapper.toDaoModel(state.qualifiedConversationId),
        qualifiedToId = idMapper.toDaoModel(state.qualifiedToId),
        status = statusMapper.toDaoModel(state.status),
        toId = state.toId,
    )

}

