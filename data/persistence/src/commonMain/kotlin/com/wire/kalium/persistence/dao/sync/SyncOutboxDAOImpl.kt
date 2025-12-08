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

import app.cash.sqldelight.coroutines.asFlow
import com.wire.kalium.persistence.SyncOutboxQueries
import com.wire.kalium.persistence.db.ReadDispatcher
import com.wire.kalium.persistence.db.WriteDispatcher
import com.wire.kalium.persistence.util.mapToList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant

internal class SyncOutboxDAOImpl internal constructor(
    private val syncOutboxQueries: SyncOutboxQueries,
    private val readDispatcher: ReadDispatcher,
    private val writeDispatcher: WriteDispatcher
) : SyncOutboxDAO {

    override suspend fun selectPendingOperations(limit: Int): List<SyncOutboxEntity> = withContext(readDispatcher.value) {
        syncOutboxQueries.selectPendingOperations(limit.toLong())
            .executeAsList()
            .map { it.toEntity() }
    }

    override suspend fun markAsInProgress(ids: List<Long>, timestamp: Instant) {
        withContext(writeDispatcher.value) {
            syncOutboxQueries.markAsInProgress(timestamp, ids)
        }
    }

    override suspend fun markAsSent(ids: List<Long>) {
        withContext(writeDispatcher.value) {
            syncOutboxQueries.markAsSent(ids)
        }
    }

    override suspend fun markAsFailed(ids: List<Long>, timestamp: Instant, errorMessage: String) {
        withContext(writeDispatcher.value) {
            syncOutboxQueries.markAsFailed(timestamp, errorMessage, ids)
        }
    }

    override suspend fun resetFailedToPending(maxAttempts: Int): Int = withContext(writeDispatcher.value) {
        syncOutboxQueries.resetFailedToPending(maxAttempts.toLong())
            .executeAsOne()
            .toInt()
    }

    override fun observeStats(): Flow<Map<String, Int>> {
        return syncOutboxQueries.selectStats()
            .asFlow()
            .mapToList()
            .map { stats ->
                stats.associate { it.sync_status to it.COUNT.toInt() }
            }
    }

    override suspend fun selectPendingCount(): Long = withContext(readDispatcher.value) {
        syncOutboxQueries.selectPendingCount().executeAsOne()
    }
}

private fun com.wire.kalium.persistence.SyncOutbox.toEntity() = SyncOutboxEntity(
    id = id,
    tableName = table_name,
    operationType = operation_type,
    rowKey = row_key,
    rowData = row_data,
    createdAt = created_at,
    syncStatus = sync_status,
    attemptCount = attempt_count.toInt(),
    lastAttemptAt = last_attempt_at,
    errorMessage = error_message
)
