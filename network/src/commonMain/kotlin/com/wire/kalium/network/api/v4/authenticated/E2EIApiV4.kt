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
import com.wire.kalium.network.api.base.authenticated.e2ei.AccessTokenResponse
import com.wire.kalium.network.api.base.authenticated.e2ei.AcmeDirectoriesResponse
import com.wire.kalium.network.api.base.authenticated.e2ei.ACMEResponse
import com.wire.kalium.network.api.base.authenticated.e2ei.AuthzDirectoriesResponse
import com.wire.kalium.network.api.base.authenticated.e2ei.ChallengeResponse
import com.wire.kalium.network.api.base.authenticated.e2ei.E2EIApi
import com.wire.kalium.network.serialization.JoseJson
import com.wire.kalium.network.utils.CustomErrors
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.NetworkResponse.*
import com.wire.kalium.network.utils.flatMap
import com.wire.kalium.network.utils.handleUnsuccessfulResponse
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*

internal open class E2EIApiV4 internal constructor(private val authenticatedNetworkClient: AuthenticatedNetworkClient) : E2EIApi {

    private val httpClient get() = authenticatedNetworkClient.httpClient

    override suspend fun getACMEDirectories(): NetworkResponse<AcmeDirectoriesResponse> = wrapKaliumResponse {
        httpClient.get("$ACME_BASE_URL:$ACME_PORT/$PATH_ACME_DIRECTORIES")
    }

    override suspend fun getACMENonce(url: String): NetworkResponse<String> = httpClient.prepareHead(url).execute { httpResponse ->
        handleACMENonceResponse(httpResponse)
    }

    override suspend fun getAuhzDirectories(): NetworkResponse<AuthzDirectoriesResponse> = wrapKaliumResponse {
        httpClient.get("$ACME_BASE_URL:$DEX_PORT/$PATH_DEX_CONFIGURATION")
    }

    override suspend fun getAuthzChallenge(
        url: String
    ): NetworkResponse<ACMEResponse> = sendACMERequest(url)

    override suspend fun getNewAccount(
        url: String, body: ByteArray
    ): NetworkResponse<ACMEResponse> = sendACMERequest(url, body)

    override suspend fun getNewOrder(
        url: String, body: ByteArray
    ): NetworkResponse<ACMEResponse> = sendACMERequest(url, body)

    override suspend fun dpopChallenge(
        url: String, body: ByteArray
    ): NetworkResponse<ChallengeResponse> = sendChallengeRequest(url, body)

    override suspend fun oidcChallenge(
        url: String, body: ByteArray
    ): NetworkResponse<ChallengeResponse> = sendChallengeRequest(url, body)

    override suspend fun getWireNonce(clientId: String): NetworkResponse<String> =
        httpClient.prepareHead("${PATH_CLIENTS}/$clientId/${PATH_NONCE}") {
            contentType(ContentType.Application.JoseJson)
        }.execute { httpResponse ->
            handleACMENonceResponse(httpResponse)
        }

    override suspend fun getAccessToken(clientId: String, dpopToken: String): NetworkResponse<AccessTokenResponse> = wrapKaliumResponse {
        httpClient.post("${PATH_CLIENTS}/$clientId/${PATH_ACCESS_TOKEN}") {
            headers.append(DPOP_HEADER_KEY, dpopToken)
        }
    }

    private suspend fun handleACMENonceResponse(
        httpResponse: HttpResponse
    ): NetworkResponse<String> = if (httpResponse.status.isSuccess()) httpResponse.headers[NONCE_HEADER_KEY]?.let { nonce ->
        Success(nonce, httpResponse)
    } ?: run {
        CustomErrors.MISSING_NONCE
    }
    else {
        handleUnsuccessfulResponse(httpResponse)
    }

    override suspend fun sendACMERequest(url: String, body: ByteArray?): NetworkResponse<ACMEResponse> = httpClient.preparePost(url) {
        contentType(ContentType.Application.JoseJson)
        body?.let { setBody(body) }
    }.execute { httpResponse ->
        handleACMERequestResponse(httpResponse)
    }

    private suspend fun handleACMERequestResponse(
        httpResponse: HttpResponse
    ): NetworkResponse<ACMEResponse> = if (httpResponse.status.isSuccess()) {
        httpResponse.headers[NONCE_HEADER_KEY]?.let { nonce ->
            Success(
                ACMEResponse(
                    nonce, response = httpResponse.body()
                ), httpResponse
            )
        } ?: run {
            CustomErrors.MISSING_NONCE
        }
    } else {
        handleUnsuccessfulResponse(httpResponse)
    }

    private suspend fun sendChallengeRequest(url: String, body: ByteArray): NetworkResponse<ChallengeResponse> =
        wrapKaliumResponse<ChallengeResponse> {
            httpClient.post(url) {
                contentType(ContentType.Application.JoseJson)
                setBody(body)
            }
        }.flatMap { challengeResponse ->
            challengeResponse.headers[NONCE_HEADER_KEY.lowercase()]?.let { nonce ->
                Success(
                    ChallengeResponse(
                        type = challengeResponse.value.type,
                        url = challengeResponse.value.url,
                        status = challengeResponse.value.status,
                        token = challengeResponse.value.token,
                        nonce = nonce
                    ), challengeResponse.headers, challengeResponse.httpCode
                )

            } ?: run {
                CustomErrors.MISSING_NONCE
            }
        }

    private companion object {
        const val ACME_BASE_URL = "https://balderdash.hogwash.work"
        const val ACME_PORT = "9000"
        const val DEX_PORT = "5556"

        const val PATH_DEX_CONFIGURATION = "dex/.well-known/openid-configuration"
        const val PATH_ACME_DIRECTORIES = "acme/wire/directory"
        const val PATH_CLIENTS = "clients"
        const val PATH_NONCE = "nonce"
        const val PATH_ACCESS_TOKEN = "access-token"

        const val DPOP_HEADER_KEY = "dpop"
        const val NONCE_HEADER_KEY = "Replay-Nonce"
    }
}
