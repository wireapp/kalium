package com.wire.kalium.logic.feature.auth

import com.wire.kalium.logic.data.login.LoginRepository
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.failure.AuthenticationFailure
import com.wire.kalium.logic.functional.isRight

class LoginUsingEmailUseCase(
    private val loginRepository: LoginRepository,
    private val sessionRepository: SessionRepository
) {
    suspend operator fun invoke(email: String, password: String, shouldPersistClient: Boolean): AuthenticationResult {
        val result = loginRepository.loginWithEmail(email, password, shouldPersistClient)
        return if (result.isRight()) {
            sessionRepository.storeSession(result.value)
            AuthenticationResult.Success(result.value)
        } else {
            if (result.value is AuthenticationFailure) {
                AuthenticationResult.Failure.InvalidCredentials
            } else {
                AuthenticationResult.Failure.Generic(result.value)
            }
        }
    }

}
