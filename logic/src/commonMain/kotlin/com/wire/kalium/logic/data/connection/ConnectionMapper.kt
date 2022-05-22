package com.wire.kalium.logic.data.connection

import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.persistence.dao.ConnectionEntity

internal typealias ApiConnection = com.wire.kalium.network.api.user.connection.Connection

interface ConnectionMapper {
    fun fromApiToDao(state: ApiConnection): ConnectionEntity
}

internal class ConnectionMapperImpl(private val idMapper: IdMapper, private val statusMapper: ConnectionStatusMapper) : ConnectionMapper {
    override fun fromApiToDao(state: ApiConnection): ConnectionEntity = ConnectionEntity(
         conversationId = state.conversationId,
         from = state.from,
         lastUpdate = state.lastUpdate,
         qualifiedConversationId = idMapper.fromApiToDao(state.qualifiedConversationId),
         qualifiedToId = idMapper.fromApiToDao(state.qualifiedToId),
         status = statusMapper.fromApiToDao(state.status),
         toId = state.toId,
     )

}

