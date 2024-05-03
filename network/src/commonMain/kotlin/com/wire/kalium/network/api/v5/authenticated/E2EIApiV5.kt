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
import com.wire.kalium.network.api.base.authenticated.e2ei.AccessTokenResponse
import com.wire.kalium.network.api.v4.authenticated.E2EIApiV4
import com.wire.kalium.network.serialization.JoseJson
import com.wire.kalium.network.utils.CustomErrors
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.handleUnsuccessfulResponse
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.request.post
import io.ktor.client.request.prepareHead
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess

internal open class E2EIApiV5 internal constructor(
    private val authenticatedNetworkClient: AuthenticatedNetworkClient
) : E2EIApiV4() {
    private val httpClient get() = authenticatedNetworkClient.httpClient

    override suspend fun getWireNonce(clientId: String): NetworkResponse<String> =
        httpClient.prepareHead("$PATH_CLIENTS/$clientId/$PATH_NONCE") {
            contentType(ContentType.Application.JoseJson)
        }.execute { httpResponse ->
            handleNonceResponse(httpResponse)
        }

    private suspend fun handleNonceResponse(
        httpResponse: HttpResponse
    ): NetworkResponse<String> = if (httpResponse.status.isSuccess())
        httpResponse.headers[NONCE_HEADER_KEY]?.let { nonce ->
            NetworkResponse.Success(nonce, httpResponse)
        } ?: run {
            CustomErrors.MISSING_NONCE
        }
    else {
        handleUnsuccessfulResponse(httpResponse)
    }

    override suspend fun getAccessToken(clientId: String, dpopToken: String): NetworkResponse<AccessTokenResponse> = wrapKaliumResponse {
        httpClient.post("$PATH_CLIENTS/$clientId/$PATH_ACCESS_TOKEN") {
            headers.append(DPOP_HEADER_KEY, dpopToken)
        }
    }

    private companion object {
        const val PATH_CLIENTS = "clients"
        const val PATH_NONCE = "nonce"
        const val PATH_ACCESS_TOKEN = "access-token"

        const val DPOP_HEADER_KEY = "dpop"
        const val NONCE_HEADER_KEY = "Replay-Nonce"
    }
}
