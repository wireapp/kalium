/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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

package com.wire.kalium.logic.sync.remoteBackup

import com.wire.backup.data.BackupMessage
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.sync.MessageSyncRepository
import com.wire.kalium.logic.feature.backup.mapper.toBackupMessage
import io.mockative.Mockable
import kotlinx.serialization.json.Json

/**
 * Domain service for tracking message operations for remote synchronization.
 * This is not a traditional use case, but rather a cross-cutting service
 * that can be depended upon by use cases and other components.
 */
@Mockable
interface MessageSyncTracker {
    suspend fun trackMessageInsert(message: Message)
    suspend fun trackMessageDelete(conversationId: ConversationId, messageId: String)
    suspend fun trackMessageUpdate(conversationId: ConversationId, messageId: String)
}

internal class MessageSyncTrackerImpl(
    private val messageSyncRepository: MessageSyncRepository,
    private val messageRepository: MessageRepository,
    private val isFeatureEnabled: Boolean,
    kaliumLogger: KaliumLogger
) : MessageSyncTracker {

    companion object {
        private val json: Json = Json { ignoreUnknownKeys = true }
    }

    private val logger = kaliumLogger.withTextTag("MessageSyncTracker")

    override suspend fun trackMessageInsert(message: Message) {
        if (!isFeatureEnabled) return

        try {
            val backupMessage = message.toBackupMessage() ?: run {
                logger.d("Message cannot be converted to BackupMessage (unsupported type), skipping sync")
                return
            }

            val payload = json.encodeToString<BackupMessage>(backupMessage)

            messageSyncRepository.upsertMessageToSync(
                conversationId = message.conversationId,
                messageNonce = message.id,
                timestamp = message.date,
                payload = payload
            )

            logger.d("Tracked message insert: ${message.id} in conversation ${message.conversationId}")
        } catch (e: Exception) {
            logger.w("Failed to track message insert: ${e.message}")
        }
    }

    override suspend fun trackMessageDelete(conversationId: ConversationId, messageId: String) {
        if (!isFeatureEnabled) return

        try {
            messageSyncRepository.markMessageForDeletion(
                conversationId = conversationId,
                messageNonce = messageId
            )

            logger.d("Tracked message delete: $messageId in conversation $conversationId")
        } catch (e: Exception) {
            logger.w("Failed to track message delete: ${e.message}")
        }
    }

    override suspend fun trackMessageUpdate(conversationId: ConversationId, messageId: String) {
        if (!isFeatureEnabled) return

        try {
            // Fetch the updated message from the repository
            when (val messageResult = messageRepository.getMessageById(conversationId, messageId)) {
                is Either.Left -> {
                    logger.w("Failed to fetch updated message for tracking: ${messageResult.value}")
                }
                is Either.Right -> {
                    val message = messageResult.value
                    // Convert to BackupMessage
                    val backupMessage = message.toBackupMessage()
                    if (backupMessage == null) {
                        logger.d("Updated message cannot be converted to BackupMessage (unsupported type), skipping sync tracking")
                        return
                    }

                    val payload = json.encodeToString<BackupMessage>(backupMessage)

                    // UPSERT the sync record
                    messageSyncRepository.upsertMessageToSync(
                        conversationId = conversationId,
                        messageNonce = messageId,
                        timestamp = message.date,
                        payload = payload
                    )

                    logger.d("Tracked message update: $messageId in conversation $conversationId")
                }
            }
        } catch (e: Exception) {
            logger.w("Failed to track message update: ${e.message}")
        }
    }
}
