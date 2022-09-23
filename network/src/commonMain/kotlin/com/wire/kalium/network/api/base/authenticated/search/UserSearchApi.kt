package com.wire.kalium.network.api.base.authenticated.search

import com.wire.kalium.network.utils.NetworkResponse

interface UserSearchApi {

    suspend fun search(
        userSearchRequest: UserSearchRequest
    ): NetworkResponse<UserSearchResponse>

}
