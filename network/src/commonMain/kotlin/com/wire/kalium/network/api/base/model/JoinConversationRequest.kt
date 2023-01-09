package com.wire.kalium.network.api.base.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class JoinConversationRequest(
    @SerialName("code") val code: String,
    @SerialName("key") val key: String,
    @SerialName("uri") val uri: String?
)
