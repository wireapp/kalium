package com.wire.kalium.network.api.user.logout

interface LogoutApi {
    fun logout(cookie: Cookie)

    companion object {
        protected val PATH_LOGOUT = "logout"
    }
}
