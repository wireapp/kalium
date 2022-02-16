package com.wire.kalium.api.tools.json.api.notification

import com.wire.kalium.api.ApiTest
import com.wire.kalium.network.api.notification.EventContentDTO
import com.wire.kalium.network.api.notification.NotificationApiImpl
import com.wire.kalium.network.tools.BackendConfig
import com.wire.kalium.network.utils.isSuccessful
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

class NotificationApiTest : ApiTest {

    @Test
    fun givenAValidRequest_whenGettingNotificationsByBatch_thenTheRequestShouldBeConfiguredCorrectly() = runTest {
        val clientId = "cId"
        val since = "sinceId"
        val limit = 400
        val httpClient = mockAuthenticatedHttpClient(
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
        val notificationsApi = NotificationApiImpl(httpClient, BACKEND_CONFIG)

        notificationsApi.notificationsByBatch(limit, clientId, since)
    }

    @Test
    fun givenAValidRequest_whenGettingAllNotifications_thenTheRequestShouldBeConfiguredCorrectly() = runTest {
        val clientId = "cId"
        val limit = 400
        val httpClient = mockAuthenticatedHttpClient(
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
        val notificationsApi = NotificationApiImpl(httpClient, BACKEND_CONFIG)

        notificationsApi.getAllNotifications(limit, clientId)
    }

    @Test
    fun givenAValidResponseWithAnEventOfUnknownType_whenGettingNotificationsByBatch_thenTheResponseShouldBeParsedCorrectly() = runTest {
        val httpClient = mockAuthenticatedHttpClient(
            NotificationEventsResponseJson.notificationsWithUnknownEventAtFirstPosition,
            statusCode = HttpStatusCode.OK
        )
        val notificationsApi = NotificationApiImpl(httpClient, BACKEND_CONFIG)

        val result = notificationsApi.notificationsByBatch(1, "", "")

        assertTrue(result.isSuccessful())
        val notifications = result.value.notifications.first()

        val firstEvent = notifications.payload!!.first()
        assertIs<EventContentDTO.Unknown>(firstEvent)
    }

    @Test
    fun givenAValidResponseWithUnknownEventType_whenGettingAllNotifications_thenTheResponseShouldBeParsedCorrectly() = runTest {
        val httpClient = mockAuthenticatedHttpClient(
            NotificationEventsResponseJson.notificationsWithUnknownEventAtFirstPosition,
            statusCode = HttpStatusCode.OK
        )
        val notificationsApi = NotificationApiImpl(httpClient, BACKEND_CONFIG)

        val result = notificationsApi.getAllNotifications(1, "")

        assertTrue(result.isSuccessful())
        val notifications = result.value.notifications.first()

        val firstEvent = notifications.payload!!.first()
        assertIs<EventContentDTO.Unknown>(firstEvent)
    }

    private companion object {
        val BACKEND_CONFIG = BackendConfig(
            "apiBaseUrl", "accountsUrl",
            "websocketUrl", "blacklist", "teams", "website"
        )
        const val PATH_NOTIFICATIONS = "/notifications"
        const val SIZE_QUERY_KEY = "size"
        const val CLIENT_QUERY_KEY = "client"
        const val SINCE_QUERY_KEY = "since"
    }
}
