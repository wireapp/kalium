package com.wire.kalium.api.user.logout

import com.wire.kalium.api.NetworkResponse
import com.wire.kalium.api.wrapKaliumResponse
import io.ktor.client.*
import io.ktor.client.request.*
import javax.ws.rs.core.HttpHeaders

class LogoutApiImp(private val client: HttpClient) : LogoutApi {

    private companion object {
        const val PATH_LOGOUT = "access/logout"
        const val COOKIE = "cookie"
    }

    override suspend fun logout(cookie: String): NetworkResponse<Unit> = wrapKaliumResponse {
        client.post(path = PATH_LOGOUT) {
            header(COOKIE, cookie)
        }
    }
}
