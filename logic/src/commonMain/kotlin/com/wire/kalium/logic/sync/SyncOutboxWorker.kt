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

package com.wire.kalium.logic.sync

import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logger.KaliumLogger.Companion.ApplicationFlow.SYNC
import com.wire.kalium.logic.data.sync.SyncOutboxRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.sync.observer.NetworkStateObserver
import com.wire.kalium.network.NetworkState
import kotlinx.coroutines.flow.first

/**
 * Worker that processes pending sync outbox operations.
 * Uploads database changes to the remote server in batches.
 * Only runs when network is available and sync is enabled.
 */
internal class SyncOutboxWorker(
    private val syncOutboxRepository: SyncOutboxRepository,
    private val networkStateObserver: NetworkStateObserver,
    private val userId: UserId
) : DefaultWorker {

    /**
     * Process all pending outbox operations in batches.
     * Continues processing until no more pending operations remain.
     *
     * @return [Result.Success] - Always succeeds, errors are logged
     */
    override suspend fun doWork(): Result {
        kaliumLogger.withFeatureId(SYNC).i("SyncOutboxWorker started for user ${userId.value}")

        // Check if sync is enabled
        val isSyncEnabled = syncOutboxRepository.isSyncEnabled()
        if (!isSyncEnabled) {
            kaliumLogger.withFeatureId(SYNC).d("Sync replication is disabled, skipping")
            return Result.Success
        }

        // Check network connectivity
        val networkState = networkStateObserver.observeNetworkState().first()
        if (networkState !is NetworkState.ConnectedWithInternet) {
            kaliumLogger.withFeatureId(SYNC).d("No internet connection, deferring sync")
            return Result.Success
        }

        // Process batches until no more pending
        var hasMore = true
        var totalAccepted = 0
        var totalFailed = 0

        while (hasMore) {
            syncOutboxRepository.processBatch()
                .onSuccess { result ->
                    totalAccepted += result.acceptedCount
                    totalFailed += result.failedCount
                    hasMore = result.hasMorePending

                    kaliumLogger.withFeatureId(SYNC).d(
                        "Batch processed: ${result.acceptedCount} accepted, " +
                                "${result.failedCount} failed, hasMore=${result.hasMorePending}"
                    )
                }
                .onFailure { error ->
                    kaliumLogger.withFeatureId(SYNC).w("Batch processing failed: $error")
                    hasMore = false
                }
        }

        kaliumLogger.withFeatureId(SYNC).i(
            "SyncOutboxWorker completed: $totalAccepted accepted, $totalFailed failed"
        )

        return Result.Success
    }

    companion object {
        const val NAME_PREFIX = "sync-outbox-"
    }
}
