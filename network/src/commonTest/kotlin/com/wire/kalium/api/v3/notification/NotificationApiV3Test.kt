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

package com.wire.kalium.api.v3.notification

import com.wire.kalium.api.ApiTest
import com.wire.kalium.api.TEST_BACKEND_CONFIG
import com.wire.kalium.api.json.model.ErrorResponseJson
import com.wire.kalium.network.AuthenticatedWebSocketClient
import com.wire.kalium.network.api.v3.authenticated.NotificationApiV3
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.utils.isSuccessful
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
internal class NotificationApiV3Test : ApiTest() {

    /**
     * Doesn't do anything.
     * TODO: Actually mock WS with data
     */
    private fun fakeWebsocketClient(): AuthenticatedWebSocketClient = mockWebsocketClient()

    @Test
    fun given404Response_whenGettingAllNotifications_thenTheErrorResponseIsBeingForwarded() = runTest {
        val errorResponseJson = ErrorResponseJson.valid
        val networkClient = mockAuthenticatedNetworkClient(
            errorResponseJson.rawJson,
            statusCode = HttpStatusCode.NotFound
        )
        val notificationsApi = NotificationApiV3(networkClient, fakeWebsocketClient(), TEST_BACKEND_CONFIG.links)

        val result = notificationsApi.getAllNotifications(1, "")

        assertFalse(result.isSuccessful())
        val exception = result.kException
        assertIs<KaliumException.InvalidRequestError>(exception)

        assertEquals(errorResponseJson.serializableData, exception.errorResponse)
    }
}
