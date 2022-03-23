package com.wire.kalium.network.api.user.login

import com.wire.kalium.network.api.model.AuthenticationResult
import com.wire.kalium.network.utils.NetworkResponse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


interface LoginApi {

    sealed class LoginParam(open val password: String, open val label: String) {
        internal abstract fun toBody(): LoginRequest
        data class LoginWithEmail(
            val email: String,
            override val password: String,
            override val label: String
        ) : LoginParam(password, label) {
            override fun toBody(): LoginRequest = LoginRequest(email = email, password = password, label = label)
        }

        data class LoginWithHandel(
            val handle: String,
            override val password: String,
            override val label: String
        ) : LoginParam(password, label) {
            override fun toBody(): LoginRequest = LoginRequest(handle = handle, password = password, label = label)
        }
    }

    suspend fun login(
        param: LoginParam,
        persist: Boolean,
        apiBaseUrl: String
    ): NetworkResponse<AuthenticationResult>
}

@Serializable
internal data class LoginRequest(
    @SerialName("email") val email: String? = null,
    @SerialName("handle") val handle: String? = null,
    @SerialName("password") val password: String,
    @SerialName("label") val label: String
)
