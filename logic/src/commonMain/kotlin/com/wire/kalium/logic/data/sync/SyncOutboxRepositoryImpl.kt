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
import com.wire.kalium.common.error.wrapApiRequest
import com.wire.kalium.common.error.wrapStorageRequest
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.map
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.network.api.base.authenticated.sync.SyncApi
import com.wire.kalium.network.api.model.sync.OperationDTO
import com.wire.kalium.network.api.model.sync.SyncOperationRequest
import com.wire.kalium.persistence.dao.sync.SyncOutboxDAO
import com.wire.kalium.persistence.dao.sync.SyncStateDAO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json

internal class SyncOutboxRepositoryImpl(
    private val syncOutboxDAO: SyncOutboxDAO,
    private val syncStateDAO: SyncStateDAO,
    private val syncApi: SyncApi,
    private val userId: UserId,
    private val clientIdProvider: CurrentClientIdProvider
) : SyncOutboxRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun isSyncEnabled(): Boolean = wrapStorageRequest {
        syncStateDAO.selectState(KEY_SYNC_ENABLED)?.toBoolean() ?: false
    }.fold(
        { false },
        { it }
    )

    override suspend fun setSyncEnabled(enabled: Boolean): Either<CoreFailure, Unit> = wrapStorageRequest {
        syncStateDAO.upsertState(KEY_SYNC_ENABLED, enabled.toString(), Clock.System.now())
    }

    override suspend fun processBatch(): Either<CoreFailure, BatchProcessResult> {
        // Check if sync is enabled
        if (!isSyncEnabled()) {
            return Either.Right(BatchProcessResult(0, 0, false))
        }

        // Get batch size from config
        val batchSize = wrapStorageRequest {
            syncStateDAO.selectState(KEY_BATCH_SIZE)?.toIntOrNull() ?: DEFAULT_BATCH_SIZE
        }.fold({ DEFAULT_BATCH_SIZE }, { it })

        // Fetch pending operations
        return wrapStorageRequest {
            syncOutboxDAO.selectPendingOperations(batchSize)
        }.flatMap { operations ->
            if (operations.isEmpty()) {
                return@flatMap Either.Right(BatchProcessResult(0, 0, false))
            }

            val timestamp = Clock.System.now()
            val operationIds = operations.map { it.id }

            // Mark as in-progress
            wrapStorageRequest {
                syncOutboxDAO.markAsInProgress(operationIds, timestamp)
            }.flatMap {
                // Convert to network model
                val clientId = clientIdProvider.invoke()?.value ?: "unknown"
                val batchId = "${userId.value}-${timestamp.toEpochMilliseconds()}"

                val request = SyncOperationRequest(
                    userId = userId.value,
                    batchId = batchId,
                    deviceId = clientId,
                    operations = operations.map { entity ->
                        OperationDTO(
                            sequenceId = entity.id,
                            table = entity.tableName,
                            operation = entity.operationType,
                            rowKey = json.parseToJsonElement(entity.rowKey),
                            rowData = entity.rowData?.let { json.parseToJsonElement(it) },
                            timestamp = entity.createdAt.toString()
                        )
                    }
                )

                // Upload to server
                wrapApiRequest {
                    syncApi.uploadOperations(request)
                }.flatMap { response ->
                    // Mark accepted as sent
                    if (response.acceptedSequences.isNotEmpty()) {
                        wrapStorageRequest {
                            syncOutboxDAO.markAsSent(response.acceptedSequences)
                        }
                    }

                    // Mark rejected as failed
                    if (response.rejectedSequences.isNotEmpty()) {
                        val rejectedIds = response.rejectedSequences.map { it.sequenceId }
                        val errorMessage = response.rejectedSequences.firstOrNull()?.reason ?: "Unknown error"
                        wrapStorageRequest {
                            syncOutboxDAO.markAsFailed(rejectedIds, timestamp, errorMessage)
                        }
                    }

                    // Check if more pending
                    wrapStorageRequest {
                        syncOutboxDAO.selectPendingCount()
                    }.map { pendingCount ->
                        BatchProcessResult(
                            acceptedCount = response.acceptedSequences.size,
                            failedCount = response.rejectedSequences.size,
                            hasMorePending = pendingCount > 0
                        )
                    }
                }
            }
        }
    }

    override fun observeOutboxStats(): Flow<SyncOutboxStats> {
        return syncOutboxDAO.observeStats().map { statsMap ->
            SyncOutboxStats(
                pendingCount = statsMap["PENDING"] ?: 0,
                inProgressCount = statsMap["IN_PROGRESS"] ?: 0,
                failedCount = statsMap["FAILED"] ?: 0
            )
        }
    }

    override suspend fun retryFailedOperations(): Either<CoreFailure, Int> = wrapStorageRequest {
        syncOutboxDAO.resetFailedToPending(MAX_ATTEMPTS)
    }

    override suspend fun getPendingOperationCount(): Either<CoreFailure, Long> = wrapStorageRequest {
        syncOutboxDAO.selectPendingCount()
    }

    private companion object {
        const val KEY_SYNC_ENABLED = "sync_enabled"
        const val KEY_BATCH_SIZE = "batch_size"
        const val DEFAULT_BATCH_SIZE = 100
        const val MAX_ATTEMPTS = 3
    }
}
