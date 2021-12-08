package com.wire.kalium.logic

import com.wire.kalium.network.LoginNetworkContainer
import com.wire.kalium.network.api.user.login.LoginWithEmailRequest

class Authentication(private val loginNetworkContainer: LoginNetworkContainer) {

    suspend fun loginUsingEmail(email: String, password: String): AuthenticationResult {
        loginNetworkContainer.loginApi.emailLogin(
            LoginWithEmailRequest(email, password, "Hey it's me Destin")
        ).resultBody
    }

    sealed class AuthenticationResult {
        class Success(val userSession: UserSession) : AuthenticationResult()

        sealed class Failure : AuthenticationResult() {
            object InvalidCredentials : Failure()
            class Generic(val genericFailure: GenericFailure) : Failure()
        }
    }
}
