package com.wire.kalium.network.api.notification

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class EventResponse(
    @SerialName("id") val id: String,
    @SerialName("transient") val transient: Boolean,
    @SerialName("payload") val payload: List<Event>?
)

@Serializable
data class MessageEventData(
    @SerialName("text") val text: String,
    @SerialName("sender") val sender: String,
    @SerialName("recipient") val recipient: String
)
