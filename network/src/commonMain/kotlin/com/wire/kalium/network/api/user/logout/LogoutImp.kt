package com.wire.kalium.network.api.user.logout

import com.wire.kalium.network.api.KaliumHttpResult
import com.wire.kalium.network.api.user.login.LoginWithEmailResponse
import com.wire.kalium.network.api.wrapKaliumResponse
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.http.HttpHeaders

class LogoutImp(private val httpClient: HttpClient) : LogoutApi {
    override suspend fun logout(cookie: String): KaliumHttpResult<LoginWithEmailResponse> = wrapKaliumResponse {
        httpClient.post(path = "$PATH_ACCESS$PATH_LOGOUT") {
            header(HttpHeaders.Cookie, cookie)
        }
    }

    private companion object {
        const val PATH_ACCESS = "/access"
        const val PATH_LOGOUT = "/logout"
    }
}
