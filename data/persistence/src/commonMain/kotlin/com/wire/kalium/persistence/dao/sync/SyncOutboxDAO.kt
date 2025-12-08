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

package com.wire.kalium.persistence.dao.sync

import io.mockative.Mockable
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

/**
 * Data Access Object for managing sync outbox operations.
 * The outbox pattern queues database changes for upload to remote server.
 */
@Mockable
interface SyncOutboxDAO {

    /**
     * Retrieves pending operations from the outbox, ordered by creation time.
     * @param limit Maximum number of operations to retrieve
     * @return List of pending operations ready for sync
     */
    suspend fun selectPendingOperations(limit: Int): List<SyncOutboxEntity>

    /**
     * Marks operations as in-progress before uploading to server.
     * @param ids List of operation IDs to mark
     * @param timestamp Time when operations were marked as in-progress
     */
    suspend fun markAsInProgress(ids: List<Long>, timestamp: Instant)

    /**
     * Deletes operations that were successfully uploaded to server.
     * @param ids List of operation IDs to remove from outbox
     */
    suspend fun markAsSent(ids: List<Long>)

    /**
     * Marks operations as failed after upload attempt.
     * @param ids List of operation IDs that failed
     * @param timestamp Time when operations failed
     * @param errorMessage Error description from server or network layer
     */
    suspend fun markAsFailed(ids: List<Long>, timestamp: Instant, errorMessage: String)

    /**
     * Resets failed operations back to pending status for retry.
     * Only operations below the max attempt threshold are reset.
     * @param maxAttempts Maximum retry attempts allowed
     * @return Number of operations reset to pending
     */
    suspend fun resetFailedToPending(maxAttempts: Int): Int

    /**
     * Observes outbox statistics (counts by status).
     * Emits a map of status -> count whenever the outbox changes.
     * @return Flow of status statistics
     */
    fun observeStats(): Flow<Map<String, Int>>

    /**
     * Retrieves count of pending operations.
     * @return Number of operations waiting to be synced
     */
    suspend fun selectPendingCount(): Long
}

/**
 * Entity representing a single database operation in the sync outbox.
 */
data class SyncOutboxEntity(
    val id: Long,
    val tableName: String,
    val operationType: String,
    val rowKey: String,
    val rowData: String?,
    val createdAt: Instant,
    val syncStatus: String,
    val attemptCount: Int,
    val lastAttemptAt: Instant?,
    val errorMessage: String?
)
