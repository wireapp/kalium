package com.wire.kalium.network.api.auth

import com.wire.kalium.network.utils.NetworkResponse

interface AuthApi {

    suspend fun renewAccessToken(refreshToken: String): NetworkResponse<RenewAccessTokenResponse>

    // TODO: move this to user api
    suspend fun removeCookiesByIds(removeCookiesByIdsRequest: RemoveCookiesByIdsRequest): NetworkResponse<Unit>

    suspend fun removeCookiesByLabels(removeCookiesWithIdsRequest: RemoveCookiesByLabels): NetworkResponse<Unit>

}
