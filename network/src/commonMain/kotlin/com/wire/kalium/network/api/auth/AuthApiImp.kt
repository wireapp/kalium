package com.wire.kalium.network.api.auth

import com.wire.kalium.network.api.RefreshTokenProperties
import com.wire.kalium.network.api.model.AccessTokenDTO
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.HttpClient
import io.ktor.client.request.cookie
import io.ktor.client.request.post
import io.ktor.client.request.setBody

class AuthApiImp(private val httpClient: HttpClient) : AuthApi {
    override suspend fun renewAccessToken(refreshToken: String): NetworkResponse<AccessTokenDTO> = wrapKaliumResponse {
        httpClient.post(PATH_ACCESS) {
            this.cookie(RefreshTokenProperties.COOKIE_NAME, refreshToken)
        }
    }

    override suspend fun removeCookiesByIds(removeCookiesByIdsRequest: RemoveCookiesByIdsRequest): NetworkResponse<Unit> =
        wrapKaliumResponse {
            httpClient.post("$PATH_COOKIES$PATH_REMOVE") {
                setBody(removeCookiesByIdsRequest)
            }
        }

    override suspend fun removeCookiesByLabels(removeCookiesWithIdsRequest: RemoveCookiesByLabels): NetworkResponse<Unit> =
        wrapKaliumResponse {
            httpClient.post("$PATH_COOKIES$PATH_REMOVE") {
                setBody(removeCookiesWithIdsRequest)
            }
        }

    private companion object {
        const val PATH_ACCESS = "access"
        const val PATH_COOKIES = "cookies"
        const val PATH_REMOVE = "/remove"
    }
}
