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

import com.wire.kalium.network.api.base.authenticated.AccessTokenApi
import com.wire.kalium.network.api.base.model.AccessTokenDTO
import com.wire.kalium.network.api.base.model.RefreshTokenDTO
import com.wire.kalium.network.api.base.model.RefreshTokenProperties
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.flatMap
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.http.HttpHeaders

internal open class AccessTokenApiV0(private val httpClient: HttpClient) : AccessTokenApi {
    override suspend fun getToken(refreshToken: String, clientId: String?): NetworkResponse<Pair<AccessTokenDTO, RefreshTokenDTO?>> =
        wrapKaliumResponse<AccessTokenDTO> {
            httpClient.post(PATH_ACCESS) {
                header(HttpHeaders.Cookie, "${RefreshTokenProperties.COOKIE_NAME}=$refreshToken")
            }
        }.flatMap { accessTokenResponse ->
            accessTokenResponse.cookies[RefreshTokenProperties.COOKIE_NAME].let { newRefreshToken ->
                newRefreshToken?.let {
                    NetworkResponse.Success(
                        Pair(accessTokenResponse.value, RefreshTokenDTO(newRefreshToken)),
                        accessTokenResponse.headers,
                        accessTokenResponse.httpCode
                    )
                } ?: run {
                    NetworkResponse.Success(
                        Pair(accessTokenResponse.value, null), accessTokenResponse.headers, accessTokenResponse.httpCode
                    )
                }
            }
        }

    private companion object {
        const val PATH_ACCESS = "access"
    }
}
