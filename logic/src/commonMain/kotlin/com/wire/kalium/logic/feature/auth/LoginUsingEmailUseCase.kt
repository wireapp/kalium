package com.wire.kalium.logic.feature.auth

import com.wire.kalium.logic.GenericFailure
import com.wire.kalium.logic.data.login.LoginRepository
import com.wire.kalium.logic.data.session.SessionRepository

class LoginUsingEmailUseCase(
    private val loginRepository: LoginRepository,
    private val sessionRepository: SessionRepository
) {
    suspend operator fun invoke(email: String, password: String, shouldPersistClient: Boolean): Result {
        val result = loginRepository.loginWithEmail(email, password, shouldPersistClient)
        if (result is Result.Success) {
            sessionRepository.storeSession(result.userSession)
        }
        return result
    }

    sealed class Result {
        class Success(val userSession: AuthSession) : Result()

        sealed class Failure : Result() {
            object InvalidCredentials : Failure()
            class Generic(val genericFailure: GenericFailure) : Failure()
        }
    }
}
