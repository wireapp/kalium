package com.wire.kalium.network.api.auth

import com.wire.kalium.network.api.RefreshTokenProperties
import com.wire.kalium.network.api.model.AccessTokenDTO
import com.wire.kalium.network.api.model.RefreshTokenDTO
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.flatMap
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.http.HttpHeaders

interface AccessTokenApi {
    suspend fun getToken(refreshToken: String): NetworkResponse<Pair<AccessTokenDTO, RefreshTokenDTO?>>
}

internal class AccessTokenApiImpl(private val httpClient: HttpClient) : AccessTokenApi {
    override suspend fun getToken(refreshToken: String): NetworkResponse<Pair<AccessTokenDTO, RefreshTokenDTO?>> =
        wrapKaliumResponse<AccessTokenDTO> {
            httpClient.post(PATH_ACCESS) {
                header(HttpHeaders.Cookie, "${RefreshTokenProperties.COOKIE_NAME}=$refreshToken")
            }
        }.flatMap { response ->
            response.cookies[RefreshTokenProperties.COOKIE_NAME].let { newRefreshToken ->
                newRefreshToken?.let {
                    NetworkResponse.Success(
                        Pair(response.value, RefreshTokenDTO(newRefreshToken)), response.headers, response.httpCode
                    )
                } ?: run {
                    NetworkResponse.Success(
                        Pair(response.value, null), response.headers, response.httpCode
                    )
                }
            }
        }

    private companion object {
        const val PATH_ACCESS = "access"
    }
}
