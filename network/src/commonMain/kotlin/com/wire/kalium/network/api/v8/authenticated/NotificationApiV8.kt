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

import com.wire.kalium.network.AuthenticatedNetworkClient
import com.wire.kalium.network.AuthenticatedWebSocketClient
import com.wire.kalium.network.api.authenticated.notification.ConsumableNotificationResponse
import com.wire.kalium.network.api.base.authenticated.notification.WebSocketEvent
import com.wire.kalium.network.api.unbound.configuration.ServerConfigDTO
import com.wire.kalium.network.api.v7.authenticated.NotificationApiV7
import com.wire.kalium.network.utils.NetworkResponse
import kotlinx.coroutines.flow.Flow

internal open class NotificationApiV8 internal constructor(
    authenticatedNetworkClient: AuthenticatedNetworkClient,
    authenticatedWebSocketClient: AuthenticatedWebSocketClient,
    serverLinks: ServerConfigDTO.Links
) : NotificationApiV7(authenticatedNetworkClient, authenticatedWebSocketClient, serverLinks) {

    override suspend fun consumeLiveEvents(clientId: String): NetworkResponse<Flow<WebSocketEvent<ConsumableNotificationResponse>>> {
        TODO("Not yet implemented")
    }
}
