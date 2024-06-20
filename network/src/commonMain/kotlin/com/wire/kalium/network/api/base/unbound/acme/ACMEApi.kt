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
import com.wire.kalium.network.api.unbound.acme.ACMEAuthorizationResponse
import com.wire.kalium.network.api.unbound.acme.ACMEResponse
import com.wire.kalium.network.api.unbound.acme.AcmeDirectoriesResponse
import com.wire.kalium.network.api.unbound.acme.AuthorizationResponse
import com.wire.kalium.network.api.unbound.acme.ChallengeResponse
import com.wire.kalium.network.api.unbound.acme.DtoAuthorizationChallengeType
import com.wire.kalium.network.api.unbound.acme.FederationCertificateChainResponse
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.serialization.JoseJson
import com.wire.kalium.network.tools.KtxSerializer
import com.wire.kalium.network.utils.CustomErrors
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.flatMap
import com.wire.kalium.network.utils.handleUnsuccessfulResponse
import com.wire.kalium.network.utils.mapSuccess
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.prepareHead
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.http.protocolWithAuthority
import okio.IOException

interface ACMEApi {
    suspend fun getTrustAnchors(discoveryUrl: String): NetworkResponse<ByteArray>
    suspend fun getACMEDirectories(discoveryUrl: String): NetworkResponse<AcmeDirectoriesResponse>
    suspend fun getACMENonce(url: String): NetworkResponse<String>
    suspend fun sendACMERequest(url: String, body: ByteArray? = null): NetworkResponse<ACMEResponse>
    suspend fun sendAuthorizationRequest(url: String, body: ByteArray? = null): NetworkResponse<ACMEAuthorizationResponse>
    suspend fun sendChallengeRequest(url: String, body: ByteArray): NetworkResponse<ChallengeResponse>

    /**
     * Retrieves the ACME federation certificate chain from the specified discovery URL.
     *
     * @param discoveryUrl The non-blank URL of the ACME federation discovery endpoint.
     * @return A [NetworkResponse] object containing the certificate chain as a list of strings.
     */
    suspend fun getACMEFederationCertificateChain(discoveryUrl: String): NetworkResponse<List<String>>
    suspend fun getClientDomainCRL(url: String): NetworkResponse<ByteArray>
}

