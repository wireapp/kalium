package com.wire.kalium.network.api.user.logout

import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post

class LogoutImp(private val httpClient: HttpClient) : LogoutApi {

    override suspend fun logout(cookie: String): NetworkResponse<Unit> = wrapKaliumResponse {
        httpClient.post("$PATH_ACCESS$PATH_LOGOUT") {
            header(HEADER_KEY_COOKIE, cookie)
        }
    }

    private companion object {
        const val PATH_ACCESS = "/access"
        const val PATH_LOGOUT = "/logout"
        const val HEADER_KEY_COOKIE = "cookie"
    }
}
