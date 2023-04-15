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
import com.wire.kalium.network.api.base.authenticated.prekey.ListPrekeysResponse
import com.wire.kalium.network.api.base.authenticated.prekey.PreKeyApi
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody

internal open class PreKeyApiV0 internal constructor(private val authenticatedNetworkClient: AuthenticatedNetworkClient) : PreKeyApi {

    protected val httpClient get() = authenticatedNetworkClient.httpClient
    override suspend fun getUsersPreKey(users: Map<String, Map<String, List<String>>>): NetworkResponse<ListPrekeysResponse> =
        wrapKaliumResponse {
            httpClient.post("$PATH_USERS/$PATH_List_PREKEYS") {
                setBody(users)
            }
        }

    override suspend fun getClientAvailablePrekeys(clientId: String): NetworkResponse<List<Int>> = wrapKaliumResponse {
        httpClient.get("$PATH_CLIENTS/$clientId/$PATH_PRE_KEY")
    }

    protected companion object {
        const val PATH_USERS = "users"
        const val PATH_CLIENTS = "clients"
        const val PATH_PRE_KEY = "prekeys"
        const val PATH_List_PREKEYS = "list-prekeys"
    }
}
