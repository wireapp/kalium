package com.wire.kalium.api.auth

import com.wire.kalium.api.KaliumHttpResult
import com.wire.kalium.exceptions.HttpException
import io.ktor.http.Cookie

/**
 *
 */
interface AuthApi {

    @Throws(HttpException::class)
    suspend fun renewAccessToken(cookie: Cookie): KaliumHttpResult<RenewAccessTokenResponse>

    // TODO: move this to user api
    @Throws(HttpException::class)
    suspend fun removeCookiesByIds(removeCookiesByIdsRequest: RemoveCookiesByIdsRequest): KaliumHttpResult<Nothing>

    @Throws(HttpException::class)
    suspend fun RemoveCookiesByLabels(removeCookiesWithIdsRequest: RemoveCookiesByLabels): KaliumHttpResult<Nothing>

}
