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
    @SerialName("updates")
    val updates: List<MessageSyncUpdateDTO>
)

/**
 * Individual message update to be synchronized
 */
@Serializable
data class MessageSyncUpdateDTO(
    @SerialName("conversation_id")
    val conversationId: String,
    @SerialName("message_nonce")
    val messageNonce: String,
    @SerialName("timestamp")
    val timestamp: Long, // Unix timestamp in milliseconds
    @SerialName("operation")
    val operation: Int, // 1 = upsert, 2 = delete
    @SerialName("payload")
    val payload: String? // JSON string of BackupMessage, null for deletes
)
