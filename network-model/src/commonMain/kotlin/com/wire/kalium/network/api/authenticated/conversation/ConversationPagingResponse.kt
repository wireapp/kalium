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

package com.wire.kalium.network.api.authenticated.conversation

import com.wire.kalium.network.api.model.ConversationId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ConversationPagingResponse(
    @SerialName("qualified_conversations") val conversationsIds: List<ConversationId>,
    @SerialName("has_more") val hasMore: Boolean,
    @SerialName("paging_state") val pagingState: String
)

@Serializable
data class ConversationResponseDTO(
    @SerialName("found") val conversationsFound: List<ConversationResponse>,
    @SerialName("not_found") val conversationsNotFound: List<ConversationId>,
    @SerialName("failed") val conversationsFailed: List<ConversationId>,
)

@Serializable
data class ConversationResponseDTOV3(
    @SerialName("found") val conversationsFound: List<ConversationResponseV3>,
    @SerialName("not_found") val conversationsNotFound: List<ConversationId>,
    @SerialName("failed") val conversationsFailed: List<ConversationId>,
)
