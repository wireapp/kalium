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

package com.wire.kalium.network.api.authenticated.nomaddevice

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NomadBatchRestoreResponse(
    @SerialName("conversations")
    val conversations: List<NomadConversationBatchRestore>
)

@Serializable
data class NomadConversationBatchRestore(
    @SerialName("conversation")
    val conversation: Conversation,
    @SerialName("messages")
    val messages: List<NomadStoredMessage>,
    @SerialName("has_more")
    val hasMore: Boolean,
    @SerialName("next_cursor")
    val nextCursor: Long,
    @SerialName("next_timestamp")
    val nextTimestamp: Long
)
