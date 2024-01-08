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

package com.wire.kalium.network.api.v3.authenticated

import com.wire.kalium.network.AuthenticatedNetworkClient
import com.wire.kalium.network.AuthenticatedWebSocketClient
import com.wire.kalium.network.api.base.authenticated.notification.NotificationResponse
import com.wire.kalium.network.api.v2.authenticated.NotificationApiV2
import com.wire.kalium.network.tools.ServerConfigDTO
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.request.get
import io.ktor.client.request.parameter

internal open class NotificationApiV3 internal constructor(
    private val authenticatedNetworkClient: AuthenticatedNetworkClient,
    authenticatedWebSocketClient: AuthenticatedWebSocketClient,
    serverLinks: ServerConfigDTO.Links
) : NotificationApiV2(authenticatedNetworkClient, authenticatedWebSocketClient, serverLinks) {

    private val httpClient get() = authenticatedNetworkClient.httpClient

    override suspend fun notificationsCall(
        querySize: Int,
        queryClient: String,
        querySince: String?
    ): NetworkResponse<NotificationResponse> = wrapKaliumResponse {
        // Pretty much the same V0 request, but without the 404 overwrite
        httpClient.get(V0.PATH_NOTIFICATIONS) {
            parameter(V0.SIZE_QUERY_KEY, querySize)
            parameter(V0.CLIENT_QUERY_KEY, queryClient)
            querySince?.let { parameter(V0.SINCE_QUERY_KEY, it) }
        }
    }
}
