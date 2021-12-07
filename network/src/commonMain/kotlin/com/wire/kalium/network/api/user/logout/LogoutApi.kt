package com.wire.kalium.network.api.user.logout

import com.wire.kalium.network.api.KaliumHttpResult

interface LogoutApi {
    suspend fun logout(cookie: String): KaliumHttpResult<Unit>
}
