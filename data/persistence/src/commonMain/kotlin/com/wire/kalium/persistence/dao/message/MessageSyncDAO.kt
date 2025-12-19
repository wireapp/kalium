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

package com.wire.kalium.persistence.dao.message

import com.wire.kalium.persistence.dao.QualifiedIDEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

interface MessageSyncDAO {
    /**
     * Inserts or updates a message in the sync queue with payload (for UPSERT operation).
     */
    suspend fun upsertMessageToSync(
        conversationId: QualifiedIDEntity,
        messageNonce: String,
        timestamp: Instant,
        payload: String
    )

    /**
     * Marks a message for deletion in the sync queue (for DELETE operation).
     */
    suspend fun markMessageForDeletion(
        conversationId: QualifiedIDEntity,
        messageNonce: String
    )

    suspend fun getMessagesToSync(limit: Int): List<MessageToSynchronizeEntity>

    /**
     * Deletes synced messages from the sync queue.
     * @param messagesToDelete Map of conversation ID to list of message nonces in that conversation
     */
    suspend fun deleteSyncedMessages(
        messagesToDelete: Map<QualifiedIDEntity, List<String>>
    )

    suspend fun countPendingMessages(): Long

    suspend fun getAllMessages(): List<MessageToSynchronizeEntity>

    /**
     * Observes the count of pending messages to sync.
     * Emits whenever messages are inserted or deleted from the sync table.
     */
    fun observePendingMessagesCount(): Flow<Long>
}
