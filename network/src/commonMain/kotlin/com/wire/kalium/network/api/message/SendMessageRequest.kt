package com.wire.kalium.network.api.message

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SendMessageRequest(
        @SerialName("sender") val sender: String,
        @SerialName("data") val `data`: String,
        @SerialName("native_push") val nativePush: Boolean,
        @SerialName("recipients") val recipients: HashMap<String, HashMap<String, String>>,
        @SerialName("transient") val transient: Boolean,
        @SerialName("report_missing") val reportMissing: List<String> = listOf(),
        @SerialName("native_priority") val priority: String = "low"
)

