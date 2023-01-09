package com.wire.kalium.network.api.base.authenticated.conversation.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LimitedConversationInfo(
    @SerialName("id") val nonQualifiedConversationId: String,
    @SerialName("name") val name: String?
)
