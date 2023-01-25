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
import com.wire.kalium.network.api.base.authenticated.serverpublickey.MLSPublicKeyApi
import com.wire.kalium.network.api.base.authenticated.serverpublickey.MLSPublicKeysDTO
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.request.get

open class MLSPublicKeyApiV0 internal constructor(private val authenticatedNetworkClient: AuthenticatedNetworkClient) : MLSPublicKeyApi {

    // TODO: must be disabled for v0 and v1

    private val httpClient get() = authenticatedNetworkClient.httpClient

    override suspend fun getMLSPublicKeys(): NetworkResponse<MLSPublicKeysDTO> =
        wrapKaliumResponse { httpClient.get("$PATH_MLS/$PATH_PUBLIC_KEYS") }

    private companion object {
        const val PATH_PUBLIC_KEYS = "public-keys"
        const val PATH_MLS = "mls"
    }
}
