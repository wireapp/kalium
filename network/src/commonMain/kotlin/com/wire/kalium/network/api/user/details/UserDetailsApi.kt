package com.wire.kalium.network.api.user.details

import com.wire.kalium.network.utils.NetworkResponse

interface UserDetailsApi {

    suspend fun getMultipleUsers(users: ListUserRequest): NetworkResponse<Unit>
}
