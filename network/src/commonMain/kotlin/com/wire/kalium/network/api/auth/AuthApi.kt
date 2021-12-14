package com.wire.kalium.network.api.auth

import com.wire.kalium.network.utils.NetworkResponse
import io.ktor.http.Cookie

interface AuthApi {

    suspend fun renewAccessToken(cookie: Cookie): NetworkResponse<RenewAccessTokenResponse>

    // TODO: move this to user api
    suspend fun removeCookiesByIds(removeCookiesByIdsRequest: RemoveCookiesByIdsRequest): NetworkResponse<Unit>

    suspend fun removeCookiesByLabels(removeCookiesWithIdsRequest: RemoveCookiesByLabels): NetworkResponse<Unit>

}
