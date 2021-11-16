package com.wire.kalium.api.message

import com.wire.kalium.models.outbound.otr.Recipients
import kotlinx.serialization.SerialName

data class SendMessageRequest(
        @SerialName("sender") val sender: String,
        @SerialName("data") val `data`: String,
        @SerialName("native_push") val native_push: Boolean,
        @SerialName("recipients") val recipients: Recipients,
        @SerialName("transient") val transient: Boolean,
        @SerialName("report_missing") val reportMissing: List<String> = listOf(),
        @SerialName("native_priority") val priority: String = "low"
)
