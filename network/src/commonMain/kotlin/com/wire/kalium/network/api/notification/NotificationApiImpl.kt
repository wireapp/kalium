package com.wire.kalium.network.api.notification

import com.wire.kalium.logger.KaliumLogger.Companion.ApplicationFlow.EVENT_RECEIVER
import com.wire.kalium.network.AuthenticatedNetworkClient
import com.wire.kalium.network.AuthenticatedWebSocketClient
import com.wire.kalium.network.kaliumLogger
import com.wire.kalium.network.tools.KtxSerializer
import com.wire.kalium.network.tools.ServerConfigDTO
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.setWSSUrl
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.call.body
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.websocket.Frame
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.serialization.decodeFromString

class NotificationApiImpl internal constructor(
    private val authenticatedNetworkClient: AuthenticatedNetworkClient,
    private val authenticatedWebSocketClient: AuthenticatedWebSocketClient,
    private val serverLinks: ServerConfigDTO.Links
) : NotificationApi {

    private val httpClient get() = authenticatedNetworkClient.httpClient

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

    // TODO(refactor): rename this function. It gets the first page of notifications, not all of them.
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

    override suspend fun listenToLiveEvents(clientId: String): Flow<WebSocketEvent<EventResponse>> = flow {
        authenticatedWebSocketClient
            .createDisposableHttpClient()
            .webSocket({
                setWSSUrl(serverLinks.webSocket, PATH_AWAIT)
                parameter(CLIENT_QUERY_KEY, clientId)
            }, {
                emitWebSocketEvents(this)
            })
    }

    private suspend fun FlowCollector<WebSocketEvent<EventResponse>>.emitWebSocketEvents(
        defaultClientWebSocketSession: DefaultClientWebSocketSession
    ) {
        kaliumLogger.i("Websocket open")
        emit(WebSocketEvent.Open())

        defaultClientWebSocketSession.incoming
            .consumeAsFlow()
            .onCompletion {
                kaliumLogger.w("Websocket Closed", it)
                emit(WebSocketEvent.Close(it))
            }
            .collect { frame ->
                kaliumLogger.withFeatureId(EVENT_RECEIVER).v("Websocket Received Frame: $frame")
                when (frame) {
                    is Frame.Binary -> {
                        // assuming here the byteArray is an ASCII character set
                        val jsonString = io.ktor.utils.io.core.String(frame.data)
                        kaliumLogger.withFeatureId(EVENT_RECEIVER).v("Binary frame content: '$jsonString'")
                        val event = KtxSerializer.json.decodeFromString<EventResponse>(jsonString)
                        emit(WebSocketEvent.BinaryPayloadReceived(event))
                    }
                    else -> {
                        kaliumLogger.withFeatureId(EVENT_RECEIVER).v("Websocket frame not handled: $frame")
                        emit(WebSocketEvent.NonBinaryPayloadReceived(frame.data))
                    }
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
