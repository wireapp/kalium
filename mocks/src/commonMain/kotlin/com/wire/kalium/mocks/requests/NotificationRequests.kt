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
package com.wire.kalium.mocks.requests

import com.wire.kalium.mocks.extensions.toJsonString
import com.wire.kalium.mocks.responses.CommonResponses
import com.wire.kalium.mocks.responses.NotificationEventsResponseJson
import com.wire.kalium.network.utils.MockUnboundNetworkClient
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode

object NotificationRequests {

    /**
     * URL Paths
     */
    private const val PATH_LAST_NOTIFICATIONS = "${CommonResponses.BASE_PATH_V1}notifications/last"
    private const val PATH_PUSH_TOKENS = "${CommonResponses.BASE_PATH_V1}push/tokens"

    /**
     * Request / Responses
     */
    private val lastNotificationsApiRequestSuccess = MockUnboundNetworkClient.TestRequestHandler(
        path = PATH_LAST_NOTIFICATIONS,
        httpMethod = HttpMethod.Get,
        responseBody = NotificationEventsResponseJson.mostRecentEvent.toJsonString(),
        statusCode = HttpStatusCode.OK,
    )

    private val registerTokenResponse = """ 
            {
             "app":"8218398",
             "client":"123456",
             "token":"oaisjdoiasjd",
             "transport":"GCM"
                }
            """.trimIndent()

    private val pushTokenApiRequestSuccess = MockUnboundNetworkClient.TestRequestHandler(
        path = PATH_PUSH_TOKENS,
        httpMethod = HttpMethod.Post,
        responseBody = registerTokenResponse,
        statusCode = HttpStatusCode.OK,
    )

    val notificationsRequestResponseSuccess = listOf(
        pushTokenApiRequestSuccess,
        lastNotificationsApiRequestSuccess
    )
}
