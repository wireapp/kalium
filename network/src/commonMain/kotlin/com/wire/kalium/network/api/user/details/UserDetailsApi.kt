package com.wire.kalium.network.api.user.details

import com.wire.kalium.network.api.UserId
import com.wire.kalium.network.utils.NetworkResponse

interface UserDetailsApi {
    suspend fun getMultipleUsers(users: ListUserRequest): NetworkResponse<List<UserProfileDTO>>
    suspend fun getUserInfo(userId: UserId): NetworkResponse<UserProfileDTO>
}
