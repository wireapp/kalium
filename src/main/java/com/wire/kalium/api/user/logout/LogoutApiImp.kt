package com.wire.kalium.api.user.logout

import io.ktor.client.*
import io.ktor.client.request.*
import javax.ws.rs.core.HttpHeaders

class LogoutApiImp(private val client: HttpClient) : LogoutApi {

    override suspend fun logout(cookie: String) {
        client.post<Unit>(path = LogoutApi.PATH_LOGOUT) {
            header(HttpHeaders.COOKIE, cookie)
        }
    }
}