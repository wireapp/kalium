package com.wire.kalium.network.api.user.login

import com.wire.kalium.network.utils.NetworkResponse

interface LoginApi {

    sealed class LoginParam(val password: String, val label: String) {
        class LoginWithEmail(val email: String, password: String, label: String) : LoginParam(password, label)
        class LoginWithHandel(val handle: String, password: String, label: String) : LoginParam(password, label)
    }

    suspend fun login(param: LoginParam, persist: Boolean): NetworkResponse<LoginResponse>
}
