/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

package com.wire.kalium.network.api.base.authenticated.notification

import com.wire.kalium.network.api.authenticated.notification.ConsumableNotificationResponse
import com.wire.kalium.network.api.authenticated.notification.EventResponse
import com.wire.kalium.network.api.authenticated.notification.NotificationResponse
import com.wire.kalium.network.api.base.authenticated.BaseApi
import com.wire.kalium.network.utils.NetworkResponse
import kotlinx.coroutines.flow.Flow

sealed class WebSocketEvent<BinaryPayloadType> {
    class Open<BinaryPayloadType> : WebSocketEvent<BinaryPayloadType>()

    data class BinaryPayloadReceived<BinaryPayloadType>(val payload: BinaryPayloadType) : WebSocketEvent<BinaryPayloadType>()

    data class NonBinaryPayloadReceived<BinaryPayloadType>(val payload: ByteArray) : WebSocketEvent<BinaryPayloadType>() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false

            other as NonBinaryPayloadReceived<*>

            return payload.contentEquals(other.payload)
        }

        override fun hashCode(): Int {
            return payload.contentHashCode()
        }
    }

    data class Close<BinaryPayloadType>(val cause: Throwable?) : WebSocketEvent<BinaryPayloadType>()
}

interface NotificationApi : BaseApi {
    suspend fun mostRecentNotification(queryClient: String): NetworkResponse<EventResponse>

    suspend fun notificationsByBatch(querySize: Int, queryClient: String, querySince: String): NetworkResponse<NotificationResponse>

    suspend fun oldestNotification(queryClient: String): NetworkResponse<EventResponse>

    /**
     * request Notifications from the beginning of time
     */
    suspend fun getAllNotifications(querySize: Int, queryClient: String): NetworkResponse<NotificationResponse>

    suspend fun getServerTime(querySize: Int): NetworkResponse<String>

    @Deprecated("Starting API v8 prefer consumeLiveEvents instead", ReplaceWith("consumeLiveEvents(clientId)"))
    suspend fun listenToLiveEvents(clientId: String): NetworkResponse<Flow<WebSocketEvent<EventResponse>>>
    suspend fun consumeLiveEvents(clientId: String): NetworkResponse<Flow<WebSocketEvent<ConsumableNotificationResponse>>>

}
