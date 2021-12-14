package com.wire.kalium.network.api.notification

import com.wire.kalium.network.utils.NetworkResponse

interface NotificationApi {
    suspend fun lastNotification(queryClient: String): NetworkResponse<NotificationResponse>

    suspend fun notificationsByBatch(querySize: Int, queryClient: String, querySince: String): NetworkResponse<NotificationPageResponse>

    /**
     * request Notifications from the beginning of time
     */
    suspend fun getAllNotifications(querySize: Int, queryClient: String): NetworkResponse<NotificationPageResponse>

}
