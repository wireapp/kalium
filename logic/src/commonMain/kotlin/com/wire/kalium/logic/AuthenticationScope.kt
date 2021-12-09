package com.wire.kalium.logic

import com.wire.kalium.network.LoginNetworkContainer
import com.wire.kalium.network.api.user.login.LoginWithEmailRequest

class AuthenticationScope(private val loginNetworkContainer: LoginNetworkContainer) {

    suspend fun loginUsingEmail(email: String, password: String): AuthenticationResult {
        val response = loginNetworkContainer.loginApi.emailLogin(
            LoginWithEmailRequest(email, password, "Hey it's me Destin"), false
        )

        return when {
            response.isSuccessful -> {
                val resultBody = response.resultBody
                val refreshToken = response.headers["Cookie"]?.firstOrNull() ?: "TODO"
                val session = AuthSession(resultBody.accessToken, refreshToken, resultBody.tokenType)
                AuthenticationResult.Success(session)
            }
            response.httpStatusCode in (401..403) -> AuthenticationResult.Failure.InvalidCredentials
            else -> AuthenticationResult.Failure.Generic(GenericFailure.ServerMiscommunication)
        }
    }

    sealed class AuthenticationResult {
        class Success(val userSession: AuthSession) : AuthenticationResult()

        sealed class Failure : AuthenticationResult() {
            object InvalidCredentials : Failure()
            class Generic(val genericFailure: GenericFailure) : Failure()
        }
    }
}
