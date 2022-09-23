package com.wire.kalium.network.api.base.unauthenticated

import com.wire.kalium.network.api.base.model.SessionDTO
import com.wire.kalium.network.api.base.model.UserDTO
import com.wire.kalium.network.utils.NetworkResponse

interface LoginApi {
    sealed class LoginParam(
        open val password: String,
        open val label: String
    ) {
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
        persist: Boolean
    ): NetworkResponse<Pair<SessionDTO, UserDTO>>
}
