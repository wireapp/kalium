/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

package com.wire.kalium.persistence.dao

import app.cash.sqldelight.coroutines.asFlow
import com.wire.kalium.persistence.ConnectionsQueries
import com.wire.kalium.persistence.ConversationsQueries
import com.wire.kalium.persistence.util.mapToList
import com.wire.kalium.persistence.util.requireField
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.datetime.toInstant
import kotlin.coroutines.CoroutineContext
import com.wire.kalium.persistence.Connection as SQLDelightConnection

private class ConnectionMapper {
    fun toModel(state: SQLDelightConnection): ConnectionEntity = ConnectionEntity(
        conversationId = state.conversation_id,
        from = state.from_id,
        lastUpdateDate = state.last_update_date,
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
        last_update_date: Instant,
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
        bot_service: BotIdEntity?,
        deleted: Boolean?,
        incomplete_metadata: Boolean?,
        expires_at: Instant?,
        defederated: Boolean?,
        supportedProtocols: Set<SupportedProtocolEntity>?,
        oneToOneConversationId: QualifiedIDEntity?
    ): ConnectionEntity = ConnectionEntity(
        conversationId = conversation_id,
        from = from_id,
        lastUpdateDate = last_update_date,
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
            hasIncompleteMetadata = incomplete_metadata.requireField("incomplete_metadata"),
            expiresAt = expires_at,
            defederated = defederated.requireField("defederated"),
            supportedProtocols = supportedProtocols,
            activeOneOnOneConversationId = oneToOneConversationId
        ) else null
    )

}

class ConnectionDAOImpl(
    private val connectionsQueries: ConnectionsQueries,
    private val conversationsQueries: ConversationsQueries,
    private val queriesContext: CoroutineContext
) : ConnectionDAO {

    private val connectionMapper = ConnectionMapper()
    override suspend fun getConnections(): Flow<List<ConnectionEntity>> {
        return connectionsQueries.getConnections()
            .asFlow()
            .flowOn(queriesContext)
            .mapToList()
            .map { it.map(connectionMapper::toModel) }
    }

    override suspend fun getConnection(conversationId: QualifiedIDEntity): ConnectionEntity? = withContext(queriesContext) {
        connectionsQueries.selectConnection(conversationId).executeAsOneOrNull()?.let { connectionMapper.toModel(it) }
    }

    override suspend fun getConnectionRequests(): Flow<List<ConnectionEntity>> {
        return connectionsQueries.selectConnectionRequests(connectionMapper::toModel)
            .asFlow()
            .flowOn(queriesContext)
            .mapToList()
    }

    override suspend fun insertConnection(connectionEntity: ConnectionEntity) = withContext(queriesContext) {
        connectionsQueries.insertConnection(
            from_id = connectionEntity.from,
            conversation_id = connectionEntity.conversationId,
            qualified_conversation = connectionEntity.qualifiedConversationId,
            to_id = connectionEntity.toId,
            last_update_date = connectionEntity.lastUpdateDate,
            qualified_to = connectionEntity.qualifiedToId,
            status = connectionEntity.status
        )
    }

    override suspend fun insertConnections(users: List<ConnectionEntity>) = withContext(queriesContext) {
        connectionsQueries.transaction {
            for (connectionEntity: ConnectionEntity in users) {
                connectionsQueries.insertConnection(
                    from_id = connectionEntity.from,
                    conversation_id = connectionEntity.conversationId,
                    qualified_conversation = connectionEntity.qualifiedConversationId,
                    to_id = connectionEntity.toId,
                    last_update_date = connectionEntity.lastUpdateDate,
                    qualified_to = connectionEntity.qualifiedToId,
                    status = connectionEntity.status
                )
            }
        }
    }

    override suspend fun updateConnectionLastUpdatedTime(lastUpdate: String, id: String) = withContext(queriesContext) {
        connectionsQueries.updateConnectionLastUpdated(lastUpdate.toInstant(), id)
    }

    override suspend fun deleteConnectionDataAndConversation(conversationId: QualifiedIDEntity) = withContext(queriesContext) {
        connectionsQueries.transaction {
            connectionsQueries.deleteConnection(conversationId)
            conversationsQueries.deleteConversation(conversationId)
        }
    }

    override suspend fun getConnectionRequestsForNotification(): Flow<List<ConnectionEntity>> {
        return connectionsQueries.selectConnectionsForNotification(connectionMapper::toModel)
            .asFlow()
            .flowOn(queriesContext)
            .mapToList()
    }

    override suspend fun updateNotificationFlag(flag: Boolean, userId: QualifiedIDEntity) = withContext(queriesContext) {
        connectionsQueries.updateNotificationFlag(flag, userId)
    }

    override suspend fun setAllConnectionsAsNotified() = withContext(queriesContext) {
        connectionsQueries.setAllConnectionsAsNotified()
    }
}
