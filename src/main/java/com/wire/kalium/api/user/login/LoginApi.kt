package com.wire.kalium.api.user.login

import com.wire.kalium.api.NetworkResponse
import com.wire.kalium.exceptions.HttpException

interface LoginApi {
    @Throws(HttpException::class)
    suspend fun emailLogin(
        loginWithEmailRequest: LoginWithEmailRequest,
        persist: Boolean
    ): LoginWithEmailResponse
}
