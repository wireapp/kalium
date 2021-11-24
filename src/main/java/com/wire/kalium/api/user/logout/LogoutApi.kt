package com.wire.kalium.api.user.logout

import com.wire.kalium.exceptions.HttpException
import javax.ws.rs.core.Cookie

interface LogoutApi {
    @Throws(HttpException::class)
    suspend fun logout(cookie: String)

    companion object {
        const val PATH_LOGOUT = "logout"
        const val QUERY_ACCESS_TOKEN = "access_token"
    }
}