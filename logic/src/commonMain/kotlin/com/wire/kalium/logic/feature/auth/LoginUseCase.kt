package com.wire.kalium.logic.feature.auth

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.configuration.ServerConfig
import com.wire.kalium.logic.data.auth.login.LoginRepository
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.failure.AuthenticationFailure
import com.wire.kalium.logic.functional.Either

sealed class AuthenticationResult {
    data class Success(val userSession: AuthSession) : AuthenticationResult()

    sealed class Failure : AuthenticationResult() {
        object InvalidCredentials : Failure()
        object InvalidUserIdentifier : Failure()
        class Generic(val genericFailure: CoreFailure) : Failure()
    }
}

class LoginUseCase(
    private val loginRepository: LoginRepository,
    private val sessionRepository: SessionRepository,
    private val validateEmailUseCase: ValidateEmailUseCase,
    private val validateUserHandleUseCase: ValidateUserHandleUseCase
) {
    suspend operator fun invoke(userIdentifier: String, password: String, shouldPersistClient: Boolean, serverConfig: ServerConfig): AuthenticationResult {
        val result = when {
            validateEmailUseCase(userIdentifier) -> {
                loginRepository.loginWithEmail(userIdentifier, password, shouldPersistClient, serverConfig)
            }
            validateUserHandleUseCase(userIdentifier) -> {
                loginRepository.loginWithHandle(userIdentifier, password, shouldPersistClient, serverConfig)
            }
            else -> return AuthenticationResult.Failure.InvalidUserIdentifier
        }

        return when (result) {
            is Either.Right -> {
//                sessionRepository.storeSession(result.value)
                AuthenticationResult.Success(result.value)
            }
            is Either.Left -> {
                if (result.value is AuthenticationFailure) {
                    AuthenticationResult.Failure.InvalidCredentials
                } else {
                    AuthenticationResult.Failure.Generic(result.value)
                }
            }
        }
    }
}
