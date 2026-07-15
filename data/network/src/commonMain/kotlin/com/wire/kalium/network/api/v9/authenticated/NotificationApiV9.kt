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

@file:Suppress("TooGenericExceptionCaught")

package com.wire.kalium.network.api.v9.authenticated

import com.wire.kalium.logger.KaliumLogger.Companion.ApplicationFlow.EVENT_RECEIVER
import com.wire.kalium.network.AuthenticatedNetworkClient
import com.wire.kalium.network.AuthenticatedWebSocketClient
import com.wire.kalium.network.api.authenticated.notification.ConsumableNotificationResponse
import com.wire.kalium.network.api.authenticated.notification.EventAcknowledgeRequest
import com.wire.kalium.network.api.base.authenticated.notification.WebSocketEvent
import com.wire.kalium.network.api.unbound.configuration.ServerConfigDTO
import com.wire.kalium.network.api.v0.authenticated.NotificationApiV0.V0.CLIENT_QUERY_KEY
import com.wire.kalium.network.api.v0.authenticated.NotificationApiV0.V0.PATH_EVENTS
import com.wire.kalium.network.api.v0.authenticated.NotificationApiV0.V0.SYNC_MARKER_KEY
import com.wire.kalium.network.api.v8.authenticated.NotificationApiV8
import com.wire.kalium.network.kaliumLogger
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.tools.KtxSerializer
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.deleteSensitiveItemsFromJson
import com.wire.kalium.network.utils.mapSuccess
import com.wire.kalium.network.utils.setWSSUrl
import com.wire.kalium.network.utils.wrapRequest
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.websocket.Frame
import io.ktor.websocket.CloseReason
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.encodeToJsonElement

internal open class NotificationApiV9 internal constructor(
    private val authenticatedNetworkClient: AuthenticatedNetworkClient,
    private val authenticatedWebSocketClient: AuthenticatedWebSocketClient,
    val serverLinks: ServerConfigDTO.Links
) : NotificationApiV8(authenticatedNetworkClient, authenticatedWebSocketClient, serverLinks) {
    private var session: WebSocketSession? = null
    val mutex = Mutex()

    /**
     * Creates a new WebSocket session or returns an existing one.
     * @param clientId the clientId of the user to connect to the websocket
     */
    protected suspend fun getOrCreateAsyncEventsWebSocketSession(clientId: String, markerId: String, origin: String): WebSocketSession {
        mutex.withLock {
            if (session == null) {
                kaliumLogger.d("Creating new WebSocket session on $this by $origin")
                session = authenticatedWebSocketClient.createWebSocketSession(clientId) {
                    setWSSUrl(authenticatedWebSocketClient.createWSSUrl(shouldAddApiVersion = true), PATH_EVENTS)
                    parameter(CLIENT_QUERY_KEY, clientId)
                    parameter(SYNC_MARKER_KEY, markerId)
                }
            }
            return session!!
        }
    }

    override suspend fun acknowledgeEvents(
        clientId: String,
        markerId: String,
        eventAcknowledgeRequest: EventAcknowledgeRequest,
    ): NetworkResponse<Unit> {
        val session = getOrCreateAsyncEventsWebSocketSession(clientId, markerId, ::acknowledgeEvents.name)

        return KtxSerializer.json.encodeToJsonElement(eventAcknowledgeRequest).let { json ->
            try {
                session.outgoing.send(Frame.Text(json.toString()))
                kaliumLogger.i("Acknowledge event sent successfully $json on $this")
                NetworkResponse.Success(Unit, emptyMap(), 0)
            } catch (failure: Throwable) {
                kaliumLogger.e("Failed to send acknowledge event $json", failure)
                NetworkResponse.Error(KaliumException.GenericError(failure))
            }
        }
    }

    override suspend fun closeLiveEvents(): NetworkResponse<Unit> = try {
        val current = mutex.withLock { session }
        current?.close(CloseReason(CloseReason.Codes.NORMAL, "Live event owner closed"))
        current?.cancel()
        if (current != null) authenticatedWebSocketClient.releaseWebSocketSession(current)
        mutex.withLock {
            if (session === current) session = null
        }
        NetworkResponse.Success(Unit, emptyMap(), 0)
    } catch (failure: Throwable) {
        NetworkResponse.Error(KaliumException.GenericError(failure))
    }

    override suspend fun consumeLiveEvents(
        clientId: String,
        markerId: String
    ): NetworkResponse<Flow<WebSocketEvent<ConsumableNotificationResponse>>> =
        cookies().mapSuccess {
            flow {
                // TODO: Delete this once we can intercept and handle token refresh when connecting WebSocket
                //       WebSocket requests are not intercept-able, and they throw
                //       exceptions when the backend returns 401 instead of triggering a token refresh.
                //       This call to lastNotification will make sure that if the token is expired, it will be refreshed
                //       before attempting to open the websocket
                val session = getOrCreateAsyncEventsWebSocketSession(clientId, markerId, ::consumeLiveEvents.name)
                emitWebSocketEvents(session)
            }
        }

    private suspend fun cookies(): NetworkResponse<Unit> = wrapRequest {
        authenticatedNetworkClient.httpClient.get("/cookies")
    }

    private suspend fun FlowCollector<WebSocketEvent<ConsumableNotificationResponse>>.emitWebSocketEvents(
        defaultClientWebSocketSession: WebSocketSession
    ) {
        val logger = kaliumLogger.withFeatureId(EVENT_RECEIVER)
        logger.i("Websocket open")
        emit(WebSocketEvent.Open(shouldProcessPendingEvents = false))

        defaultClientWebSocketSession.incoming
            .consumeAsFlow()
            .onCompletion {
                withContext(NonCancellable) {
                    try {
                        defaultClientWebSocketSession.cancel()
                    } finally {
                        authenticatedWebSocketClient.releaseWebSocketSession(defaultClientWebSocketSession)
                        mutex.withLock {
                            if (session === defaultClientWebSocketSession) session = null
                        }
                    }
                }
                logger.w("Websocket Closed", it)
                emit(WebSocketEvent.Close(it))
            }
            .collect { frame ->
                logger.v("Websocket Received Frame: $frame")
                when (frame) {
                    is Frame.Binary -> {
                        // assuming here the byteArray is an ASCII/UTF-8 character set
                        val jsonString = frame.data.decodeToString()

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
