package com.wire.kalium.network.api.notification

import com.wire.kalium.network.utils.NetworkResponse
import kotlinx.coroutines.flow.Flow

interface NotificationApi {
    suspend fun lastNotification(queryClient: String): NetworkResponse<EventResponse>

    suspend fun notificationsByBatch(querySize: Int, queryClient: String, querySince: String): NetworkResponse<NotificationResponse>

    /**
     * request Notifications from the beginning of time
     */
    suspend fun getAllNotifications(querySize: Int, queryClient: String): NetworkResponse<NotificationResponse>

    suspend fun listenToLiveEvents(clientId: String): Flow<EventResponse>

}
