package com.wire.kalium.api.user.login

import com.wire.kalium.exceptions.HttpException

interface LoginApi {
    @Throws(HttpException::class)
    suspend fun emailLogin(
            loginWithEmailRequest: LoginWithEmailRequest,
            persist: Boolean
    ): LoginWithEmailResponse

    companion object {
        const val PATH_LOGIN = "login"
        const val QUERY_PERSIST = "persist"
    }
}
