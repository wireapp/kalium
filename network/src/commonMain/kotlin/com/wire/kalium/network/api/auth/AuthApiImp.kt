package com.wire.kalium.network.api.auth

import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*

class AuthApiImp(private val httpClient: HttpClient) : AuthApi {
    override suspend fun renewAccessToken(cookie: Cookie): NetworkResponse<RenewAccessTokenResponse> = wrapKaliumResponse {
        httpClient.post(path = PATH_ACCESS) {
            this.cookie(cookie.name, cookie.value)
        }
    }

    override suspend fun removeCookiesByIds(removeCookiesByIdsRequest: RemoveCookiesByIdsRequest): NetworkResponse<Unit> =
        wrapKaliumResponse {
            httpClient.post(path = "$PATH_COOKIES$PATH_REMOVE") {
                body = removeCookiesByIdsRequest
            }
        }

    override suspend fun removeCookiesByLabels(removeCookiesWithIdsRequest: RemoveCookiesByLabels): NetworkResponse<Unit> =
        wrapKaliumResponse {
            httpClient.post(path = "$PATH_COOKIES$PATH_REMOVE") {
                body = removeCookiesWithIdsRequest
            }
        }

    private companion object {
        const val PATH_ACCESS = "access"
        const val PATH_COOKIES = "cookies"
        const val PATH_REMOVE = "/remove"
    }
}
