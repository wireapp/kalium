package com.wire.kalium.network.api.base.authenticated.logout

import com.wire.kalium.network.utils.NetworkResponse

interface LogoutApi {
    suspend fun logout(): NetworkResponse<Unit>
    suspend fun removeCookiesByIds(removeCookiesByIdsRequest: RemoveCookiesByIdsRequest): NetworkResponse<Unit>
    suspend fun removeCookiesByLabels(removeCookiesByLabelsRequest: RemoveCookiesByLabels): NetworkResponse<Unit>
}

