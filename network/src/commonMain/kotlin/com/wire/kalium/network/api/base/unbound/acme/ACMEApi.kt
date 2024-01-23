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
package com.wire.kalium.network.api.base.unbound.acme

import com.wire.kalium.network.UnboundNetworkClient
import com.wire.kalium.network.serialization.JoseJson
import com.wire.kalium.network.utils.CustomErrors
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.flatMap
import com.wire.kalium.network.utils.handleUnsuccessfulResponse
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.prepareHead
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess

interface ACMEApi {
    suspend fun getTrustAnchors(acmeUrl: String): NetworkResponse<CertificateChain>
    suspend fun getACMEDirectories(discoveryUrl: String): NetworkResponse<AcmeDirectoriesResponse>
    suspend fun getACMENonce(url: String): NetworkResponse<String>
    suspend fun sendACMERequest(url: String, body: ByteArray? = null): NetworkResponse<ACMEResponse>
    suspend fun sendChallengeRequest(url: String, body: ByteArray): NetworkResponse<ChallengeResponse>
    suspend fun getACMEFederation(baseUrl: String): NetworkResponse<CertificateChain>
}

class ACMEApiImpl internal constructor(
    private val unboundNetworkClient: UnboundNetworkClient
) : ACMEApi {
    private val httpClient get() = unboundNetworkClient.httpClient

    override suspend fun getTrustAnchors(acmeUrl: String): NetworkResponse<CertificateChain> = wrapKaliumResponse {
        httpClient.get("$acmeUrl/$PATH_ACME_ROOTS_PEM")
    }

    override suspend fun getACMEDirectories(discoveryUrl: String): NetworkResponse<AcmeDirectoriesResponse> = wrapKaliumResponse {
        httpClient.get(discoveryUrl)
    }

    override suspend fun getACMENonce(url: String): NetworkResponse<String> =
        httpClient.prepareHead(url).execute { httpResponse ->
            handleACMENonceResponse(httpResponse)
        }

    private suspend fun handleACMENonceResponse(httpResponse: HttpResponse): NetworkResponse<String> =
        if (httpResponse.status.isSuccess()) httpResponse.headers[NONCE_HEADER_KEY]?.let { nonce ->
            NetworkResponse.Success(nonce, httpResponse)
        } ?: run {
            CustomErrors.MISSING_NONCE
        }
        else {
            handleUnsuccessfulResponse(httpResponse)
        }

    override suspend fun sendACMERequest(url: String, body: ByteArray?): NetworkResponse<ACMEResponse> =
        httpClient.preparePost(url) {
            contentType(ContentType.Application.JoseJson)
            body?.let { setBody(body) }
        }.execute { httpResponse ->
            handleACMERequestResponse(httpResponse)
        }

    private suspend fun handleACMERequestResponse(httpResponse: HttpResponse): NetworkResponse<ACMEResponse> =
        if (httpResponse.status.isSuccess()) {
            httpResponse.headers[NONCE_HEADER_KEY]?.let { nonce ->
                NetworkResponse.Success(
                    ACMEResponse(
                        nonce,
                        response = httpResponse.body(),
                        location = httpResponse.headers[LOCATION_HEADER_KEY].toString()
                    ), httpResponse
                )
            } ?: run {
                CustomErrors.MISSING_NONCE
            }
        } else {
            handleUnsuccessfulResponse(httpResponse)
        }

    override suspend fun sendChallengeRequest(url: String, body: ByteArray): NetworkResponse<ChallengeResponse> =
        wrapKaliumResponse<ChallengeResponse> {
            httpClient.post(url) {
                contentType(ContentType.Application.JoseJson)
                setBody(body)
            }
        }.flatMap { challengeResponse ->
            challengeResponse.headers[NONCE_HEADER_KEY.lowercase()]?.let { nonce ->
                NetworkResponse.Success(
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

    override suspend fun getACMEFederation(baseUrl: String): NetworkResponse<CertificateChain> = wrapKaliumResponse {
        httpClient.get("$baseUrl/$PATH_ACME_FEDERATION")
    }

    private companion object {
        const val PATH_ACME_FEDERATION = "federation"
        const val PATH_ACME_ROOTS_PEM = "roots.pem"

        const val NONCE_HEADER_KEY = "Replay-Nonce"
        const val LOCATION_HEADER_KEY = "location"
    }

}
