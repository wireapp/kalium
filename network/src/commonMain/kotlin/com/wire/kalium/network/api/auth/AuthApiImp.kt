package com.wire.kalium.network.api.auth

import com.wire.kalium.network.api.KaliumHttpResult
import com.wire.kalium.network.api.wrapKaliumResponse
import com.wire.kalium.network.exceptions.AuthException
import io.ktor.client.HttpClient
import io.ktor.client.call.receive
import io.ktor.client.request.cookie
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.http.Cookie

class AuthApiImp(private val httpClient: HttpClient) : AuthApi {
    override suspend fun renewAccessToken(cookie: Cookie): KaliumHttpResult<RenewAccessTokenResponse> =
        wrapKaliumResponse<RenewAccessTokenResponse> {
            httpClient.post<HttpResponse>(path = PATH_ACCESS) {
                this.cookie(cookie.name, cookie.value)
            }.receive()
        }.also {
            if (it.httpStatusCode == 401 or 403 or 400) {
                throw AuthException(code = it.httpStatusCode)
            }
        }

    override suspend fun removeCookiesByIds(removeCookiesByIdsRequest: RemoveCookiesByIdsRequest): KaliumHttpResult<Unit> =
        wrapKaliumResponse<Unit> {
            httpClient.post<HttpResponse>(path = "$PATH_COOKIES$PATH_REMOVE") {
                body = removeCookiesByIdsRequest
            }.receive()
        }

    override suspend fun removeCookiesByLabels(removeCookiesWithIdsRequest: RemoveCookiesByLabels): KaliumHttpResult<Unit> =
        wrapKaliumResponse<Unit> {
            httpClient.post<HttpResponse>(path = "$PATH_COOKIES$PATH_REMOVE") {
                body = removeCookiesWithIdsRequest
            }.receive()
        }

    private companion object {
        const val PATH_ACCESS = "access"
        const val PATH_COOKIES = "cookies"
        const val PATH_REMOVE = "/remove"
    }
}