class ACMEApiImpl internal constructor(
    private val unboundNetworkClient: UnboundNetworkClient,
    private val unboundClearTextTrafficNetworkClient: UnboundNetworkClient
) : ACMEApi {
    private val httpClient get() = unboundNetworkClient.httpClient
    private val clearTextTrafficHttpClient get() = unboundClearTextTrafficNetworkClient.httpClient

    override suspend fun getTrustAnchors(discoveryUrl: String): NetworkResponse<ByteArray> {
        val protocolWithAuthority = Url(discoveryUrl).protocolWithAuthority

        if (discoveryUrl.isBlank() || protocolWithAuthority.isBlank()) {
            return NetworkResponse.Error(
                KaliumException.GenericError(
                    IllegalArgumentException(
                        "getTrustAnchors: Url cannot be empty or protocolWithAuthority cannot be empty" +
                                ", is urlBlank = ${discoveryUrl.isBlank()}" +
                                ", is protocolWithAuthorityBlank = ${protocolWithAuthority.isBlank()}"
                    )
                )
            )
        }

        return wrapKaliumResponse {
            httpClient.get("$protocolWithAuthority/$PATH_ACME_ROOTS_PEM")
        }

    }

    override suspend fun getACMEDirectories(discoveryUrl: String): NetworkResponse<AcmeDirectoriesResponse> {
        if (discoveryUrl.isBlank()) {
            return NetworkResponse.Error(KaliumException.GenericError(IllegalArgumentException("getACMEDirectories: Url cannot be empty")))
        }

        return wrapKaliumResponse {
            httpClient.get(discoveryUrl)
        }
    }

    override suspend fun getACMENonce(url: String): NetworkResponse<String> {
        return try {
            if (url.isBlank()) {
                return NetworkResponse.Error(KaliumException.GenericError(IllegalArgumentException("sendACMERequest: Url cannot be empty")))
            }
            httpClient.prepareHead(url).execute { httpResponse ->
                handleACMENonceResponse(httpResponse)

            }
        } catch (e: IOException) {
            NetworkResponse.Error(KaliumException.GenericError(e))
        }
    }

    private suspend fun handleACMENonceResponse(httpResponse: HttpResponse): NetworkResponse<String> =
        if (httpResponse.status.isSuccess()) httpResponse.headers[NONCE_HEADER_KEY]?.let { nonce ->
            NetworkResponse.Success(nonce, httpResponse)
        } ?: run {
            CustomErrors.MISSING_NONCE
        } else {
            handleUnsuccessfulResponse(httpResponse)
        }

    override suspend fun sendACMERequest(
        url: String,
        body: ByteArray?
    ): NetworkResponse<ACMEResponse> {
        return try {
            if (url.isBlank()) {
                return NetworkResponse.Error(KaliumException.GenericError(IllegalArgumentException("sendACMERequest: Url cannot be empty")))
            }
            httpClient.preparePost(url) {
                contentType(ContentType.Application.JoseJson)
                body?.let { setBody(body) }
            }.execute { httpResponse ->
                handleACMERequestResponse(httpResponse)
            }
        } catch (e: IOException) {
            NetworkResponse.Error(KaliumException.GenericError(e))
        }
    }

    override suspend fun sendAuthorizationRequest(url: String, body: ByteArray?): NetworkResponse<ACMEAuthorizationResponse> {
        if (url.isBlank()) {
            return NetworkResponse.Error(
                KaliumException.GenericError(
                    IllegalArgumentException("sendAuthorizationRequest: Url cannot be empty")
                )
            )
        }

        return wrapKaliumResponse<String> {
            httpClient.post(url) {
                contentType(ContentType.Application.JoseJson)
                setBody(body)
                accept(ContentType.Application.Json)
            }
        }.flatMap { challengeResponse -> // this is the json response as string
            runCatching {
                val type: DtoAuthorizationChallengeType =
                    KtxSerializer.json.decodeFromString<AuthorizationResponse>(challengeResponse.value).let {
                        it.challenges.firstOrNull()?.type
                    } ?: return@flatMap CustomErrors.MISSING_CHALLENGE

                challengeResponse.headers[NONCE_HEADER_KEY.lowercase()]?.let { nonce ->
                    NetworkResponse.Success(
                        ACMEAuthorizationResponse(
                            nonce = nonce,
                            location = challengeResponse.headers[LOCATION_HEADER_KEY],
                            response = challengeResponse.value.encodeToByteArray(),
                            challengeType = type
                        ), challengeResponse.headers, challengeResponse.httpCode
                    )
                } ?: run {
                    CustomErrors.MISSING_NONCE
                }
            }.getOrElse { unhandledException ->
                // since we are handling manually our network exceptions for this endpoint, handle ie: no host exception
                NetworkResponse.Error(KaliumException.GenericError(unhandledException))
            }
        }

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

    override suspend fun sendChallengeRequest(url: String, body: ByteArray): NetworkResponse<ChallengeResponse> {
        if (url.isBlank()) {
            return NetworkResponse.Error(
                KaliumException.GenericError(
                    IllegalArgumentException("sendChallengeRequest: Url cannot be empty")
                )
            )
        }

        return wrapKaliumResponse<ChallengeResponse> {
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
                        target = challengeResponse.value.target,
                        nonce = nonce
                    ), challengeResponse.headers, challengeResponse.httpCode
                )
            } ?: run {
                CustomErrors.MISSING_NONCE
            }
        }
    }

    override suspend fun getACMEFederationCertificateChain(discoveryUrl: String): NetworkResponse<List<String>> {
        val protocolWithAuthority = Url(discoveryUrl).protocolWithAuthority
        if (discoveryUrl.isBlank() || protocolWithAuthority.isBlank()) {
            return NetworkResponse.Error(
                KaliumException.GenericError(
                    IllegalArgumentException(
                        "getACMEFederation: Url cannot be empty, " +
                                "is urlBlank = ${discoveryUrl.isBlank()}, " +
                                "is protocolWithAuthorityBlank = ${protocolWithAuthority.isBlank()}"
                    )
                )
            )
        }

        return wrapKaliumResponse<FederationCertificateChainResponse> {
            httpClient.get("$protocolWithAuthority/$PATH_ACME_FEDERATION")
        }.mapSuccess { it.certificates }
    }

    override suspend fun getClientDomainCRL(url: String): NetworkResponse<ByteArray> {
        if (url.isBlank()) {
            return NetworkResponse.Error(
                KaliumException.GenericError(
                    IllegalArgumentException("getClientDomainCRL: Url cannot be empty")
                )
            )
        }

        return wrapKaliumResponse {
            val httpUrl = URLBuilder(url).apply {
                this.protocol = URLProtocol.HTTP
            }.build()
            clearTextTrafficHttpClient.get(httpUrl)
        }
    }

    private companion object {
        const val PATH_ACME_FEDERATION = "federation"
        const val PATH_ACME_ROOTS_PEM = "roots.pem"

        const val NONCE_HEADER_KEY = "Replay-Nonce"
        const val LOCATION_HEADER_KEY = "location"
    }
}
