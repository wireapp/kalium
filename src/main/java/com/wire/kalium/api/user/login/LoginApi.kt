package com.wire.kalium.api.user.login

import com.wire.kalium.api.KaliumHttpResult
import com.wire.kalium.exceptions.HttpException
import com.wire.kalium.tools.HostProvider


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
