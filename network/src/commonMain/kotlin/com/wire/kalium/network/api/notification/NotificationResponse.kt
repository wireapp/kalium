package com.wire.kalium.network.api.notification

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


@Serializable
data class NotificationPageResponse(
    @SerialName("time") val time: String,
    @SerialName("has_more") val hasMore: Boolean,
    @SerialName("notifications") val notifications: List<NotificationResponse>
)

@Serializable
data class NotificationResponse(
    @SerialName("payload") val payload: List<Event>?,
    @SerialName("id") val id: String
)
