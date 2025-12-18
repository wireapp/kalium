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

import com.wire.kalium.logger.KaliumLogger.Companion.ApplicationFlow.SYNC
import com.wire.kalium.logic.feature.message.sync.SyncMessagesResult
import com.wire.kalium.logic.feature.message.sync.SyncMessagesUseCase
import com.wire.kalium.common.logger.kaliumLogger

/**
 * Worker that retries failed message synchronization attempts.
 * @see [MessageSyncRetryWorker.doWork]
 */
class MessageSyncRetryWorker(
    private val syncMessages: SyncMessagesUseCase
) : DefaultWorker {

    /**
     * Attempts to synchronize pending messages to the backup service.
     *
     * @return [Result.Success] if sync succeeds or nothing to sync
     * @return [Result.Failure] for 4xx client errors (permanent failures, don't retry)
     * @return [Result.Retry] for 5xx server errors or exceptions (temporary failures, retry)
     */
    override suspend fun doWork(): Result {
        kaliumLogger.withFeatureId(SYNC).i("Attempting message sync retry")

        return when (val result = syncMessages()) {
            is SyncMessagesResult.Success -> {
                kaliumLogger.withFeatureId(SYNC).i("Message sync succeeded")
                Result.Success
            }
            is SyncMessagesResult.NothingToSync -> {
                kaliumLogger.withFeatureId(SYNC).i("Nothing to sync")
                Result.Success
            }
            is SyncMessagesResult.Disabled -> {
                kaliumLogger.withFeatureId(SYNC).i("Message sync feature disabled")
                Result.Success
            }
            is SyncMessagesResult.ApiFailure -> {
                // 4xx client errors = don't retry (permanent failure)
                // 5xx server errors = retry
                if (result.statusCode in 400..499) {
                    kaliumLogger.withFeatureId(SYNC).w("Client error (${result.statusCode}), not retrying")
                    Result.Failure
                } else {
                    kaliumLogger.withFeatureId(SYNC).w("Server error (${result.statusCode}), will retry")
                    Result.Retry
                }
            }
            is SyncMessagesResult.Failure -> {
                kaliumLogger.withFeatureId(SYNC).e("Exception during sync: ${result.exception.message}", result.exception)
                Result.Retry
            }
        }
    }

    companion object {
        const val NAME_PREFIX = "message-sync-retry-"
    }
}
