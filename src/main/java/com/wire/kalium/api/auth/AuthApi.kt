package com.wire.kalium.api.auth

import com.wire.kalium.exceptions.HttpException
import javax.ws.rs.core.Cookie

interface AuthApi {

    @Throws(HttpException::class)
    fun renewAccessToken(cookie: Cookie): RenewAccessTokenResponse

    @Throws(HttpException::class)
    fun removeCookies(removeCookiesRequest: RemoveCookiesRequest)

    companion object {
        protected const val PATH_ACCESS = "access"
    }
}
