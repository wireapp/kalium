package com.wire.kalium.network.api.base.authenticated.notification.conversation

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MessageEventData(
    @SerialName("text") val text: String,
    @SerialName("sender") val sender: String,
    @SerialName("recipient") val recipient: String,
    @SerialName("data") val encryptedExternalData: String? = null
)
