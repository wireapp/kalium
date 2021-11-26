package com.wire.kalium.api.auth

import com.wire.kalium.api.KaliumHttpResult
import com.wire.kalium.api.NetworkResponse
import com.wire.kalium.api.wrapKaliumResponse
import com.wire.kalium.exceptions.AuthException
import io.ktor.client.HttpClient
import io.ktor.client.call.receive
import io.ktor.client.request.cookie
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.request
import io.ktor.http.Cookie

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

    override suspend fun RemoveCookiesByLabels(removeCookiesWithIdsRequest: RemoveCookiesByLabels): NetworkResponse<Unit> =
        wrapKaliumResponse {
            httpClient.post(path = "$PATH_COOKIES$PATH_REMOVE") {
                body = removeCookiesWithIdsRequest
            }
        }

    companion object {
        private const val PATH_ACCESS = "access"
        private const val PATH_COOKIES = "cookies"
        private const val PATH_REMOVE = "/remove"
    }
}
