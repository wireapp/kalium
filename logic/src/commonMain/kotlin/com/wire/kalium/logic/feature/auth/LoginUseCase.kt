package com.wire.kalium.logic.feature.auth

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.auth.login.LoginRepository
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.exceptions.isInvalidCredentials

sealed class AuthenticationResult {
    data class Success(val userSession: AuthSession.Tokens) : AuthenticationResult()

    sealed class Failure : AuthenticationResult() {
        object InvalidCredentials : Failure()
        object InvalidUserIdentifier : Failure()
        class Generic(val genericFailure: CoreFailure) : Failure()
    }
}

interface LoginUseCase {
    suspend operator fun invoke(
        userIdentifier: String, password: String, shouldPersistClient: Boolean
    ): AuthenticationResult
}

internal class LoginUseCaseImpl(
    private val loginRepository: LoginRepository,
    private val validateEmailUseCase: ValidateEmailUseCase,
    private val validateUserHandleUseCase: ValidateUserHandleUseCase,
    private val serverLinks: ServerConfig.Links
) : LoginUseCase {
    override suspend operator fun invoke(
        userIdentifier: String, password: String, shouldPersistClient: Boolean
    ): AuthenticationResult {
        // remove White Spaces around userIdentifier
        val cleanUserIdentifier = userIdentifier.trim()

        return when {
            validateEmailUseCase(cleanUserIdentifier) -> {
                loginRepository.loginWithEmail(cleanUserIdentifier, password, shouldPersistClient)
            }
            validateUserHandleUseCase(cleanUserIdentifier).isValid -> {
                loginRepository.loginWithHandle(cleanUserIdentifier, password, shouldPersistClient)
            }
            else -> return AuthenticationResult.Failure.InvalidUserIdentifier
        }.fold({
            when (it) {
                is NetworkFailure.ServerMiscommunication -> handleServerMiscommunication(it)
                is NetworkFailure.NoNetworkConnection -> AuthenticationResult.Failure.Generic(it)
            }
        }, {
            AuthenticationResult.Success(AuthSession(it, serverLinks))
        })
    }

    private fun handleServerMiscommunication(error: NetworkFailure.ServerMiscommunication): AuthenticationResult.Failure {
        return if (error.kaliumException is KaliumException.InvalidRequestError && error.kaliumException.isInvalidCredentials()) {
            AuthenticationResult.Failure.InvalidCredentials
        } else {
            AuthenticationResult.Failure.Generic(error)
        }
    }
}
