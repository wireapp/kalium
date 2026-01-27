/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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
package com.wire.kalium.network.api.authenticated.remoteBackup

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DTO representing the payload of a message sync operation.
 * This mirrors the structure of BackupMessage for type-safe serialization.
 */
@Serializable
data class MessageSyncPayloadDTO(
    @SerialName("id")
    val id: String,
    @SerialName("conversationId")
    val conversationId: MessageSyncQualifiedIdDTO,
    @SerialName("senderUserId")
    val senderUserId: MessageSyncQualifiedIdDTO,
    @SerialName("senderClientId")
    val senderClientId: String,
    @SerialName("creationDate")
    val creationDate: Long,
    @SerialName("content")
    val content: MessageSyncContentDTO,
    @SerialName("lastEditTime")
    val lastEditTime: Long? = null
)

/**
 * DTO for qualified IDs in message sync payloads.
 */
@Serializable
data class MessageSyncQualifiedIdDTO(
    @SerialName("id")
    val id: String,
    @SerialName("domain")
    val domain: String
)
