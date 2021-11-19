package com.wire.kalium.api.notification

import com.wire.kalium.api.KaliumHttpResult
import com.wire.kalium.api.wrapKaliumResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.receive
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse

class NotificationApiImpl(private val httpClient: HttpClient) : NotificationApi{
    override suspend fun lastNotification(queryClient: String
    ): KaliumHttpResult<NotificationResponse> = wrapKaliumResponse {
        httpClient.get<HttpResponse>(path = "$PATH_NOTIFICATIONS$PATH_LAST") {
            parameter(CLIENT_QUERY_KEY, queryClient)
        }.receive()
    }

    override suspend fun notificationsByBatch(
            querySize: Int,
            queryClient: String,
            querySince: String
    ): KaliumHttpResult<NotificationPageResponse> = notificationsCall(querySize = querySize, queryClient = queryClient, querySince = querySince)

    override suspend fun getAllNotifications(querySize: Int, queryClient: String): KaliumHttpResult<NotificationPageResponse> =
    notificationsCall(querySize = querySize, queryClient = queryClient, querySince = null)

    private suspend fun notificationsCall(
            querySize: Int,
            queryClient: String,
            querySince: String?
    ): KaliumHttpResult<NotificationPageResponse> = wrapKaliumResponse{
        httpClient.get<HttpResponse>(path = PATH_NOTIFICATIONS) {
            parameter(SIZE_QUERY_KEY, querySize)
            parameter(CLIENT_QUERY_KEY, queryClient)
            querySince?.let { parameter(SINCE_QUERY_KEY, it) }
        }.receive()
    }

    companion object {
        private const val PATH_NOTIFICATIONS = "/notifications"
        private const val PATH_LAST = "/last"
        private const val SIZE_QUERY_KEY = "size"
        private const val CLIENT_QUERY_KEY = "client"
        private const val SINCE_QUERY_KEY = "since"
    }
}
