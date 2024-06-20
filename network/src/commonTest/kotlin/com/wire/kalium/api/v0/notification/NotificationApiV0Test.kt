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

package com.wire.kalium.api.v0.notification

import com.wire.kalium.api.ApiTest
import com.wire.kalium.api.TEST_BACKEND_CONFIG
import com.wire.kalium.api.json.model.ErrorResponseJson
import com.wire.kalium.mocks.responses.NotificationEventsResponseJson
import com.wire.kalium.network.AuthenticatedWebSocketClient
import com.wire.kalium.network.api.authenticated.notification.EventContentDTO
import com.wire.kalium.network.api.authenticated.notification.EventResponse
import com.wire.kalium.network.api.authenticated.notification.NotificationResponse
import com.wire.kalium.network.api.base.authenticated.notification.WebSocketEvent
import com.wire.kalium.network.api.v0.authenticated.NotificationApiV0
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.isSuccessful
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

internal class NotificationApiV0Test : ApiTest() {

    /**
     * Doesn't do anything.
     * TODO: Actually mock WS with data
     */
    private fun fakeWebsocketClient(): AuthenticatedWebSocketClient = mockWebsocketClient()

    @Test
    fun givenAValidRequest_whenGettingNotificationsByBatch_thenTheRequestShouldBeConfiguredCorrectly() = runTest {
        val clientId = "cId"
        val since = "sinceId"
        val limit = 400
        val networkClient = mockAuthenticatedNetworkClient(
            NotificationEventsResponseJson.notificationsWithUnknownEventAtFirstPosition,
            statusCode = HttpStatusCode.OK,
            assertion = {
                assertGet()
                assertPathEqual(PATH_NOTIFICATIONS)
                assertQueryParameter(CLIENT_QUERY_KEY, clientId)
                assertQueryParameter(SIZE_QUERY_KEY, limit.toString())
                assertQueryParameter(SINCE_QUERY_KEY, since)
            }
        )
        val notificationsApi = NotificationApiV0(networkClient, fakeWebsocketClient(), TEST_BACKEND_CONFIG.links)

        notificationsApi.notificationsByBatch(limit, clientId, since)
    }

    @Test
    fun givenAValidRequest_whenGettingAllNotifications_thenTheRequestShouldBeConfiguredCorrectly() = runTest {
        val clientId = "cId"
        val limit = 400
        val networkClient = mockAuthenticatedNetworkClient(
            NotificationEventsResponseJson.notificationsWithUnknownEventAtFirstPosition,
            statusCode = HttpStatusCode.OK,
            assertion = {
                assertGet()
                assertPathEqual(PATH_NOTIFICATIONS)
                assertQueryParameter(CLIENT_QUERY_KEY, clientId)
                assertQueryParameter(SIZE_QUERY_KEY, limit.toString())
                assertQueryDoesNotExist(SINCE_QUERY_KEY)
            }
        )
        val notificationsApi = NotificationApiV0(networkClient, fakeWebsocketClient(), TEST_BACKEND_CONFIG.links)

        notificationsApi.getAllNotifications(limit, clientId)
    }

    @Test
    fun givenAValidResponseWithAnEventOfUnknownType_whenGettingNotificationsByBatch_thenTheResponseShouldBeParsedCorrectly() = runTest {
        val networkClient = mockAuthenticatedNetworkClient(
            NotificationEventsResponseJson.notificationsWithUnknownEventAtFirstPosition,
            statusCode = HttpStatusCode.OK
        )
        val notificationsApi = NotificationApiV0(networkClient, fakeWebsocketClient(), TEST_BACKEND_CONFIG.links)

        val result = notificationsApi.notificationsByBatch(1, "", "")

        assertTrue(result.isSuccessful())
        val notifications = result.value.notifications.first()

        val firstEvent = notifications.payload!!.first()
        assertIs<EventContentDTO.Unknown>(firstEvent)
    }

    @Test
    fun givenAValidResponseWithUnknownEventType_whenGettingAllNotifications_thenTheResponseShouldBeParsedCorrectly() = runTest {
        val networkClient = mockAuthenticatedNetworkClient(
            NotificationEventsResponseJson.notificationsWithUnknownEventAtFirstPosition,
            statusCode = HttpStatusCode.OK
        )
        val notificationsApi = NotificationApiV0(networkClient, fakeWebsocketClient(), TEST_BACKEND_CONFIG.links)

        val result = notificationsApi.getAllNotifications(1, "")

        assertTrue(result.isSuccessful())
        val notifications = result.value.notifications.first()

        val firstEvent = notifications.payload!!.first()
        assertIs<EventContentDTO.Unknown>(firstEvent)
    }

