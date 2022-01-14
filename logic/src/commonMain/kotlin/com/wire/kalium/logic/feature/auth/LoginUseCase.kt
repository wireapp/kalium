package com.wire.kalium.logic.feature.auth

import com.wire.kalium.logic.data.login.LoginRepository
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.failure.AuthenticationFailure
import com.wire.kalium.logic.functional.Either

class LoginUseCase(
    private val loginRepository: LoginRepository,
    private val sessionRepository: SessionRepository,
    private val validateEmailUseCase: ValidateEmailUseCase,
    private val validateUserHandleUseCase: ValidateUserHandleUseCase
) {
    suspend operator fun invoke(userIdentifier: String, password: String, shouldPersistClient: Boolean): AuthenticationResult {
        val result = when (validateEmailUseCase(userIdentifier)) {
            true -> loginRepository.loginWithEmail(userIdentifier, password, shouldPersistClient)
            false -> {
                when (validateUserHandleUseCase(userIdentifier)) {
                    true -> loginRepository.loginWithHandle(userIdentifier, password, shouldPersistClient)
                    // userIdentifier is neither email nor user handle
                    false -> return AuthenticationResult.Failure.InvalidUserIdentifier
                }
            }
        }

        return when (result) {
            is Either.Right -> {
                sessionRepository.storeSession(result.value)
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
