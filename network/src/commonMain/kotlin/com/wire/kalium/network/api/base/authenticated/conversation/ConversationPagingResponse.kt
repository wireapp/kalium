package com.wire.kalium.network.api.base.authenticated.conversation

import com.wire.kalium.network.api.base.model.ConversationId
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
