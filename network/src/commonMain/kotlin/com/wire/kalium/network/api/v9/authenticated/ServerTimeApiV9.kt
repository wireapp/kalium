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
package com.wire.kalium.network.api.v9.authenticated

import com.wire.kalium.network.AuthenticatedNetworkClient
import com.wire.kalium.network.api.authenticated.time.ServerTimeDTO
import com.wire.kalium.network.api.v8.authenticated.ServerTimeApiV8
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.request.get

internal open class ServerTimeApiV9(private val authenticatedNetworkClient: AuthenticatedNetworkClient) :
    ServerTimeApiV8(authenticatedNetworkClient) {

    private val httpClient get() = authenticatedNetworkClient.httpClient

    override suspend fun getServerTime(): NetworkResponse<ServerTimeDTO> = wrapKaliumResponse {
        httpClient.get(PATH_SERVER_TIME)
    }

    companion object {
        const val PATH_SERVER_TIME = "/time"
    }
}
