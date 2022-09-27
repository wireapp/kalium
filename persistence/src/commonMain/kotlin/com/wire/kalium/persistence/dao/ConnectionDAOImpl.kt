package com.wire.kalium.persistence.dao

import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToList
import com.wire.kalium.persistence.Connection as SQLDelightConnection
import com.wire.kalium.persistence.ConnectionsQueries
import com.wire.kalium.persistence.ConversationsQueries
import com.wire.kalium.persistence.util.requireField
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
    @Suppress("FunctionParameterNaming", "LongParameterList")
    fun toModel(
        from_id: String,
        conversation_id: String,
        qualified_conversation: QualifiedIDEntity,
        to_id: String,
        last_update: String,
        qualified_to: QualifiedIDEntity,
        status: ConnectionEntity.State,
        should_notify: Boolean?,
        qualified_id: QualifiedIDEntity?,
        name: String?,
        handle: String?,
        email: String?,
        phone: String?,
        accent_id: Int?,
        team: String?,
        connection_status: ConnectionEntity.State?,
        preview_asset_id: QualifiedIDEntity?,
        complete_asset_id: QualifiedIDEntity?,
        user_availability_status: UserAvailabilityStatusEntity?,
        user_type: UserTypeEntity?,
        bot_service: BotEntity?,
        deleted: Boolean?
    ): ConnectionEntity = ConnectionEntity(
        conversationId = conversation_id,
        from = from_id,
        lastUpdate = last_update,
        qualifiedConversationId = qualified_conversation,
        qualifiedToId = qualified_to,
        status = status,
        toId = to_id,
        shouldNotify = should_notify,
        otherUser = if (qualified_id != null) UserEntity(
            id = qualified_id,
            name = name,
            handle = handle,
            email = email,
            phone = phone,
            accentId = accent_id.requireField("accent_id"),
            team = team,
            connectionStatus = connection_status.requireField("connection_status"),
            previewAssetId = preview_asset_id,
            completeAssetId = complete_asset_id,
            availabilityStatus = user_availability_status.requireField("user_availability_status"),
            userType = user_type.requireField("user_type"),
            botService = bot_service,
            deleted = deleted.requireField("deleted"),
        ) else null
    )

}

class ConnectionDAOImpl(
    private val connectionsQueries: ConnectionsQueries,
    private val conversationsQueries: ConversationsQueries
) : ConnectionDAO {

    private val connectionMapper = ConnectionMapper()
    override suspend fun getConnections(): Flow<List<ConnectionEntity>> {
        return connectionsQueries.getConnections()
            .asFlow()
            .mapToList()
            .map { it.map(connectionMapper::toModel) }
    }

    override suspend fun getConnectionRequests(): Flow<List<ConnectionEntity>> {
        return connectionsQueries.selectConnectionRequests(connectionMapper::toModel)
            .asFlow()
            .mapToList()
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
        return connectionsQueries.selectConnectionsForNotification(connectionMapper::toModel)
            .asFlow()
            .mapToList()
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
