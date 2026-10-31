/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.wire.kalium.logic.sync.receiving

import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.persistence.dao.message.MessageEntity
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
    private val self: UserId,
    private val deleteFile: (String) -> Unit,
) {
    suspend fun previousWarning(received: ReceivedPayload.Connection): MessageEntity? {
        if (received.connection.status != ConnectionEntity.State.ACCEPTED) return null
        val conversations = data.messages.getConversationIdsWithMessage(received.warningId)
        check(conversations.size <= 1) { "Accepted warning identity conflicts with stored state" }
        return conversations.singleOrNull()?.let { data.messages.getMessageById(received.warningId, it) }
    }

    @Suppress("CyclomaticComplexMethod")
    suspend fun persist(
        received: ReceivedPayload.Connection,
        requiredEarlier: Set<QualifiedIDEntity>,
        recreatedLater: Set<QualifiedIDEntity>,
        previousWarning: MessageEntity?,
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
            ConnectionEntity.State.ACCEPTED -> {
                data.persistAcceptedConnection(connection, self, received.warningId, previousWarning)
            }
            else -> {
                if (connection.status == ConnectionEntity.State.PENDING) {
                    data.conversations.insertConversation(connection.pendingConversation())
                }
                data.connections.insertConnection(connection)
            }
        }
    }
}
