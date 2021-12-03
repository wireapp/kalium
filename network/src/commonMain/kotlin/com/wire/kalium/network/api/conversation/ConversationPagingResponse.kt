package com.wire.kalium.network.api.conversation

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ConversationPagingResponse(
    @SerialName("conversations") val conversations: List<ConversationResponse>,

    @SerialName("has_more") val hasMore: Boolean
)
