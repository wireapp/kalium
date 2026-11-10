/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.wire.kalium.logic.sync.receiving

import com.wire.kalium.logic.data.conversation.pendingConversation
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.network.api.authenticated.connection.ConnectionDTO
import com.wire.kalium.persistence.dao.ConnectionEntity
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.db.MessageEffectDAOs

internal fun ConnectionDTO.toEntity() = ConnectionEntity(
    conversationId = conversationId,
    from = from,
    lastUpdateDate = lastUpdate,
    qualifiedConversationId = qualifiedConversationId.toDao(),
    qualifiedToId = qualifiedToId.toDao(),
    status = ConnectionEntity.State.valueOf(status.name),
    toId = toId,
)

internal class ReceivedConnectionHandler(
    private val data: MessageEffectDAOs,
    private val deleteFile: (String) -> Unit,
) {
    @Suppress("CyclomaticComplexMethod")
    suspend fun persist(
        received: ReceivedPayload.Connection,
        requiredEarlier: Set<QualifiedIDEntity>,
        recreatedLater: Set<QualifiedIDEntity>,
    ) {
        val connection = received.connection
        val id = connection.qualifiedConversationId
        data.users.insertOrIgnoreIncompleteUsers(listOf(connection.qualifiedToId))
        received.profiles.persist(data.users, data.apps)
        if (connection.status != ConnectionEntity.State.ACCEPTED) {
            data.users.upsertConnectionStatuses(mapOf(connection.qualifiedToId to connection.status))
        }
        when (connection.status) {
            ConnectionEntity.State.CANCELLED -> {
                check(id !in requiredEarlier && id !in recreatedLater) {
                    "Connection cancellation requires replayable conversation metadata"
                }
                data.assets.deleteConversationFiles(id, deleteFile)
                data.connections.deleteConnectionDataAndConversation(id)
            }
            ConnectionEntity.State.ACCEPTED -> data.persistAcceptedConnection(connection)
            else -> {
                if (connection.status == ConnectionEntity.State.PENDING) {
                    data.conversations.insertConversation(connection.pendingConversation())
                }
                data.connections.insertConnection(connection)
            }
        }
    }
}
