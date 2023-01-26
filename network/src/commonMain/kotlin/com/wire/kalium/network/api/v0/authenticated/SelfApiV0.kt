/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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

package com.wire.kalium.network.api.v0.authenticated

import com.wire.kalium.network.AuthenticatedNetworkClient
import com.wire.kalium.network.api.base.authenticated.self.ChangeHandleRequest
import com.wire.kalium.network.api.base.authenticated.self.SelfApi
import com.wire.kalium.network.api.base.authenticated.self.UserUpdateRequest
import com.wire.kalium.network.api.base.model.UserDTO
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.client.request.setBody

internal open class SelfApiV0 internal constructor(private val authenticatedNetworkClient: AuthenticatedNetworkClient) : SelfApi {

    private val httpClient get() = authenticatedNetworkClient.httpClient

    override suspend fun getSelfInfo(): NetworkResponse<UserDTO> = wrapKaliumResponse {
        httpClient.get(PATH_SELF)
    }

    override suspend fun updateSelf(userUpdateRequest: UserUpdateRequest): NetworkResponse<Unit> = wrapKaliumResponse {
        httpClient.put(PATH_SELF) {
            setBody(userUpdateRequest)
        }
    }

    override suspend fun changeHandle(request: ChangeHandleRequest): NetworkResponse<Unit> = wrapKaliumResponse {
        httpClient.put("$PATH_SELF/$PATH_HANDLE") {
            setBody(request)
        }
    }

    private companion object {
        const val PATH_SELF = "self"
        const val PATH_HANDLE = "handle"
    }
}
