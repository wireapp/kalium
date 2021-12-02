package com.wire.kalium.api.message

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

@Serializable
data class MissingDevicesResponse(
        @SerialName("missing") val missing: HashMap<String, List<String>>,
        @SerialName("redundant") val redundant: HashMap<String, List<String>>,
        @SerialName("deleted") val deleted: HashMap<String, List<String>>
) : SendMessageResponse()

object MessageSent : SendMessageResponse()
