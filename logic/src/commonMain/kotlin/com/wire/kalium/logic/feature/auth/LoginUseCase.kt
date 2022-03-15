package com.wire.kalium.logic.feature.auth

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.configuration.ServerConfig
import com.wire.kalium.logic.data.auth.login.LoginRepository
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.functional.suspending
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.exceptions.isInvalidCredentials

sealed class AuthenticationResult {
    data class Success(val userSession: AuthSession) : AuthenticationResult()

    sealed class Failure : AuthenticationResult() {
        object InvalidCredentials : Failure()
        object InvalidUserIdentifier : Failure()
        class Generic(val genericFailure: CoreFailure) : Failure()
    }
}

interface LoginUseCase {
    suspend operator fun invoke(
        userIdentifier: String,
        password: String,
        shouldPersistClient: Boolean,
        serverConfig: ServerConfig
    ): AuthenticationResult
}

class LoginUseCaseImpl(
    private val loginRepository: LoginRepository,
    private val sessionRepository: SessionRepository,
    private val validateEmailUseCase: ValidateEmailUseCase,
    private val validateUserHandleUseCase: ValidateUserHandleUseCase
) : LoginUseCase {
    override suspend operator fun invoke(
        userIdentifier: String,
        password: String,
        shouldPersistClient: Boolean,
        serverConfig: ServerConfig
    ): AuthenticationResult = suspending {
        // remove White Spaces around userIdentifier
        val cleanUserIdentifier = userIdentifier.trim()

        when {
            validateEmailUseCase(cleanUserIdentifier) -> {
                loginRepository.loginWithEmail(cleanUserIdentifier, password, shouldPersistClient, serverConfig)
            }
            validateUserHandleUseCase(cleanUserIdentifier) -> {
                loginRepository.loginWithHandle(cleanUserIdentifier, password, shouldPersistClient, serverConfig)
            }
            else -> return@suspending AuthenticationResult.Failure.InvalidUserIdentifier
        }.coFold({
            when (it.kaliumException) {
                is KaliumException.InvalidRequestError -> {
                    if (it.kaliumException.isInvalidCredentials()) {
                        AuthenticationResult.Failure.InvalidCredentials
                    } else {
                        AuthenticationResult.Failure.Generic(it)
                    }
                }
                else -> AuthenticationResult.Failure.Generic(it)
            }
        }, {
            sessionRepository.storeSession(it)
            sessionRepository.updateCurrentSession(it.userId)
            AuthenticationResult.Success(it)
        })
    }
}
