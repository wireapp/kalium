package com.wire.kalium.network.api.user.login

import com.wire.kalium.network.api.KaliumHttpResult


interface LoginApi {
    suspend fun emailLogin(loginWithEmailRequest: LoginWithEmailRequest, persist: Boolean): KaliumHttpResult<LoginWithEmailResponse>
}
