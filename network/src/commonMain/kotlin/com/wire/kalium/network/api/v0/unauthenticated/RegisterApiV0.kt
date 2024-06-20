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

package com.wire.kalium.network.api.v0.unauthenticated

import com.wire.kalium.network.UnauthenticatedNetworkClient
import com.wire.kalium.network.api.base.model.AccessTokenDTO
import com.wire.kalium.network.api.base.model.RefreshTokenProperties
import com.wire.kalium.network.api.base.model.SelfUserDTO
import com.wire.kalium.network.api.base.model.SessionDTO
import com.wire.kalium.network.api.base.unauthenticated.register.ActivationParam
import com.wire.kalium.network.api.base.unauthenticated.register.RegisterApi
import com.wire.kalium.network.api.base.unauthenticated.register.RegisterParam
import com.wire.kalium.network.api.base.unauthenticated.register.RequestActivationCodeParam
import com.wire.kalium.network.utils.CustomErrors
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.flatMap
import com.wire.kalium.network.utils.mapSuccess
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpHeaders

internal open class RegisterApiV0 internal constructor(
    private val unauthenticatedNetworkClient: UnauthenticatedNetworkClient
) : RegisterApi {

    private val httpClient get() = unauthenticatedNetworkClient.httpClient

    private suspend fun getToken(refreshToken: String): NetworkResponse<AccessTokenDTO> = wrapKaliumResponse {
        httpClient.post(PATH_ACCESS) {
            header(HttpHeaders.Cookie, "${RefreshTokenProperties.COOKIE_NAME}=$refreshToken")
        }
    }

    override suspend fun register(
        param: RegisterParam
    ): NetworkResponse<Pair<SelfUserDTO, SessionDTO>> = wrapKaliumResponse<SelfUserDTO> {
        httpClient.post(REGISTER_PATH) {
            setBody(param.toBody())
        }
    }.flatMap { registerResponse ->
        registerResponse.cookies[RefreshTokenProperties.COOKIE_NAME]?.let { refreshToken ->
            getToken(refreshToken).mapSuccess { accessTokenDTO ->
                Pair(
                    registerResponse.value,
                    SessionDTO(
                        userId = registerResponse.value.id,
                        tokenType = accessTokenDTO.tokenType,
                        accessToken = accessTokenDTO.value,
                        refreshToken = refreshToken,
                        cookieLabel = param.cookieLabel
                    )
                )
            }
        } ?: run {
            CustomErrors.MISSING_REFRESH_TOKEN
        }
    }

    override suspend fun requestActivationCode(
        param: RequestActivationCodeParam
    ): NetworkResponse<Unit> = wrapKaliumResponse {
        httpClient.post("$ACTIVATE_PATH/$SEND_PATH") {
            setBody(param.toBody())
        }
    }

    override suspend fun activate(param: ActivationParam): NetworkResponse<Unit> = wrapKaliumResponse {
        httpClient.post(ACTIVATE_PATH) {
            setBody(param.toBody())
        }
    }

    private companion object {
        const val REGISTER_PATH = "register"
        const val ACTIVATE_PATH = "activate"
        const val SEND_PATH = "send"
        const val PATH_ACCESS = "access"
    }

}
