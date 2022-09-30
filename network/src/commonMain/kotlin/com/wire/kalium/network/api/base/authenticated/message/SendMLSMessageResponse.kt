package com.wire.kalium.network.api.base.authenticated.message

import com.wire.kalium.network.api.base.authenticated.notification.EventResponse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SendMLSMessageResponse(
    @SerialName("time")
    val time: String,
    @SerialName("events")
    val events: List<EventResponse>
)
