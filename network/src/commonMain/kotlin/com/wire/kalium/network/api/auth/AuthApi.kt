package com.wire.kalium.network.api.auth

import com.wire.kalium.network.api.KaliumHttpResult

/**
 *
 */
interface AuthApi {

    suspend fun renewAccessToken(cookieName: String, cookieValue: String): KaliumHttpResult<RenewAccessTokenResponse>

    // TODO: move this to user api
    suspend fun removeCookiesByIds(removeCookiesByIdsRequest: RemoveCookiesByIdsRequest): KaliumHttpResult<Unit>

    suspend fun removeCookiesByLabels(removeCookiesWithIdsRequest: RemoveCookiesByLabels): KaliumHttpResult<Unit>

}
