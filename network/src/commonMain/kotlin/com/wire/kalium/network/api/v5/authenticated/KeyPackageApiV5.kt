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
import com.wire.kalium.network.api.authenticated.keypackage.ClaimedKeyPackageList
import com.wire.kalium.network.api.authenticated.keypackage.KeyPackage
import com.wire.kalium.network.api.base.authenticated.keypackage.KeyPackageApi
import com.wire.kalium.network.api.authenticated.keypackage.KeyPackageCountDTO
import com.wire.kalium.network.api.authenticated.keypackage.KeyPackageList
import com.wire.kalium.network.api.v4.authenticated.KeyPackageApiV4
import com.wire.kalium.network.kaliumLogger
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.handleUnsuccessfulResponse
import com.wire.kalium.network.utils.wrapFederationResponse
import com.wire.kalium.network.utils.wrapKaliumResponse
import com.wire.kalium.util.int.toHexString
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody

internal open class KeyPackageApiV5 internal constructor(
    private val authenticatedNetworkClient: AuthenticatedNetworkClient
) : KeyPackageApiV4() {
    private val httpClient get() = authenticatedNetworkClient.httpClient

    override suspend fun claimKeyPackages(
        param: KeyPackageApi.Param
    ): NetworkResponse<ClaimedKeyPackageList> = wrapKaliumResponse(unsuccessfulResponseOverride = { response ->
        wrapFederationResponse(response, delegatedHandler = { handleUnsuccessfulResponse(response) })
    }) {
        httpClient.post("$PATH_KEY_PACKAGES/$PATH_CLAIM/${param.user.domain}/${param.user.value}") {
            param.selfClientId?.let {
                parameter(QUERY_SKIP_OWN, it)
            }
            parameter(QUERY_CIPHER_SUITE, param.cipherSuite)
        }
    }

    override suspend fun uploadKeyPackages(
        clientId: String,
        keyPackages: List<KeyPackage>
    ): NetworkResponse<Unit> =
        wrapKaliumResponse {
            kaliumLogger.v("Keypackages Count to upload: ${keyPackages.size}")
            httpClient.post("$PATH_KEY_PACKAGES/$PATH_SELF/$clientId") {
                setBody(KeyPackageList(keyPackages))
            }
        }

    override suspend fun replaceKeyPackages(
        clientId: String,
        keyPackages: List<KeyPackage>,
        cipherSuite: Int
    ): NetworkResponse<Unit> =
        wrapKaliumResponse {
            kaliumLogger.v("Keypackages Count to replace: ${keyPackages.size}")
            httpClient.put("$PATH_KEY_PACKAGES/$PATH_SELF/$clientId") {
                setBody(KeyPackageList(keyPackages))
                parameter(QUERY_CIPHER_SUITES, cipherSuite.toHexString())
            }
        }

    override suspend fun getAvailableKeyPackageCount(
        clientId: String,
        cipherSuite: Int,
    ): NetworkResponse<KeyPackageCountDTO> =
        wrapKaliumResponse {
            httpClient.get("$PATH_KEY_PACKAGES/$PATH_SELF/$clientId/$PATH_COUNT") {
                parameter(QUERY_CIPHER_SUITE, cipherSuite.toHexString())
            }
        }

    private companion object {
        const val PATH_KEY_PACKAGES = "mls/key-packages"
        const val PATH_CLAIM = "claim"
        const val PATH_SELF = "self"
        const val PATH_COUNT = "count"
        const val QUERY_SKIP_OWN = "skip_own"
        const val QUERY_CIPHER_SUITE = "ciphersuite"
        const val QUERY_CIPHER_SUITES = "ciphersuites"
    }
}
