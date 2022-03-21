package com.wire.kalium.network.api.auth

import com.wire.kalium.network.api.RefreshTokenProperties
import com.wire.kalium.network.api.model.AccessTokenDTO
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.http.HttpHeaders

interface AccessTokenApi {
    suspend fun getToken(refreshToken: String): NetworkResponse<AccessTokenDTO>
}

internal class AccessTokenApiImpl(private val httpClient: HttpClient) : AccessTokenApi {
    override suspend fun getToken(refreshToken: String): NetworkResponse<AccessTokenDTO> = wrapKaliumResponse {
        httpClient.post(PATH_ACCESS) {
            header(HttpHeaders.Cookie, "${RefreshTokenProperties.COOKIE_NAME}=$refreshToken")
        }
    }

    private companion object {
        const val PATH_ACCESS = "access"
    }
}
