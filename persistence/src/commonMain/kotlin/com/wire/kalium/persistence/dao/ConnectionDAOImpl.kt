package com.wire.kalium.persistence.dao

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.wire.kalium.persistence.Connection as SQLDelightConnection
import com.wire.kalium.persistence.ConnectionsQueries
import com.wire.kalium.persistence.ConversationsQueries
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
        shouldNotify = state.should_notify
    )
}

class ConnectionDAOImpl(
    private val connectionsQueries: ConnectionsQueries,
    private val conversationsQueries: ConversationsQueries
) : ConnectionDAO {

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

    override suspend fun deleteConnectionDataAndConversation(conversationId: QualifiedIDEntity) {
        connectionsQueries.transaction {
            connectionsQueries.deleteConnection(conversationId)
            conversationsQueries.deleteConversation(conversationId)
        }
    }

    override suspend fun getConnectionRequestsForNotification(): Flow<List<ConnectionEntity>> {
        return connectionsQueries.selectConnectionsForNotification()
            .asFlow()
            .mapToList()
            .map { it.map(connectionMapper::toModel) }
    }

    override suspend fun updateNotificationFlag(flag: Boolean, userId: QualifiedIDEntity) {
        connectionsQueries.updateNotificationFlag(flag, userId)
    }

    override suspend fun updateAllNotificationFlags(flag: Boolean) {
        connectionsQueries.transaction {
            connectionsQueries.selectConnectionRequests()
                .executeAsList()
                .forEach { connectionsQueries.updateNotificationFlag(flag, it.qualified_to) }
        }
    }
}
