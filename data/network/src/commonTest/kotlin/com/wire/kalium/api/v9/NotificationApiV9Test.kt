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

package com.wire.kalium.api.v9

import com.wire.kalium.api.ApiTest
import com.wire.kalium.api.TEST_BACKEND_CONFIG
import com.wire.kalium.mocks.responses.ErrorResponseJson
import com.wire.kalium.mocks.responses.NotificationEventsResponseJson
import com.wire.kalium.network.AuthenticatedWebSocketClient
import com.wire.kalium.network.api.authenticated.notification.ConsumableNotificationResponse
import com.wire.kalium.network.api.base.authenticated.notification.WebSocketEvent
import com.wire.kalium.network.api.v9.authenticated.NotificationApiV9
import com.wire.kalium.network.api.authenticated.notification.AcknowledgeData
import com.wire.kalium.network.api.authenticated.notification.AcknowledgeType
import com.wire.kalium.network.api.authenticated.notification.EventAcknowledgeRequest
import com.wire.kalium.network.kaliumLogger
import com.wire.kalium.network.tools.KtxSerializer
import com.wire.kalium.network.utils.MockWebSocketSession
import com.wire.kalium.network.utils.NetworkResponse
import io.ktor.client.engine.mock.MockEngine
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.encodeToJsonElement
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

internal class NotificationApiV9Test : ApiTest() {

    /**
     * Doesn't do anything.
     * TODO: Actually mock WS with data
     */
    private fun fakeWebsocketClient(): AuthenticatedWebSocketClient = mockWebsocketClient()

    @Test
    fun givenSuccessLastNotificationResponse_whenListeningToLiveEvents_thenTheResponseIsParsedCorrectly() = runTest {
        val networkClient = mockAuthenticatedNetworkClient(
            NotificationEventsResponseJson.notificationWithLastEvent,
            statusCode = HttpStatusCode.OK
        )
        val notificationsApi = NotificationApiV9(networkClient, fakeWebsocketClient(), TEST_BACKEND_CONFIG.links)
        val result = notificationsApi.consumeLiveEvents("some-client", "some-marker")

        assertIs<NetworkResponse.Success<Flow<WebSocketEvent<ConsumableNotificationResponse>>>>(result)
    }

    @Test
    fun givenFailureLastNotificationResponse_whenListeningToLiveEvents_thenTheResponseIsParsedCorrectly() = runTest {
        val networkClient = mockAuthenticatedNetworkClient(
            ErrorResponseJson.valid.rawJson,
            statusCode = HttpStatusCode.BadRequest,
        )
        val notificationsApi = NotificationApiV9(networkClient, fakeWebsocketClient(), TEST_BACKEND_CONFIG.links)
        val result = notificationsApi.consumeLiveEvents("some-client", "some-marker")

        assertIs<NetworkResponse.Error>(result)
    }

    @Test
    fun strictAcknowledgementUsesOnlyTheOpenSession() = runTest {
        val session = MockWebSocketSession(Job())
        var sessionsOpened = 0
        val websocket = AuthenticatedWebSocketClient(MockEngine { error("No request expected") }, TEST_BEARER_AUTH_PROVIDER,
            TEST_BACKEND_CONFIG, kaliumLogger) { _, _ -> sessionsOpened++; session }
        val networkClient = mockAuthenticatedNetworkClient("", statusCode = HttpStatusCode.OK)
        val notificationsApi = NotificationApiV9(networkClient, websocket, TEST_BACKEND_CONFIG.links)
        val ack = EventAcknowledgeRequest(AcknowledgeType.ACK, AcknowledgeData(7UL, false))

        assertIs<NetworkResponse.Error>(notificationsApi.acknowledgeEventsOnOpenSession("client", "marker", ack))
        assertEquals(0, sessionsOpened)

        val events = assertIs<NetworkResponse.Success<Flow<WebSocketEvent<ConsumableNotificationResponse>>>>(
            notificationsApi.consumeLiveEvents("client", "marker")
        ).value
        val opened = CompletableDeferred<Unit>()
        val collecting = launch { events.collect { if (it is WebSocketEvent.Open) opened.complete(Unit) } }
        opened.await()

        assertIs<NetworkResponse.Success<Unit>>(notificationsApi.acknowledgeEventsOnOpenSession("client", "marker", ack))
        val frame = assertIs<Frame.Text>((session.outgoing as ReceiveChannel<Frame>).tryReceive().getOrNull())
        assertEquals(KtxSerializer.json.encodeToJsonElement(ack).toString(), frame.readText())
        assertEquals(1, sessionsOpened)

        // A failed session cancels its outgoing channel; the acknowledgement fails without cancelling the caller.
        (session.outgoing as ReceiveChannel<Frame>).cancel()
        assertIs<NetworkResponse.Error>(notificationsApi.acknowledgeEventsOnOpenSession("client", "marker", ack))
        collecting.cancel()
    }

    private companion object {
        const val PATH_NOTIFICATIONS = "/events"
    }
}
