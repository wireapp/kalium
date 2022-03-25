package com.wire.kalium.network.api.contact.search

import com.wire.kalium.network.utils.NetworkResponse

interface UserSearchApi {

    suspend fun search(
        userSearchRequest: UserSearchRequest
    ): NetworkResponse<UserSearchResponse>

}
