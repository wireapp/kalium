package com.wire.kalium.persistence.dao

import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToList
import com.wire.kalium.persistence.Connection as SQLDelightConnection
import com.wire.kalium.persistence.ConnectionsQueries
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private class ConnectionMapper {
    fun toModel(state: SQLDelightConnection): ConnectionEntity = ConnectionEntity(
        conversationId = state.conversation_id,
        from = state.from_id,
        lastUpdate = state.last_update,
        qualifiedConversationId = state.qualified_conversation,
        qualifiedToId = state.qualified_to,
        status = state.status,
        toId = state.to_id,
    )
}

class ConnectionDAOImpl(private val connectionsQueries: ConnectionsQueries) : ConnectionDAO {

    private val connectionMapper = ConnectionMapper()

    override suspend fun getConnectionRequests(): Flow<List<ConnectionEntity>> {
        return connectionsQueries.selectConnectionRequests()
            .asFlow()
            .mapToList()
            .map { it.map(connectionMapper::toModel) }
    }

    override suspend fun insertConnection(connectionEntity: ConnectionEntity) {
        connectionsQueries.insertConnection(
            from_id = connectionEntity.from,
            conversation_id = connectionEntity.conversationId,
            qualified_conversation = connectionEntity.qualifiedConversationId,
            to_id = connectionEntity.toId,
            last_update = connectionEntity.lastUpdate,
            qualified_to = connectionEntity.qualifiedToId,
            status = connectionEntity.status
        )
    }

    override suspend fun insertConnections(users: List<ConnectionEntity>) {
        connectionsQueries.transaction {
            for (connectionEntity: ConnectionEntity in users) {
                connectionsQueries.insertConnection(
                    from_id = connectionEntity.from,
                    conversation_id = connectionEntity.conversationId,
                    qualified_conversation = connectionEntity.qualifiedConversationId,
                    to_id = connectionEntity.toId,
                    last_update = connectionEntity.lastUpdate,
                    qualified_to = connectionEntity.qualifiedToId,
                    status = connectionEntity.status
                )
            }
        }
    }

    override suspend fun updateConnectionLastUpdatedTime(lastUpdate: String, id: String) {
        connectionsQueries.updateConnectionLastUpdated(lastUpdate, id)
    }
}
