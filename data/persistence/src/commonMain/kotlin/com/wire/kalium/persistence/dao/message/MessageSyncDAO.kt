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
    suspend fun insertOrReplaceMessageToSync(
        conversationId: QualifiedIDEntity,
        messageNonce: String,
        timestamp: Instant,
        operation: SyncOperationType,
        payload: String?
    )

    suspend fun getMessagesToSync(limit: Int): List<MessageToSynchronizeEntity>

    suspend fun deleteSyncedMessages(
        conversationIds: List<QualifiedIDEntity>,
        messageNonces: List<String>
    )

    suspend fun countPendingMessages(): Long

    suspend fun getAllMessages(): List<MessageToSynchronizeEntity>

    /**
     * Observes the count of pending messages to sync.
     * Emits whenever messages are inserted or deleted from the sync table.
     */
    fun observePendingMessagesCount(): Flow<Long>
}
