package com.wire.kalium.network.api.notification

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class NotificationResponse(
    @SerialName("time") val time: String,
    @SerialName("has_more") val hasMore: Boolean,
    @SerialName("notifications") val notifications: List<EventResponse>,
    @Transient val isMissingNotifications: Boolean = false
)
