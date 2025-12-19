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

import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.data.id.toApi
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.network.api.base.authenticated.backup.MessageSyncApi
import com.wire.kalium.network.api.model.MessageSyncRequestDTO
import com.wire.kalium.network.api.model.MessageSyncUpsertDTO
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.persistence.dao.message.SyncOperationType
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.persistence.dao.message.MessageSyncDAO
import com.wire.kalium.logic.data.id.QualifiedIdMapper

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
    private val messageSyncDAO: MessageSyncDAO,
    private val messageSyncApi: MessageSyncApi,
    private val userId: com.wire.kalium.logic.data.user.UserId,
    private val isFeatureEnabled: Boolean,
    private val qualifiedIdMapper: QualifiedIdMapper,
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
            val messagesToSync = messageSyncDAO.getMessagesToSync(limit = 100)

            if (messagesToSync.isEmpty()) {
                logger.i("No messages to sync")
                return SyncMessagesResult.NothingToSync
            }

            logger.i("Syncing ${messagesToSync.size} messages for user ${userId.value}")

            // Separate messages by operation type
            val upsertMessages = messagesToSync.filter { it.operation == SyncOperationType.UPSERT }
            val deleteMessages = messagesToSync.filter { it.operation == SyncOperationType.DELETE }

            // Build upserts map: conversation ID -> list of upsert operations
            val upserts = upsertMessages
                .groupBy { it.conversationId.toModel().toString() }
                .mapValues { (_, entities) ->
                    entities.map { entity ->
                        MessageSyncUpsertDTO(
                            messageId = entity.messageNonce,
                            timestamp = entity.timestamp.toEpochMilliseconds(),
                            payload = entity.payload!! // Upserts always have payload
                        )
                    }
                }

            // Build deletions map: conversation ID -> list of message IDs
            val deletions = deleteMessages
                .groupBy { it.conversationId.toModel().toString() }
                .mapValues { (_, entities) ->
                    entities.map { it.messageNonce }
                }

            val request = MessageSyncRequestDTO(
                userId = userId.value,
                upserts = upserts,
                deletions = deletions
            )

            // POST to API
            val response = messageSyncApi.syncMessages(request)

            // Handle response
            when (response) {
                is NetworkResponse.Success -> {
                    logger.i("Successfully synced ${messagesToSync.size} messages")

                    // Delete synced messages from database
                    // Group messages by conversation ID to ensure precise deletion
                    val messagesToDelete = messagesToSync.groupBy(
                        keySelector = { it.conversationId },
                        valueTransform = { it.messageNonce }
                    )

                    messageSyncDAO.deleteSyncedMessages(messagesToDelete)

                    SyncMessagesResult.Success
                }
                is NetworkResponse.Error -> {
                    val statusCode = when (val exception = response.kException) {
                        is KaliumException.InvalidRequestError -> exception.errorResponse.code
                        is KaliumException.ServerError -> exception.errorResponse.code
                        is KaliumException.RedirectError -> exception.errorResponse.code
                        else -> 0
                    }
                    val message = "HTTP $statusCode"
                    logger.w("API failure - $message")
                    SyncMessagesResult.ApiFailure(statusCode, message)
                }
            }
        } catch (e: Exception) {
            logger.e("Exception during sync: ${e.message}", e)
            SyncMessagesResult.Failure(e)
        }
    }
}
