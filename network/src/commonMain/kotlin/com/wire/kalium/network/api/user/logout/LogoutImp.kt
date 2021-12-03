package com.wire.kalium.network.api.user.logout

import com.wire.kalium.network.api.KaliumHttpResult
import com.wire.kalium.network.api.user.login.LoginWithEmailResponse
import com.wire.kalium.network.api.wrapKaliumResponse
import io.ktor.client.*
import io.ktor.client.request.*

class LogoutImp(private val httpClient: HttpClient) : LogoutApi {
    override suspend fun logout(cookie: String): KaliumHttpResult<LoginWithEmailResponse> = wrapKaliumResponse {
        httpClient.post(path = PATH_LOGOUT) {
            header(QUERY_COOKIE, cookie)
        }
    }

    private companion object {
        const val PATH_LOGOUT = "logout"
        const val QUERY_COOKIE = "cookie"
    }
}
