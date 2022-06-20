package com.wire.kalium.network.api.notification

import com.wire.kalium.network.utils.NetworkResponse
import kotlinx.coroutines.flow.Flow

sealed class WebSocketEvent<BinaryPayloadType> {
    class Open<BinaryPayloadType>: WebSocketEvent<BinaryPayloadType>()

    data class BinaryPayloadReceived<BinaryPayloadType>(val payload: BinaryPayloadType): WebSocketEvent<BinaryPayloadType>()

    data class NonBinaryPayloadReceived<BinaryPayloadType>(val payload: ByteArray): WebSocketEvent<BinaryPayloadType>() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false

            other as NonBinaryPayloadReceived<*>

            if (!payload.contentEquals(other.payload)) return false

            return true
        }

        override fun hashCode(): Int {
            return payload.contentHashCode()
        }
    }

    data class Close<BinaryPayloadType>(val cause: Throwable?): WebSocketEvent<BinaryPayloadType>()
}

interface NotificationApi {
    suspend fun lastNotification(queryClient: String): NetworkResponse<EventResponse>

    suspend fun notificationsByBatch(querySize: Int, queryClient: String, querySince: String): NetworkResponse<NotificationResponse>

    /**
     * request Notifications from the beginning of time
     */
    suspend fun getAllNotifications(querySize: Int, queryClient: String): NetworkResponse<NotificationResponse>

    suspend fun listenToLiveEvents(clientId: String): Flow<WebSocketEvent<EventResponse>>

}
