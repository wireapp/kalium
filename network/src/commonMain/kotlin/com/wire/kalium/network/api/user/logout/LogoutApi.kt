package com.wire.kalium.network.api.user.logout

import com.wire.kalium.network.exceptions.HttpException

interface LogoutApi {
    @Throws(HttpException::class)
    fun logout(cookie: Cookie)

    companion object {
        protected val PATH_LOGOUT = "logout"
    }
}
