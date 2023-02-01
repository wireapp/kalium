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
import com.wire.kalium.network.api.base.authenticated.keypackage.ClaimedKeyPackageList
import com.wire.kalium.network.api.base.authenticated.keypackage.KeyPackage
import com.wire.kalium.network.api.base.authenticated.keypackage.KeyPackageApi
import com.wire.kalium.network.api.base.authenticated.keypackage.KeyPackageCountDTO
import com.wire.kalium.network.api.base.authenticated.keypackage.KeyPackageList
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody

internal open class KeyPackageApiV0 internal constructor(
    private val authenticatedNetworkClient: AuthenticatedNetworkClient
) : KeyPackageApi {

    protected val httpClient get() = authenticatedNetworkClient.httpClient

    override suspend fun claimKeyPackages(
        param: KeyPackageApi.Param
    ): NetworkResponse<ClaimedKeyPackageList> = wrapKaliumResponse {
        httpClient.post("$PATH_KEY_PACKAGES/$PATH_CLAIM/${param.user.domain}/${param.user.value}") {
            if (param is KeyPackageApi.Param.SkipOwnClient) {
                parameter(QUERY_SKIP_OWN, param.selfClientId)
            }
        }
    }

    override suspend fun uploadKeyPackages(
        clientId: String,
        keyPackages: List<KeyPackage>
    ): NetworkResponse<Unit> =
        wrapKaliumResponse {
            httpClient.post("$PATH_KEY_PACKAGES/$PATH_SELF/$clientId") {
                setBody(KeyPackageList(keyPackages))
            }
        }

    override suspend fun getAvailableKeyPackageCount(clientId: String): NetworkResponse<KeyPackageCountDTO> =
        wrapKaliumResponse { httpClient.get("$PATH_KEY_PACKAGES/$PATH_SELF/$clientId/$PATH_COUNT") }

    private companion object {
        const val PATH_KEY_PACKAGES = "mls/key-packages"
        const val PATH_CLAIM = "claim"
        const val PATH_SELF = "self"
        const val PATH_COUNT = "count"
        const val QUERY_SKIP_OWN = "skip_own"
    }
}
