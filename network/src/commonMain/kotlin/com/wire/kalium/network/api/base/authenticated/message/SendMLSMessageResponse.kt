package com.wire.kalium.network.api.base.authenticated.message

import com.wire.kalium.network.api.base.authenticated.notification.EventResponse
import kotlinx.serialization.Serializable

@Serializable
data class SendMLSMessageResponse(
    val time: String,
    val events: List<EventResponse>
)
