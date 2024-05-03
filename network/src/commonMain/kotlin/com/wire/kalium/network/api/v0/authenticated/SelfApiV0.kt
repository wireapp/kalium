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

package com.wire.kalium.network.api.v0.authenticated

import com.wire.kalium.network.AuthenticatedNetworkClient
import com.wire.kalium.network.api.base.authenticated.self.ChangeHandleRequest
import com.wire.kalium.network.api.base.authenticated.self.SelfApi
import com.wire.kalium.network.api.base.authenticated.self.UserUpdateRequest
import com.wire.kalium.network.api.base.model.DeleteAccountRequest
import com.wire.kalium.network.api.base.model.RefreshTokenProperties
import com.wire.kalium.network.api.base.model.SelfUserDTO
import com.wire.kalium.network.api.base.model.SupportedProtocolDTO
import com.wire.kalium.network.api.base.model.UpdateEmailRequest
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.session.SessionManager
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.flatMap
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode

internal open class SelfApiV0 internal constructor(
    private val authenticatedNetworkClient: AuthenticatedNetworkClient,
    private val sessionManager: SessionManager
) : SelfApi {

    internal val httpClient get() = authenticatedNetworkClient.httpClient

    override suspend fun getSelfInfo(): NetworkResponse<SelfUserDTO> = wrapKaliumResponse {
        httpClient.get(PATH_SELF)
    }

    override suspend fun updateSelf(userUpdateRequest: UserUpdateRequest): NetworkResponse<Unit> = wrapKaliumResponse {
        httpClient.put(PATH_SELF) {
            setBody(userUpdateRequest)
        }
    }

    override suspend fun changeHandle(request: ChangeHandleRequest): NetworkResponse<Unit> = wrapKaliumResponse {
        httpClient.put("$PATH_SELF/$PATH_HANDLE") {
            setBody(request)
        }
    }

    override suspend fun updateEmailAddress(email: String): NetworkResponse<Boolean> =
        sessionManager.session()?.refreshToken?.let { cookie ->
            wrapKaliumResponse<Unit> {
                httpClient.put("$PATH_ACCESS/$PATH_SELF/$PATH_EMAIL") {
                    header(HttpHeaders.Cookie, "${RefreshTokenProperties.COOKIE_NAME}=$cookie")
                    setBody(UpdateEmailRequest(email))
                }
            }.flatMap { successResponse ->
                with(successResponse) {
                    when (httpCode) {
                        HttpStatusCode.NoContent.value -> NetworkResponse.Success(false, headers, httpCode)
                        else -> NetworkResponse.Success(true, headers, httpCode)
                    }
                }
            }
        } ?: NetworkResponse.Error(KaliumException.GenericError(IllegalStateException("No session found")))

    override suspend fun deleteAccount(password: String?): NetworkResponse<Unit> = wrapKaliumResponse {
        httpClient.delete(PATH_SELF) {
            setBody(DeleteAccountRequest(password))
        }
    }

    override suspend fun updateSupportedProtocols(protocols: List<SupportedProtocolDTO>): NetworkResponse<Unit> =
        getApiNotSupportedError(::updateSupportedProtocols.name, MIN_API_VERSION_SUPPORTED_PROTOCOLS)

    companion object {
        const val PATH_SELF = "self"
        const val PATH_HANDLE = "handle"
        const val PATH_ACCESS = "access"
        const val PATH_EMAIL = "email"

        const val MIN_API_VERSION_SUPPORTED_PROTOCOLS = 4
    }
}
