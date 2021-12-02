package com.wire.kalium.api.user.logout

import com.wire.kalium.exceptions.HttpException
import javax.ws.rs.core.Cookie

interface LogoutApi {
    @Throws(HttpException::class)
    fun logout(cookie: Cookie)

    companion object {
        protected val PATH_LOGOUT = "logout"
    }
}
