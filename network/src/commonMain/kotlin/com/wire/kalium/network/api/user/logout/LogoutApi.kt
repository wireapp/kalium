package com.wire.kalium.network.api.user.logout

import com.wire.kalium.network.utils.NetworkResponse

interface LogoutApi {
    suspend fun logout(cookie: String): NetworkResponse<Unit>
}
