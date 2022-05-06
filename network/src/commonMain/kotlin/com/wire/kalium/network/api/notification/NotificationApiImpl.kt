package com.wire.kalium.network.api.notification

import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.tools.KtxSerializer
import com.wire.kalium.network.tools.ServerConfigDTO
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.setWSSUrl
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.websocket.Frame
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.serialization.decodeFromString

class NotificationApiImpl(private val httpClient: HttpClient, private val serverConfigDTO: ServerConfigDTO) : NotificationApi {
    override suspend fun lastNotification(
        queryClient: String
    ): NetworkResponse<EventResponse> = wrapKaliumResponse {
        httpClient.get("$PATH_NOTIFICATIONS/$PATH_LAST") {
            parameter(CLIENT_QUERY_KEY, queryClient)
        }
    }

    override suspend fun notificationsByBatch(
        querySize: Int,
        queryClient: String,
        querySince: String
    ): NetworkResponse<NotificationResponse> =
        notificationsCall(querySize = querySize, queryClient = queryClient, querySince = querySince)

    //FIXME: This function does not get all notifications, just the first page.
    override suspend fun getAllNotifications(querySize: Int, queryClient: String): NetworkResponse<NotificationResponse> =
        notificationsCall(querySize = querySize, queryClient = queryClient, querySince = null)

    private suspend fun notificationsCall(
        querySize: Int,
        queryClient: String,
        querySince: String?
    ): NetworkResponse<NotificationResponse> {
        return wrapKaliumResponse({
            if (it.status.value != 404) null
            else {
                val body = it.body<NotificationResponse>().copy(isMissingNotifications = true)
                NetworkResponse.Success(body, it)
            }
        }) {
            httpClient.get(PATH_NOTIFICATIONS) {
                parameter(SIZE_QUERY_KEY, querySize)
                parameter(CLIENT_QUERY_KEY, queryClient)
                querySince?.let { parameter(SINCE_QUERY_KEY, it) }
            }
        }
    }

    override suspend fun listenToLiveEvents(clientId: String): Flow<EventResponse> = httpClient.webSocketSession(
        method = HttpMethod.Get
    ) {
        setWSSUrl(serverConfigDTO.webSocketBaseUrl, PATH_AWAIT)
        parameter(CLIENT_QUERY_KEY, clientId)
    }.incoming
        .consumeAsFlow()
        .catch { it.printStackTrace() }
        .mapNotNull { frame ->
            println("###### Received Frame: $frame ######")
            when (frame) {
                is Frame.Binary -> {
                    // assuming here the byteArray is an ASCII character set
                    val jsonString = io.ktor.utils.io.core.String(frame.data)
                    val event = KtxSerializer.json.decodeFromString<EventResponse>(jsonString)
                    event
                }
                else -> {
                    println("###### Websocket frame not handled: $frame ######")
                    null
                }
            }
        }

    private companion object {
        const val PATH_AWAIT = "await"
        const val PATH_NOTIFICATIONS = "notifications"
        const val PATH_LAST = "last"
        const val SIZE_QUERY_KEY = "size"
        const val CLIENT_QUERY_KEY = "client"
        const val SINCE_QUERY_KEY = "since"
    }
}
