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

package com.wire.kalium.logic.feature.message.sync

import com.wire.kalium.common.functional.fold
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.data.sync.MessageSyncRepository
import com.wire.kalium.logic.data.sync.MessageSyncRequest
import com.wire.kalium.logic.data.sync.MessageSyncUpsert
import com.wire.kalium.logic.data.sync.SyncOperationType

/**
 * Result of synchronizing messages to the backup service
 */
sealed class SyncMessagesResult {
    data object Success : SyncMessagesResult()
    data object NothingToSync : SyncMessagesResult()
    data object Disabled : SyncMessagesResult()
    data class ApiFailure(val statusCode: Int, val message: String) : SyncMessagesResult()
    data class Failure(val exception: Throwable) : SyncMessagesResult()
}

/**
 * Use case for synchronizing pending messages to the backup service
 */
class SyncMessagesUseCase internal constructor(
    private val messageSyncRepository: MessageSyncRepository,
    private val userId: com.wire.kalium.logic.data.user.UserId,
    private val isFeatureEnabled: Boolean,
    kaliumLogger: KaliumLogger = com.wire.kalium.common.logger.kaliumLogger
) {

    private val logger = kaliumLogger.withTextTag("SyncMessagesUseCase")

    suspend operator fun invoke(): SyncMessagesResult {
        // Check if feature is enabled
        if (!isFeatureEnabled) {
            logger.i("Feature disabled, skipping sync")
            return SyncMessagesResult.Disabled
        }

        return try {
            // Fetch messages to sync (limit 100)
            val messagesToSyncResult = messageSyncRepository.getMessagesToSync(limit = 100)
            val messagesToSync = messagesToSyncResult.fold(
                { return SyncMessagesResult.Failure(Exception("Failed to get messages to sync")) },
                { it }
            )

            // Fetch conversations with pending last read updates
            val conversationsWithPendingSyncResult = messageSyncRepository.getConversationsWithPendingSync()
            val conversationsWithPendingSync = conversationsWithPendingSyncResult.fold(
                { return SyncMessagesResult.Failure(Exception("Failed to get conversations pending sync")) },
                { it }
            )

            if (messagesToSync.isEmpty() && conversationsWithPendingSync.isEmpty()) {
                logger.i("No messages or conversations to sync")
                return SyncMessagesResult.NothingToSync
            }

            logger.i("Syncing ${messagesToSync.size} messages and ${conversationsWithPendingSync.size} conversation last reads for user ${userId.value}")

            // Separate messages by operation type
            val upsertMessages = messagesToSync.filter { it.operation == SyncOperationType.UPSERT }
            val deleteMessages = messagesToSync.filter { it.operation == SyncOperationType.DELETE }

            // Build upserts map: conversation ID -> list of upsert operations
            val upserts = upsertMessages
                .groupBy { it.conversationId.toString() }
                .mapValues { (_, entities) ->
                    entities.map { entity ->
                        MessageSyncUpsert(
                            messageId = entity.messageNonce,
                            timestamp = entity.timestamp.toEpochMilliseconds(),
                            payload = entity.payload!! // Upserts always have payload
                        )
                    }
                }

            // Build deletions map: conversation ID -> list of message IDs
            val deletions = deleteMessages
                .groupBy { it.conversationId.toString() }
                .mapValues { (_, entities) ->
                    entities.map { it.messageNonce }
                }

            // Build conversationsLastRead map: conversation ID -> last read message ID
            val conversationsLastRead = conversationsWithPendingSync.associate { conversation ->
                conversation.conversationId.toString() to conversation.toUploadLastRead
            }

            val request = MessageSyncRequest(
                userId = userId.value,
                upserts = upserts,
                deletions = deletions,
                conversationsLastRead = conversationsLastRead
            )

            // POST to API
            messageSyncRepository.syncMessages(request).fold(
                { networkFailure ->
                    logger.w("API failure: $networkFailure")
                    SyncMessagesResult.ApiFailure(0, networkFailure.toString())
                },
                {
                    logger.i("Successfully synced ${messagesToSync.size} messages and ${conversationsWithPendingSync.size} conversation last reads")

                    // Delete synced messages
                    val messagesToDelete = messagesToSync
                        .groupBy { it.conversationId }
                        .mapValues { (_, entities) -> entities.map { it.messageNonce } }
                    messageSyncRepository.deleteSyncedMessages(messagesToDelete)

                    // Mark conversations as uploaded
                    conversationsWithPendingSync.forEach { conversation ->
                        messageSyncRepository.markConversationLastReadAsUploaded(conversation.conversationId)
                    }

                    SyncMessagesResult.Success
                }
            )
        } catch (e: Exception) {
            logger.e("Exception during sync: ${e.message}", e)
            SyncMessagesResult.Failure(e)
        }
    }
}
