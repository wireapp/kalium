/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

package com.wire.kalium.network.api.base.unauthenticated.systemsettings

import com.wire.kalium.network.UnauthenticatedNetworkClient
import com.wire.kalium.network.api.unauthenticated.systemsettings.UnauthorizedSettingsResponse
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.request.get

interface UnauthorizedSettingsApi {
    suspend fun settings(): NetworkResponse<UnauthorizedSettingsResponse>
}

class UnauthorizedSettingsApiImpl internal constructor(
    private val unauthenticatedNetworkClient: UnauthenticatedNetworkClient
) : UnauthorizedSettingsApi {

    private val httpClient get() = unauthenticatedNetworkClient.httpClient

    override suspend fun settings(): NetworkResponse<UnauthorizedSettingsResponse> = wrapKaliumResponse {
        httpClient.get(PATH)
    }

    private companion object {
        const val PATH = "system/settings/unauthorized"
    }
}
