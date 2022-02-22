package com.wire.kalium.network.api.user.login

import com.wire.kalium.network.api.SessionDTO
import com.wire.kalium.network.utils.NetworkResponse

interface LoginApi {

    sealed class LoginParam(open val password: String, open val label: String) {
        data class LoginWithEmail(
            val email: String,
            override val password: String,
            override val label: String
        ) : LoginParam(password, label)

        data class LoginWithHandel(
            val handle: String,
            override val password: String,
            override val label: String
        ) : LoginParam(password, label)
    }

    suspend fun login(
        param: LoginParam,
        persist: Boolean,
        apiBaseUrl: String
    ): NetworkResponse<SessionDTO>
}
