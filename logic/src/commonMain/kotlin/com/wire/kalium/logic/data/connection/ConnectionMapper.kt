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

package com.wire.kalium.logic.data.connection

import com.wire.kalium.logic.data.conversation.Conversation.ProtocolInfo
import com.wire.kalium.logic.data.conversation.ConversationDetails
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.data.user.Connection
import com.wire.kalium.logic.data.user.UserMapper
import com.wire.kalium.logic.data.user.type.DomainUserTypeMapper
import com.wire.kalium.logic.data.user.type.UserType
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.network.api.authenticated.connection.ConnectionDTO
import com.wire.kalium.persistence.dao.ConnectionEntity

interface ConnectionMapper {
    fun fromApiToDao(state: ConnectionDTO): ConnectionEntity
    fun fromDaoToModel(connection: ConnectionEntity): Connection
    fun fromDaoToConversationDetails(connection: ConnectionEntity): ConversationDetails.Connection
    fun fromApiToModel(state: ConnectionDTO): Connection
    fun modelToDao(state: Connection): ConnectionEntity
}

internal class ConnectionMapperImpl(
    private val idMapper: IdMapper = MapperProvider.idMapper(),
    private val statusMapper: ConnectionStatusMapper = MapperProvider.connectionStatusMapper(),
    private val userMapper: UserMapper = MapperProvider.userMapper(),
    private val userTypeMapper: DomainUserTypeMapper = MapperProvider.userTypeMapper(),
) : ConnectionMapper {
    override fun fromApiToDao(state: ConnectionDTO): ConnectionEntity = ConnectionEntity(
        conversationId = state.conversationId,
        from = state.from,
        lastUpdateDate = state.lastUpdate,
        qualifiedConversationId = idMapper.fromApiToDao(state.qualifiedConversationId),
        qualifiedToId = idMapper.fromApiToDao(state.qualifiedToId),
        status = statusMapper.fromApiToDao(state.status),
        toId = state.toId,
    )

    override fun fromDaoToModel(connection: ConnectionEntity) = with(connection) {
        Connection(
            conversationId = conversationId,
            from = from,
            lastUpdate = lastUpdateDate,
            qualifiedConversationId = qualifiedConversationId.toModel(),
            qualifiedToId = qualifiedToId.toModel(),
            status = statusMapper.fromDaoModel(status),
            toId = toId,
            fromUser = otherUser?.let { userMapper.fromUserEntityToOtherUser(it) }
        )
    }

    override fun fromDaoToConversationDetails(
        connection: ConnectionEntity
    ): ConversationDetails.Connection = with(connection) {
        ConversationDetails.Connection(
            conversationId = qualifiedConversationId.toModel(),
            otherUser = otherUser?.let { userMapper.fromUserEntityToOtherUser(it) },
            userType = otherUser?.let { userTypeMapper.fromUserTypeEntity(it.userType) } ?: UserType.GUEST,
            lastModifiedDate = lastUpdateDate,
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
        lastUpdateDate = state.lastUpdate,
        qualifiedConversationId = state.qualifiedConversationId.toDao(),
        qualifiedToId = state.qualifiedToId.toDao(),
        status = statusMapper.toDaoModel(state.status),
        toId = state.toId,
    )
}
