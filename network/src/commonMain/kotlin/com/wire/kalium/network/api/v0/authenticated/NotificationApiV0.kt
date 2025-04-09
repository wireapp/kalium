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

package com.wire.kalium.network.api.v0.authenticated

import com.wire.kalium.logger.KaliumLogger.Companion.ApplicationFlow.EVENT_RECEIVER
import com.wire.kalium.network.AuthenticatedNetworkClient
import com.wire.kalium.network.AuthenticatedWebSocketClient
import com.wire.kalium.network.api.authenticated.notification.ConsumableNotificationResponse
import com.wire.kalium.network.api.authenticated.notification.EventResponse
import com.wire.kalium.network.api.authenticated.notification.NotificationResponse
import com.wire.kalium.network.api.base.authenticated.notification.NotificationApi
import com.wire.kalium.network.api.base.authenticated.notification.WebSocketEvent
import com.wire.kalium.network.api.model.ErrorResponse
import com.wire.kalium.network.api.unbound.configuration.ServerConfigDTO
import com.wire.kalium.network.api.v0.authenticated.NotificationApiV0.Hardcoded.NOTIFICATIONS_4O4_ERROR
import com.wire.kalium.network.api.v0.authenticated.NotificationApiV0.V0.CLIENT_QUERY_KEY
import com.wire.kalium.network.api.v0.authenticated.NotificationApiV0.V0.MINIMUM_QUERY_SIZE
import com.wire.kalium.network.api.v0.authenticated.NotificationApiV0.V0.MIN_API_VERSION_CONSUMABLE_EVENTS
import com.wire.kalium.network.api.v0.authenticated.NotificationApiV0.V0.PATH_AWAIT
import com.wire.kalium.network.api.v0.authenticated.NotificationApiV0.V0.PATH_LAST
import com.wire.kalium.network.api.v0.authenticated.NotificationApiV0.V0.PATH_NOTIFICATIONS
import com.wire.kalium.network.api.v0.authenticated.NotificationApiV0.V0.SINCE_QUERY_KEY
import com.wire.kalium.network.api.v0.authenticated.NotificationApiV0.V0.SIZE_QUERY_KEY
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.kaliumLogger
import com.wire.kalium.network.tools.KtxSerializer
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.deleteSensitiveItemsFromJson
import com.wire.kalium.network.utils.mapSuccess
import com.wire.kalium.network.utils.setWSSUrl
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion

