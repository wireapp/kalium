package com.wire.kalium.models.backend

import kotlinx.serialization.Serializable

@Serializable
data class NotificationList(
    val has_more: Boolean,
    val notifications: MutableList<Event>
)
