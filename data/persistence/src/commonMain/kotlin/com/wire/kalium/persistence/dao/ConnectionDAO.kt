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

import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

data class ConnectionEntity(
    val conversationId: String,
    val from: String,
    val lastUpdateDate: Instant,
    val qualifiedConversationId: ConversationIDEntity,
    val qualifiedToId: QualifiedIDEntity,
    val status: State,
    val toId: String,
    val shouldNotify: Boolean? = null,
    val otherUser: UserEntity? = null
) {

    enum class State {
        /** Default - No connection state */
        NOT_CONNECTED,

        /** The other user has sent a connection request to this one */
        PENDING,

        /** This user has sent a connection request to another user */
        SENT,

        /** The user has been blocked */
        BLOCKED,

        /** The connection has been ignored */
        IGNORED,

        /** The connection has been cancelled */
        CANCELLED,

        /** The connection is missing legal hold consent */
        MISSING_LEGALHOLD_CONSENT,

        /** The connection is complete and the conversation is in its normal state */
        ACCEPTED
    }
}

interface ConnectionDAO {
    suspend fun getConnections(): Flow<List<ConnectionEntity>>
    suspend fun getConnectionRequests(): Flow<List<ConnectionEntity>>
    suspend fun insertConnection(connectionEntity: ConnectionEntity)
    suspend fun insertConnections(users: List<ConnectionEntity>)
    suspend fun updateConnectionLastUpdatedTime(lastUpdate: String, id: String)
    suspend fun deleteConnectionDataAndConversation(conversationId: QualifiedIDEntity)
    suspend fun getConnectionRequestsForNotification(): Flow<List<ConnectionEntity>>
    suspend fun updateNotificationFlag(flag: Boolean, userId: QualifiedIDEntity)
    suspend fun setAllConnectionsAsNotified()
    suspend fun getConnection(id: ConversationIDEntity): ConnectionEntity?
    suspend fun getConnectionByUser(userId: QualifiedIDEntity): ConnectionEntity?
}
