/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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

package com.wire.kalium.network.api.v8.authenticated

import com.wire.kalium.logger.KaliumLogger.Companion.ApplicationFlow.EVENT_RECEIVER
import com.wire.kalium.network.AuthenticatedNetworkClient
import com.wire.kalium.network.AuthenticatedWebSocketClient
import com.wire.kalium.network.api.authenticated.notification.ConsumableNotificationResponse
import com.wire.kalium.network.api.authenticated.notification.EventAcknowledgeRequest
import com.wire.kalium.network.api.base.authenticated.notification.WebSocketEvent
import com.wire.kalium.network.api.unbound.configuration.ServerConfigDTO
import com.wire.kalium.network.api.v0.authenticated.NotificationApiV0.V0.CLIENT_QUERY_KEY
import com.wire.kalium.network.api.v0.authenticated.NotificationApiV0.V0.PATH_EVENTS
import com.wire.kalium.network.api.v7.authenticated.NotificationApiV7
import com.wire.kalium.network.kaliumLogger
import com.wire.kalium.network.tools.KtxSerializer
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.deleteSensitiveItemsFromJson
import com.wire.kalium.network.utils.mapSuccess
import com.wire.kalium.network.utils.setWSSUrl
import io.ktor.client.request.parameter
import io.ktor.http.Url
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.isActive
import kotlinx.serialization.json.encodeToJsonElement

internal open class NotificationApiV8 internal constructor(
    authenticatedNetworkClient: AuthenticatedNetworkClient,
    private val authenticatedWebSocketClient: AuthenticatedWebSocketClient,
    val serverLinks: ServerConfigDTO.Links
) : NotificationApiV7(authenticatedNetworkClient, authenticatedWebSocketClient, serverLinks) {

    private var session: WebSocketSession? = null

    /**
     * Creates a new WebSocket session or returns an existing one.
     * @param clientId the clientId of the user to connect to the websocket
     */
    protected suspend fun getOrCreateAsyncEventsWebSocketSession(clientId: String): WebSocketSession {
        if (session == null || session?.isActive == false) {
            session = authenticatedWebSocketClient.createWebSocketSession(clientId) {
                setWSSUrl(Url(serverLinks.webSocket), PATH_EVENTS)
                parameter(CLIENT_QUERY_KEY, clientId)
            }
        }
        return session!!
    }

    override suspend fun acknowledgeEvents(clientId: String, eventAcknowledgeRequest: EventAcknowledgeRequest) {
        val session = getOrCreateAsyncEventsWebSocketSession(clientId)
        KtxSerializer.json.encodeToJsonElement(eventAcknowledgeRequest).let { json ->
            session.outgoing.send(Frame.Binary(true, json.toString().encodeToByteArray()))
        }
    }

    override suspend fun consumeLiveEvents(clientId: String): NetworkResponse<Flow<WebSocketEvent<ConsumableNotificationResponse>>> =
        mostRecentNotification(clientId).mapSuccess {
            flow {
                // TODO: Delete this once we can intercept and handle token refresh when connecting WebSocket
                //       WebSocket requests are not intercept-able, and they throw
                //       exceptions when the backend returns 401 instead of triggering a token refresh.
                //       This call to lastNotification will make sure that if the token is expired, it will be refreshed
                //       before attempting to open the websocket
                val session = getOrCreateAsyncEventsWebSocketSession(clientId)
                emitWebSocketEvents(session)
            }
        }

    private suspend fun FlowCollector<WebSocketEvent<ConsumableNotificationResponse>>.emitWebSocketEvents(
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
                        val event = KtxSerializer.json.decodeFromString<ConsumableNotificationResponse>(jsonString)
                        emit(WebSocketEvent.BinaryPayloadReceived(event))
                    }

                    else -> {
                        logger.v("Websocket frame not handled: $frame")
                        emit(WebSocketEvent.NonBinaryPayloadReceived(frame.data))
                    }
                }
            }
    }
}
