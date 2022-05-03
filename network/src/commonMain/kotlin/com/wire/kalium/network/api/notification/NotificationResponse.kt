package com.wire.kalium.network.api.notification

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NotificationResponse(
    @SerialName("time") val time: String,
    @SerialName("has_more") val hasMore: Boolean,
    @SerialName("notifications") val notifications: List<EventResponse>,
    val isMissingNotifications: Boolean = false
)
