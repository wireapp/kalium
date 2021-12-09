package com.wire.kalium.network.api.notification

import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.*
import io.ktor.client.request.*

class NotificationApiImpl(private val httpClient: HttpClient) : NotificationApi {
    override suspend fun lastNotification(
        queryClient: String
    ): NetworkResponse<NotificationResponse> = wrapKaliumResponse {
        httpClient.get(path = "$PATH_NOTIFICATIONS$PATH_LAST") {
            parameter(CLIENT_QUERY_KEY, queryClient)
        }
    }

    override suspend fun notificationsByBatch(
        querySize: Int,
        queryClient: String,
        querySince: String
    ): NetworkResponse<NotificationPageResponse> =
        notificationsCall(querySize = querySize, queryClient = queryClient, querySince = querySince)

    override suspend fun getAllNotifications(querySize: Int, queryClient: String): NetworkResponse<NotificationPageResponse> =
        notificationsCall(querySize = querySize, queryClient = queryClient, querySince = null)

    private suspend fun notificationsCall(
        querySize: Int,
        queryClient: String,
        querySince: String?
    ): NetworkResponse<NotificationPageResponse> = wrapKaliumResponse {
        httpClient.get(path = PATH_NOTIFICATIONS) {
            parameter(SIZE_QUERY_KEY, querySize)
            parameter(CLIENT_QUERY_KEY, queryClient)
            querySince?.let { parameter(SINCE_QUERY_KEY, it) }
        }
    }

    companion object {
        private const val PATH_NOTIFICATIONS = "/notifications"
        private const val PATH_LAST = "/last"
        private const val SIZE_QUERY_KEY = "size"
        private const val CLIENT_QUERY_KEY = "client"
        private const val SINCE_QUERY_KEY = "since"
    }
}
