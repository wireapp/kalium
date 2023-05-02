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
import com.wire.kalium.network.api.base.authenticated.e2ei.AcmeResponse
import com.wire.kalium.network.api.base.authenticated.e2ei.AuthzDirectories
import com.wire.kalium.network.api.base.authenticated.e2ei.E2EIApi
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.kaliumLogger
import com.wire.kalium.network.serialization.JoseJson
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.NetworkResponse.*
import com.wire.kalium.network.utils.handleUnsuccessfulResponse
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.*

internal open class E2EIApiV4 internal constructor(private val authenticatedNetworkClient: AuthenticatedNetworkClient) :
    E2EIApi {

    private val httpClient get() = authenticatedNetworkClient.httpClient

    override suspend fun getAcmeDirectories(): NetworkResponse<AcmeDirectoriesResponse> =
        wrapKaliumResponse {
            httpClient.get("$TEMP_BASE_URL:$ACME_PORT/$PATH_ACME_DIRECTORIES")
        }

    override suspend fun getAuhzDirectories(): NetworkResponse<AuthzDirectories> =
        wrapKaliumResponse {
            httpClient.get("$TEMP_BASE_URL:$DEX_PORT/$PATH_DEX_CONFIGURATION")
        }

    override suspend fun getNewNonce(noncePath: String): NetworkResponse<String> =
        httpClient.prepareHead(noncePath).execute { httpResponse ->
            handleNewNonceResponse(httpResponse)
        }

    override suspend fun postAcmeRequest(requestDir: String, requestBody: ByteArray?): NetworkResponse<AcmeResponse> =
        httpClient.preparePost(requestDir)
        {
            contentType(ContentType.Application.JoseJson)
            requestBody?.let { setBody(requestBody) }
        }.execute { httpResponse ->
            handleAcmeRequestResponse(httpResponse)
        }

    override suspend fun getNewAccount(
        newAccountRequestUrl: String,
        newAccountRequestBody: ByteArray
    ): NetworkResponse<AcmeResponse> =
        postAcmeRequest(newAccountRequestUrl, newAccountRequestBody)

    override suspend fun getNewOrder(
        url: String,
        body: ByteArray
    ): NetworkResponse<AcmeResponse> = postAcmeRequest(url, body)

    override suspend fun getAuthzChallenge(
        url: String
    ): NetworkResponse<AcmeResponse> = postAcmeRequest(url, null)

    override suspend fun getWireNonce(clientId: String): NetworkResponse<String> =
        httpClient.prepareHead("${PATH_CLIENTS}/$clientId/${PATH_NONCE}")
        {
            contentType(ContentType.Application.JoseJson)
        }.execute { httpResponse ->
            handleNewNonceResponse(httpResponse)
        }

    override suspend fun getDpopAccessToken(clientId: String, dpopToken: String): NetworkResponse<AccessTokenResponse> =
        wrapKaliumResponse {
            httpClient.post("${PATH_CLIENTS}/$clientId/${PATH_ACCESS_TOKEN}")
            {
                headers.append("dpop", dpopToken)
            }
        }

    private suspend fun handleNewNonceResponse(
        httpResponse: HttpResponse
    ): NetworkResponse<String> =
        if (httpResponse.status.isSuccess() && httpResponse.headers["Replay-Nonce"] != null) {
            Success(httpResponse.headers[NONCE_HEADER_KEY].toString(), httpResponse)
        } else {
            handleUnsuccessfulResponse(httpResponse).also {
                if (it.kException is KaliumException.InvalidRequestError &&
                    it.kException.errorResponse.code == HttpStatusCode.Unauthorized.value
                ) {
                    kaliumLogger.d("Nonce error")
                }
            }
        }

    @OptIn(InternalAPI::class)
    private suspend fun handleAcmeRequestResponse(
        httpResponse: HttpResponse
    ): NetworkResponse<AcmeResponse> =
        if (httpResponse.status.isSuccess()) {
            Success(
                AcmeResponse(
                    nonce = httpResponse.headers[NONCE_HEADER_KEY].toString(),
                    response = httpResponse.body()
                ),
                httpResponse
            )
        } else {
            handleUnsuccessfulResponse(httpResponse).also {
                if (it.kException is KaliumException.InvalidRequestError &&
                    it.kException.errorResponse.code == HttpStatusCode.Unauthorized.value
                ) {
                    kaliumLogger.d("Nonce error")
                }
            }
        }


    override suspend fun sendNewAuthz(): NetworkResponse<Unit> {
        TODO("Not yet implemented")
    }

    override suspend fun sendNewOrder(): NetworkResponse<Unit> {
        TODO("Not yet implemented")
    }

    override suspend fun sendAuthzHandle(): NetworkResponse<Unit> {
        TODO("Not yet implemented")
    }

    override suspend fun sendAuthzClienId(): NetworkResponse<Unit> {
        TODO("Not yet implemented")
    }

    private companion object {
        const val PATH_CLIENTS = "clients"
        const val PATH_NONCE = "nonce"
        const val PATH_ACCESS_TOKEN = "access-token"
        const val TEMP_BASE_URL = "https://136.243.148.68"
        const val ACME_PORT = "9000"
        const val DEX_PORT = "5556"

        const val PATH_DEX_CONFIGURATION = "dex/.well-known/openid-configuration"
        const val PATH_ACME_DIRECTORIES = "acme/wire/directory"
        const val NONCE_HEADER_KEY = "Replay-Nonce"
    }
}
