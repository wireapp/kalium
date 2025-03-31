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

package com.wire.kalium.api.v8

import com.wire.kalium.api.ApiTest
import com.wire.kalium.api.TEST_BACKEND_CONFIG
import com.wire.kalium.mocks.responses.ErrorResponseJson
import com.wire.kalium.mocks.responses.NotificationEventsResponseJson
import com.wire.kalium.network.AuthenticatedWebSocketClient
import com.wire.kalium.network.api.authenticated.notification.ConsumableNotificationResponse
import com.wire.kalium.network.api.base.authenticated.notification.WebSocketEvent
import com.wire.kalium.network.api.v8.authenticated.NotificationApiV8
import com.wire.kalium.network.utils.NetworkResponse
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs

internal class NotificationApiV8Test : ApiTest() {

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
        val notificationsApi = NotificationApiV8(networkClient, fakeWebsocketClient(), TEST_BACKEND_CONFIG.links)
        val result = notificationsApi.consumeLiveEvents("")

        assertIs<NetworkResponse.Success<Flow<WebSocketEvent<ConsumableNotificationResponse>>>>(result)
    }

    @Test
    fun givenFailureLastNotificationResponse_whenListeningToLiveEvents_thenTheResponseIsParsedCorrectly() = runTest {
        val networkClient = mockAuthenticatedNetworkClient(
            ErrorResponseJson.valid.rawJson,
            statusCode = HttpStatusCode.BadRequest,
        )
        val notificationsApi = NotificationApiV8(networkClient, fakeWebsocketClient(), TEST_BACKEND_CONFIG.links)
        val result = notificationsApi.consumeLiveEvents("")

        assertIs<NetworkResponse.Error>(result)
    }

    private companion object {
        const val PATH_NOTIFICATIONS = "/events"
    }
}
