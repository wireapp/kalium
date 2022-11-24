package com.wire.kalium.api.v0.notification

import com.wire.kalium.api.ApiTest
import com.wire.kalium.api.TEST_BACKEND_CONFIG
import com.wire.kalium.model.NotificationEventsResponseJson
import com.wire.kalium.network.AuthenticatedWebSocketClient
import com.wire.kalium.network.api.base.authenticated.notification.EventContentDTO
import com.wire.kalium.network.api.base.authenticated.notification.NotificationResponse
import com.wire.kalium.network.api.v0.authenticated.NotificationApiV0
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.isSuccessful
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationApiV0Test : ApiTest {

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
    fun given404Response_whenGettingAllNotifications_thenTheErrorIsBeingForwarded() = runTest {
        val networkClient = mockAuthenticatedNetworkClient(
            NotificationEventsResponseJson.notificationsWithUnknownEventAtFirstPosition,
            statusCode = HttpStatusCode.NotFound
        )
        val notificationsApi = NotificationApiV0(networkClient, fakeWebsocketClient(), TEST_BACKEND_CONFIG.links)

        val result = notificationsApi.getAllNotifications(1, "")

        assertFalse(result.isSuccessful())
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

    private companion object {
        const val PATH_NOTIFICATIONS = "/notifications"
        const val SIZE_QUERY_KEY = "size"
        const val CLIENT_QUERY_KEY = "client"
        const val SINCE_QUERY_KEY = "since"
    }
}
