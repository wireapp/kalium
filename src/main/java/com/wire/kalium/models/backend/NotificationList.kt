package com.wire.kalium.models.backend

data class NotificationList(
        val has_more: Boolean,
        val notifications: MutableList<Event>
)
