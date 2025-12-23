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

package com.wire.kalium.network.api.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Request payload for synchronizing messages to the backup service
 */
@Serializable
data class MessageSyncRequestDTO(
    @SerialName("user_id")
    val userId: String,
    @SerialName("upserts")
    val upserts: Map<String, List<MessageSyncUpsertDTO>>, // Map from conversation ID to list of upserts
    @SerialName("deletions")
    val deletions: Map<String, List<String>>, // Map from conversation ID to list of message IDs to delete
    @SerialName("conversations_last_read")
    val conversationsLastRead: Map<String, Long> = emptyMap() // Map from conversation ID to last read timestamp (epoch millis)
)

/**
 * Individual message upsert operation
 */
@Serializable
data class MessageSyncUpsertDTO(
    @SerialName("message_id")
    val messageId: String,
    @SerialName("timestamp")
    val timestamp: Long, // Unix timestamp in milliseconds
    @SerialName("payload")
    val payload: String // JSON string of BackupMessage
)

/**
 * Response payload for fetching messages from the backup service
 */
@Serializable
data class MessageSyncFetchResponseDTO(
    @SerialName("has_more")
    val hasMore: Boolean,
    @SerialName("conversations")
    val conversations: Map<String, ConversationMessagesDTO>,
    @SerialName("pagination_token")
    val paginationToken: String? = null
)

/**
 * Messages and metadata for a single conversation
 */
@Serializable
data class ConversationMessagesDTO(
    @SerialName("last_read")
    val lastRead: Long? = null, // Last read timestamp (epoch millis)
    @SerialName("messages")
    val messages: List<MessageSyncResultDTO>
)

/**
 * Individual message result from fetch operation
 */
@Serializable
data class MessageSyncResultDTO(
    @SerialName("timestamp")
    val timestamp: String,
    @SerialName("message_id")
    val messageId: String,
    @SerialName("payload")
    val payload: String // JSON-encoded string of BackupMessage
)

/**
 * Response payload for deleting messages from the backup service
 */
@Serializable
data class DeleteMessagesResponseDTO(
    @SerialName("deleted_count")
    val deletedCount: Int
)

/**
 * Response payload for uploading cryptographic state backup
 */
@Serializable
data class StateBackupUploadResponse(
    @SerialName("uploaded_at")
    val uploadedAt: String? = null
)

/**
 * Response payload for fetching conversation last read data
 */
@Serializable
data class ConversationsLastReadResponseDTO(
    @SerialName("conversations_last_read")
    val conversationsLastRead: Map<String, Long> // Map from conversation ID to last read timestamp (epoch millis)
)
