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

package com.wire.kalium.network.api.v4.authenticated

import com.wire.kalium.network.AuthenticatedNetworkClient
import com.wire.kalium.network.api.base.authenticated.connection.ConnectionDTO
import com.wire.kalium.network.api.base.authenticated.connection.ConnectionStateDTO
import com.wire.kalium.network.api.base.authenticated.connection.UpdateConnectionRequest
import com.wire.kalium.network.api.base.model.UserId
import com.wire.kalium.network.api.v3.authenticated.ConnectionApiV3
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.wrapFederationResponse
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.utils.io.errors.IOException

internal open class ConnectionApiV4 internal constructor(
    authenticatedNetworkClient: AuthenticatedNetworkClient
) : ConnectionApiV3(authenticatedNetworkClient) {

    override suspend fun createConnection(userId: UserId): NetworkResponse<ConnectionDTO> = try {
        httpClient.post("$PATH_CONNECTIONS_ENDPOINTS/${userId.domain}/${userId.value}").let { response ->
            wrapFederationResponse(response) { wrapKaliumResponse { response } }
        }
    } catch (e: IOException) {
        NetworkResponse.Error(KaliumException.GenericError(e))
    }

    override suspend fun updateConnection(userId: UserId, connectionStatus: ConnectionStateDTO): NetworkResponse<ConnectionDTO> =
        try {
            httpClient.put("$PATH_CONNECTIONS_ENDPOINTS/${userId.domain}/${userId.value}") {
                setBody(UpdateConnectionRequest(connectionStatus))
            }.let { response ->
                wrapFederationResponse(response) { wrapKaliumResponse { response } }
            }
        } catch (e: IOException) {
            NetworkResponse.Error(KaliumException.GenericError(e))
        }
}
