package com.wire.kalium.network.api.notification

import com.wire.kalium.network.utils.NetworkResponse
import kotlinx.coroutines.flow.Flow

sealed class WebSocketEvent {
    object Open: WebSocketEvent()

    data class BinaryPayloadReceived(val payload: EventResponse): WebSocketEvent()

    data class NonBinaryPayloadReceived(val payload: ByteArray): WebSocketEvent() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false

            other as NonBinaryPayloadReceived

            if (!payload.contentEquals(other.payload)) return false

            return true
        }

        override fun hashCode(): Int {
            return payload.contentHashCode()
        }
    }

    data class Close(val cause: Throwable?): WebSocketEvent()
}

interface NotificationApi {
    suspend fun lastNotification(queryClient: String): NetworkResponse<EventResponse>

    suspend fun notificationsByBatch(querySize: Int, queryClient: String, querySince: String): NetworkResponse<NotificationResponse>

    /**
     * request Notifications from the beginning of time
     */
    suspend fun getAllNotifications(querySize: Int, queryClient: String): NetworkResponse<NotificationResponse>

    suspend fun listenToLiveEvents(clientId: String): Flow<WebSocketEvent>

}
