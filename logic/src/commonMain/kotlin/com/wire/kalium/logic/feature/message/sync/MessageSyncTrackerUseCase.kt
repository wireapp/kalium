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

import com.wire.backup.data.BackupMessage
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.feature.backup.mapper.toBackupMessage
import com.wire.kalium.persistence.dao.message.MessageSyncDAO
import com.wire.kalium.persistence.dao.message.SyncOperationType
import io.mockative.Mockable
import kotlinx.datetime.Clock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Mockable
interface MessageSyncTrackerUseCase {
    suspend fun trackMessageInsert(message: Message)
    suspend fun trackMessageDelete(conversationId: ConversationId, messageId: String)
}

internal class MessageSyncTrackerUseCaseImpl(
    private val messageSyncDAO: MessageSyncDAO,
    private val isFeatureEnabled: Boolean,
    kaliumLogger: KaliumLogger
) : MessageSyncTrackerUseCase {

    companion object {
        private val json: Json = Json { ignoreUnknownKeys = true }
    }

    private val logger = kaliumLogger.withTextTag("MessageSyncTracker")

    override suspend fun trackMessageInsert(message: Message) {
        if (!isFeatureEnabled) return

        try {
            val backupMessage = message.toBackupMessage() ?: run {
                logger.d("Message cannot be converted to BackupMessage (unsupported type), skipping sync tracking")
                return
            }

            val payload = json.encodeToString<BackupMessage>(backupMessage)

            messageSyncDAO.insertOrReplaceMessageToSync(
                conversationId = message.conversationId.toDao(),
                messageNonce = message.id,
                timestamp = Clock.System.now(),
                operation = SyncOperationType.UPSERT,
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
            messageSyncDAO.insertOrReplaceMessageToSync(
                conversationId = conversationId.toDao(),
                messageNonce = messageId,
                timestamp = Clock.System.now(),
                operation = SyncOperationType.DELETE,
                payload = null
            )

            logger.d("Tracked message delete: $messageId in conversation $conversationId")
        } catch (e: Exception) {
            logger.w("Failed to track message delete: ${e.message}")
        }
    }
}
