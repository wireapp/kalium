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

package com.wire.kalium.network.api.v5.authenticated

import com.wire.kalium.network.AuthenticatedNetworkClient
import com.wire.kalium.network.api.base.authenticated.self.UpdateSupportedProtocolsRequest
import com.wire.kalium.network.api.base.model.SupportedProtocolDTO
import com.wire.kalium.network.api.v4.authenticated.SelfApiV4
import com.wire.kalium.network.session.SessionManager
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.request.put
import io.ktor.client.request.setBody

internal open class SelfApiV5 internal constructor(
    authenticatedNetworkClient: AuthenticatedNetworkClient,
    sessionManager: SessionManager
) : SelfApiV4(authenticatedNetworkClient, sessionManager) {
    override suspend fun updateSupportedProtocols(
        protocols: List<SupportedProtocolDTO>
    ): NetworkResponse<Unit> = wrapKaliumResponse {
        httpClient.put("$PATH_SELF/$PATH_SUPPORTED_PROTOCOLS") {
            setBody(UpdateSupportedProtocolsRequest(protocols))
        }
    }

    companion object {
        const val PATH_SUPPORTED_PROTOCOLS = "supported-protocols"
    }
}
