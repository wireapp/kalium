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
