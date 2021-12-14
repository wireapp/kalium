package com.wire.kalium.network.api.user.login

import com.wire.kalium.network.utils.NetworkResponse

interface LoginApi {
    suspend fun emailLogin(loginWithEmailRequest: LoginWithEmailRequest, persist: Boolean): NetworkResponse<LoginWithEmailResponse>
}
