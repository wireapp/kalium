package com.wire.kalium.network.api.notification

import com.wire.kalium.network.api.KaliumHttpResult

interface NotificationApi {
    suspend fun lastNotification(queryClient: String): KaliumHttpResult<NotificationResponse>

    suspend fun notificationsByBatch(querySize: Int, queryClient: String, querySince: String): KaliumHttpResult<NotificationPageResponse>

    /**
     * request Notifications from the beginning of time
     */
    suspend fun getAllNotifications(querySize: Int, queryClient: String): KaliumHttpResult<NotificationPageResponse>

}