    @Test
    fun given404Response_whenGettingAllNotifications_thenTheHardcodedV0ErrorIsBeingForwarded() = runTest {
        val networkClient = mockAuthenticatedNetworkClient(
            NotificationEventsResponseJson.notificationsWithUnknownEventAtFirstPosition,
            statusCode = HttpStatusCode.NotFound
        )
        val notificationsApi = NotificationApiV0(networkClient, fakeWebsocketClient(), TEST_BACKEND_CONFIG.links)

        val result = notificationsApi.getAllNotifications(1, "")

        assertFalse(result.isSuccessful())

        val exception = result.kException
        assertIs<KaliumException.InvalidRequestError>(exception)
        assertEquals(NotificationApiV0.Hardcoded.NOTIFICATIONS_4O4_ERROR, exception.errorResponse)
    }

    @Test
    fun givenSuccessResponse_whenGettingAllNotifications_thenTheResponseIsParsedCorrectly() = runTest {
        val networkClient = mockAuthenticatedNetworkClient(
            NotificationEventsResponseJson.notificationsWithUnknownEventAtFirstPosition,
            statusCode = HttpStatusCode.OK
        )
        val notificationsApi = NotificationApiV0(networkClient, fakeWebsocketClient(), TEST_BACKEND_CONFIG.links)
        val result = notificationsApi.getAllNotifications(1, "")

        assertIs<NetworkResponse.Success<NotificationResponse>>(result)
    }

    @Test
    fun givenSuccessLastNotificationResponse_whenListeningToLiveEvents_thenTheResponseIsParsedCorrectly() = runTest {
        val networkClient = mockAuthenticatedNetworkClient(
            NotificationEventsResponseJson.notificationWithLastEvent,
            statusCode = HttpStatusCode.OK
        )
        val notificationsApi = NotificationApiV0(networkClient, fakeWebsocketClient(), TEST_BACKEND_CONFIG.links)
        val result = notificationsApi.listenToLiveEvents("")

        assertIs<NetworkResponse.Success<Flow<WebSocketEvent<EventResponse>>>>(result)
    }

    @Test
    fun givenFailureLastNotificationResponse_whenListeningToLiveEvents_thenTheResponseIsParsedCorrectly() = runTest {
        val networkClient = mockAuthenticatedNetworkClient(
            ErrorResponseJson.valid.rawJson,
            statusCode = HttpStatusCode.BadRequest,
        )
        val notificationsApi = NotificationApiV0(networkClient, fakeWebsocketClient(), TEST_BACKEND_CONFIG.links)
        val result = notificationsApi.listenToLiveEvents("")

        assertIs<NetworkResponse.Error>(result)
    }

    @Test
    fun givenClientId_whenGettingOldestNotification_thenShouldPassCorrectIdParameter() = runTest {
        val clientId = "cId"
        val networkClient = mockAuthenticatedNetworkClient(
            NotificationEventsResponseJson.notificationWithLastEvent,
            statusCode = HttpStatusCode.OK,
            assertion = {
                assertQueryParameter(CLIENT_QUERY_KEY, clientId)
                assertQueryDoesNotExist(SINCE_QUERY_KEY)
            }
        )
        val notificationsApi = NotificationApiV0(networkClient, fakeWebsocketClient(), TEST_BACKEND_CONFIG.links)
        notificationsApi.oldestNotification(clientId)
    }

    @Test
    fun givenSuccessResponse_whenGettingOldestNotification_thenTheResponseIsParsedCorrectly() = runTest {
        val jsonProvider = NotificationEventsResponseJson.notificationResponsePageWithSingleEvent

        val networkClient = mockAuthenticatedNetworkClient(
            jsonProvider.rawJson,
            statusCode = HttpStatusCode.OK
        )
        val notificationsApi = NotificationApiV0(networkClient, fakeWebsocketClient(), TEST_BACKEND_CONFIG.links)
        val result = notificationsApi.oldestNotification("")

        assertTrue(result.isSuccessful())
        assertEquals(jsonProvider.serializableData.notifications.first(), result.value)
    }

    private companion object {
        const val PATH_NOTIFICATIONS = "/notifications"
        const val SIZE_QUERY_KEY = "size"
        const val CLIENT_QUERY_KEY = "client"
        const val SINCE_QUERY_KEY = "since"
    }
}
