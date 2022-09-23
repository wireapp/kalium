package com.wire.kalium.network.api.base.authenticated.conversation

import com.wire.kalium.network.api.base.model.ConversationId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ConversationsDetailsRequest(
    @SerialName("qualified_ids")
    val conversationsIds: List<ConversationId>,
)
