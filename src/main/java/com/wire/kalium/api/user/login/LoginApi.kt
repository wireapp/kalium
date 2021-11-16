package com.wire.kalium.api.user.login

import com.wire.kalium.exceptions.HttpException


interface LoginApi {
    @Throws(HttpException::class)
    fun emailLogin(loginWithEmailRequest: LoginWithEmailRequest): LoginWithEmailResponse

    companion object {
        protected const val PATH_LOGIN = "login"
        protected const val QUERY_PERSIST = "persist"
    }
}
