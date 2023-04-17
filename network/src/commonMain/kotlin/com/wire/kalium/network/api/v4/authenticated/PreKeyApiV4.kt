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

package com.wire.kalium.network.api.v4.authenticated

import com.wire.kalium.network.AuthenticatedNetworkClient
import com.wire.kalium.network.api.base.authenticated.prekey.ListPrekeysResponse
import com.wire.kalium.network.api.v3.authenticated.PreKeyApiV3
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.request.post
import io.ktor.client.request.setBody

internal open class PreKeyApiV4 internal constructor(
    authenticatedNetworkClient: AuthenticatedNetworkClient
) : PreKeyApiV3(authenticatedNetworkClient) {

    override suspend fun getUsersPreKey(users: Map<String, Map<String, List<String>>>): NetworkResponse<ListPrekeysResponse> =
        wrapKaliumResponse<ListPrekeysResponse> {
            httpClient.post("$PATH_USERS/$PATH_List_PREKEYS") {
                setBody(users)
            }
        }
}
