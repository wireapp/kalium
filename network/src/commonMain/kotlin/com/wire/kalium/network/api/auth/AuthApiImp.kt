package com.wire.kalium.network.api.auth

import com.wire.kalium.network.api.RefreshTokenProperties
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*

class AuthApiImp(private val httpClient: HttpClient) : AuthApi {
    override suspend fun renewAccessToken(refreshToken: String): NetworkResponse<RenewAccessTokenResponse> = wrapKaliumResponse {
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
