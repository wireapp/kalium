package com.wire.kalium.network.api.conversation

import com.wire.kalium.network.api.ConversationId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ConversationsDetailsRequest(
    @SerialName("qualified_ids")
    val conversationsIds: List<ConversationId>,
)
