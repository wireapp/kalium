package com.wire.kalium.logic.feature.auth

import com.wire.kalium.logic.data.login.LoginRepository
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.failure.AuthenticationFailure
import com.wire.kalium.logic.functional.Either

class LoginUseCase(
    private val loginRepository: LoginRepository,
    private val sessionRepository: SessionRepository
) {
    suspend operator fun invoke(email: String, password: String, shouldPersistClient: Boolean): AuthenticationResult {
        return when (val result = loginRepository.loginWithEmail(email, password, shouldPersistClient)) {
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
