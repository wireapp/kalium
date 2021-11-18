package com.wire.kalium.api.auth

import com.wire.kalium.exceptions.HttpException
import javax.ws.rs.core.Cookie

/**
 *
 */
interface AuthApi {

    @Throws(HttpException::class)
    suspend fun renewAccessToken(cookie: Cookie): RenewAccessTokenResponse

    // TODO: move this to user api
    @Throws(HttpException::class)
    suspend fun removeCookies(removeCookiesRequest: RemoveCookiesRequest)

    companion object {
        protected const val PATH_ACCESS = "access"
    }
}
