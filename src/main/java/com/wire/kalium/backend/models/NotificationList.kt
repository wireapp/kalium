package com.wire.kalium.backend.models

data class NotificationList(
        val has_more: Boolean,
        val notifications: MutableList<Event>
)
