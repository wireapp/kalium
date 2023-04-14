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
import com.wire.kalium.network.api.base.authenticated.e2ei.AcmeDirectoriesResponse
import com.wire.kalium.network.api.base.authenticated.e2ei.E2EIApi
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.kaliumLogger
import com.wire.kalium.network.serialization.JoseJson
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.handleUnsuccessfulResponse
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.request.*
import io.ktor.http.*

internal open class E2EIApiV4 internal constructor(private val authenticatedNetworkClient: AuthenticatedNetworkClient) :
    E2EIApi {

    private val httpClient get() = authenticatedNetworkClient.httpClient

    override suspend fun getDirectories(): NetworkResponse<AcmeDirectoriesResponse> =
        wrapKaliumResponse {
            httpClient.get("$TEMP_BASE_URL/$PATH_ACME_DIRECTORIES")
        }

    override suspend fun getNewNonce(noncePath: String): NetworkResponse<String> =
        runCatching {
            httpClient.prepareHead(noncePath).execute { httpResponse ->
                if (httpResponse.status.isSuccess() && httpResponse.headers["Replay-Nonce"] != null) {
                    NetworkResponse.Success(httpResponse.headers["Replay-Nonce"].toString(), httpResponse)
                } else {
                    handleUnsuccessfulResponse(httpResponse).also {
                        if (it.kException is KaliumException.InvalidRequestError &&
                            it.kException.errorResponse.code == HttpStatusCode.Unauthorized.value
                        ) {
                            kaliumLogger.d("Nonce error")
                        }
                    }
                }
            }
        }.getOrElse { unhandledException ->
            // since we are handling manually our network exceptions for this endpoint, handle ie: no host exception
            NetworkResponse.Error(KaliumException.GenericError(unhandledException))
        }

    override suspend fun sendNewAccount(
        newAccountRequestUrl: String,
        newAccountRequestBody: List<UByte>
    ): NetworkResponse<String> = wrapKaliumResponse {
        httpClient.post(newAccountRequestUrl) {
            contentType(ContentType.Application.JoseJson)
            setBody(ByteArray(newAccountRequestBody.size) { newAccountRequestBody[it].toByte() })
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
        const val TEMP_BASE_URL = "https://balderdash.hogwash.work:9000/acme"
        const val PATH_ACME_DIRECTORIES = "acme/directory"
    }
}
