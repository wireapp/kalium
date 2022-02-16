package com.wire.kalium.network.api.notification.conversation

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MessageEventData(
    @SerialName("text") val text: String,
    @SerialName("sender") val sender: String,
    @SerialName("recipient") val recipient: String
)
