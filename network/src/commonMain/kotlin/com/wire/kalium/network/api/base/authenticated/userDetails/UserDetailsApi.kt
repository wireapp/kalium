package com.wire.kalium.network.api.base.authenticated.userDetails

import com.wire.kalium.network.api.base.model.UserId
import com.wire.kalium.network.utils.NetworkResponse

interface UserDetailsApi {
    suspend fun getMultipleUsers(users: ListUserRequest): NetworkResponse<List<UserProfileDTO>>
    suspend fun getUserInfo(userId: UserId): NetworkResponse<UserProfileDTO>
}