internal open class NotificationApiV0 internal constructor(
    private val authenticatedNetworkClient: AuthenticatedNetworkClient,
    private val authenticatedWebSocketClient: AuthenticatedWebSocketClient,
    private val serverLinks: ServerConfigDTO.Links
) : NotificationApi {

    private val httpClient get() = authenticatedNetworkClient.httpClient

    override suspend fun mostRecentNotification(
        queryClient: String
    ): NetworkResponse<EventResponse> = wrapKaliumResponse {
        httpClient.get("$PATH_NOTIFICATIONS/$PATH_LAST") {
            parameter(CLIENT_QUERY_KEY, queryClient)
        }
    }

    override suspend fun oldestNotification(queryClient: String): NetworkResponse<EventResponse> =
        getAllNotifications(
            querySize = MINIMUM_QUERY_SIZE,
            queryClient = queryClient
        ).mapSuccess { it.notifications.first() }

    override suspend fun notificationsByBatch(
        querySize: Int,
        queryClient: String,
        querySince: String
    ): NetworkResponse<NotificationResponse> =
        notificationsCall(querySize = querySize, queryClient = queryClient, querySince = querySince)

    // TODO(refactor): rename this function. It gets the first page of notifications, not all of them.
    override suspend fun getAllNotifications(querySize: Int, queryClient: String): NetworkResponse<NotificationResponse> =
        notificationsCall(querySize = querySize, queryClient = queryClient, querySince = null)

    override suspend fun getServerTime(querySize: Int): NetworkResponse<String> =
        notificationsCall(querySize = querySize, queryClient = null, querySince = null).mapSuccess { it.time }

    protected open suspend fun notificationsCall(
        querySize: Int,
        queryClient: String?,
        querySince: String?
    ): NetworkResponse<NotificationResponse> {
        return wrapKaliumResponse({
            if (it.status.value != HttpStatusCode.NotFound.value) null
            else {
                // In case of 404, we ignore the content completely and fallback to a 404 response to match API V3
                NetworkResponse.Error(KaliumException.InvalidRequestError(NOTIFICATIONS_4O4_ERROR))
            }
        }) {
            httpClient.get(PATH_NOTIFICATIONS) {
                parameter(SIZE_QUERY_KEY, querySize)
                queryClient?.let { parameter(CLIENT_QUERY_KEY, it) }
                querySince?.let { parameter(SINCE_QUERY_KEY, it) }
            }
        }
    }

    @Deprecated("Starting API v8 prefer consumeLiveEvents instead", ReplaceWith("consumeLiveEvents(clientId)"))
    override suspend fun listenToLiveEvents(clientId: String): NetworkResponse<Flow<WebSocketEvent<EventResponse>>> =
        mostRecentNotification(clientId).mapSuccess {
            flow {
                // TODO: Delete this once we can intercept and handle token refresh when connecting WebSocket
                //       WebSocket requests are not intercept-able, and they throw
                //       exceptions when the backend returns 401 instead of triggering a token refresh.
                //       This call to lastNotification will make sure that if the token is expired, it will be refreshed
                //       before attempting to open the websocket
                val webSocketSession = authenticatedWebSocketClient.createWebSocketSession(clientId) {
                    setWSSUrl(Url(serverLinks.webSocket), PATH_AWAIT)
                    parameter(CLIENT_QUERY_KEY, clientId)
                }
                emitWebSocketEvents(webSocketSession)
            }
        }

    override suspend fun consumeLiveEvents(clientId: String): NetworkResponse<Flow<WebSocketEvent<ConsumableNotificationResponse>>> =
        getApiNotSupportedError(::consumeLiveEvents.name, MIN_API_VERSION_CONSUMABLE_EVENTS)

    override suspend fun acknowledgeEvents(clientId: String, deliveryTag: ULong) {
        getApiNotSupportedError(::acknowledgeEvents.name, MIN_API_VERSION_CONSUMABLE_EVENTS)
    }

    private suspend fun FlowCollector<WebSocketEvent<EventResponse>>.emitWebSocketEvents(
        defaultClientWebSocketSession: WebSocketSession
    ) {
        val logger = kaliumLogger.withFeatureId(EVENT_RECEIVER)
        logger.i("Websocket open")
        emit(WebSocketEvent.Open())

        defaultClientWebSocketSession.incoming
            .consumeAsFlow()
            .onCompletion {
                defaultClientWebSocketSession.close()
                logger.w("Websocket Closed", it)
                emit(WebSocketEvent.Close(it))
            }
            .collect { frame ->
                logger.v("Websocket Received Frame: $frame")
                when (frame) {
                    is Frame.Binary -> {
                        // assuming here the byteArray is an ASCII character set
                        val jsonString = io.ktor.utils.io.core.String(frame.data)

                        logger.v("Binary frame content: '${deleteSensitiveItemsFromJson(jsonString)}'")
                        val event = KtxSerializer.json.decodeFromString<EventResponse>(jsonString)
                        emit(WebSocketEvent.BinaryPayloadReceived(event))
                    }

                    else -> {
                        logger.v("Websocket frame not handled: $frame")
                        emit(WebSocketEvent.NonBinaryPayloadReceived(frame.data))
                    }
                }
            }
    }

    protected object V0 {
        const val PATH_AWAIT = "await"
        const val PATH_EVENTS = "events"
        const val PATH_NOTIFICATIONS = "notifications"
        const val PATH_LAST = "last"
        const val SIZE_QUERY_KEY = "size"
        const val CLIENT_QUERY_KEY = "client"
        const val SINCE_QUERY_KEY = "since"

        /**
         * The backend doesn't allow queries smaller than a minimum
         * value.
         */
        const val MINIMUM_QUERY_SIZE = 100
        const val MIN_API_VERSION_CONSUMABLE_EVENTS = 8
    }

    internal object Hardcoded {
        val NOTIFICATIONS_4O4_ERROR = ErrorResponse(
            code = HttpStatusCode.NotFound.value,
            message = "Event or client not found",
            label = "missing_events_or_client_(hardcoded_v0_response)"
        )
    }
}
