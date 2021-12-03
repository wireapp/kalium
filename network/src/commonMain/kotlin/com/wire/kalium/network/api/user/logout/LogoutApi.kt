package com.wire.kalium.network.api.user.logout

import com.wire.kalium.network.api.KaliumHttpResult
import com.wire.kalium.network.api.user.login.LoginWithEmailResponse

interface LogoutApi {
    suspend fun logout(cookie: String): KaliumHttpResult<LoginWithEmailResponse>
}
