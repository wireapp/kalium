package com.wire.kalium.api.user.logout

import com.wire.kalium.api.NetworkResponse
import com.wire.kalium.exceptions.HttpException

interface LogoutApi {
    @Throws(HttpException::class)
    suspend fun logout(cookie: String): NetworkResponse<Unit>
}
