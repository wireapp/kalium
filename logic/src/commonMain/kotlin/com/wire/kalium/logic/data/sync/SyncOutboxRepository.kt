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

package com.wire.kalium.logic.data.sync

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import io.mockative.Mockable
import kotlinx.coroutines.flow.Flow

/**
 * Repository for managing the sync outbox system.
 * Handles uploading pending database operations to the remote server.
 */
@Mockable
interface SyncOutboxRepository {

    /**
     * Checks if sync replication is currently enabled.
     * @return true if sync is enabled, false otherwise
     */
    suspend fun isSyncEnabled(): Boolean

    /**
     * Enables or disables sync replication.
     * @param enabled true to enable, false to disable
     * @return Either a [CoreFailure] or Unit on success
     */
    suspend fun setSyncEnabled(enabled: Boolean): Either<CoreFailure, Unit>

    /**
     * Processes a batch of pending operations from the outbox.
     * Uploads operations to the server and updates their status.
     * @return Either a [CoreFailure] or [BatchProcessResult] on success
     */
    suspend fun processBatch(): Either<CoreFailure, BatchProcessResult>

    /**
     * Observes outbox statistics (counts by status).
     * @return Flow of [SyncOutboxStats]
     */
    fun observeOutboxStats(): Flow<SyncOutboxStats>

    /**
     * Retries failed operations by resetting them to pending status.
     * Only operations below the max attempt threshold are reset.
     * @return Either a [CoreFailure] or the number of operations reset
     */
    suspend fun retryFailedOperations(): Either<CoreFailure, Int>

    /**
     * Gets the count of pending operations waiting to be synced.
     * @return Either a [CoreFailure] or the count of pending operations
     */
    suspend fun getPendingOperationCount(): Either<CoreFailure, Long>
}

/**
 * Result of processing a batch of operations.
 */
data class BatchProcessResult(
    val acceptedCount: Int,
    val failedCount: Int,
    val hasMorePending: Boolean
)

/**
 * Statistics about the sync outbox.
 */
data class SyncOutboxStats(
    val pendingCount: Int = 0,
    val inProgressCount: Int = 0,
    val failedCount: Int = 0
)
