package com.wire.kalium.network.api.user.login

import com.wire.kalium.network.api.KaliumHttpResult
import com.wire.kalium.network.exceptions.HttpException


interface LoginApi {
    @Throws(HttpException::class)
    suspend fun emailLogin(
        loginWithEmailRequest: LoginWithEmailRequest,
        persist: Boolean
    ): KaliumHttpResult<LoginWithEmailResponse>

    companion object {
        const val PATH_LOGIN = "login"
        const val QUERY_PERSIST = "persist"
    }
}
