package com.wire.kalium.network.api.auth

import com.wire.kalium.network.api.KaliumHttpResult
import com.wire.kalium.network.exceptions.HttpException
import io.ktor.http.Cookie

/**
 *
 */
interface AuthApi {

    suspend fun renewAccessToken(cookie: Cookie): KaliumHttpResult<RenewAccessTokenResponse>

    // TODO: move this to user api
    suspend fun removeCookiesByIds(removeCookiesByIdsRequest: RemoveCookiesByIdsRequest): KaliumHttpResult<Unit>

    suspend fun RemoveCookiesByLabels(removeCookiesWithIdsRequest: RemoveCookiesByLabels): KaliumHttpResult<Unit>

}
