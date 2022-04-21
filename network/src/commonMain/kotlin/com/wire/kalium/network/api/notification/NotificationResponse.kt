package com.wire.kalium.network.api.notification

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

sealed class NotificationResponse {
    abstract val time: String
    abstract val hasMore: Boolean
    abstract val notifications: List<EventResponse>

    data class MissingSome(override val time: String, override val hasMore: Boolean, override val notifications: List<EventResponse>) :
        NotificationResponse() {
        internal constructor(notificationPage: NotificationPageResponse) : this(
            notificationPage.time,
            notificationPage.hasMore,
            notificationPage.notifications
        )
    }

    data class CompleteList(override val time: String, override val hasMore: Boolean, override val notifications: List<EventResponse>) :
        NotificationResponse() {
        internal constructor(notificationPage: NotificationPageResponse) : this(
            notificationPage.time,
            notificationPage.hasMore,
            notificationPage.notifications
        )
    }
}

@Serializable
internal data class NotificationPageResponse(
    @SerialName("time") val time: String,
    @SerialName("has_more") val hasMore: Boolean,
    @SerialName("notifications") val notifications: List<EventResponse>
)
