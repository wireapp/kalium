package com.wire.kalium.models.backend

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NotificationList(
    @SerialName("has_more") val hasMore: Boolean,
    @SerialName("notifications") val notifications: MutableList<Event>
)
