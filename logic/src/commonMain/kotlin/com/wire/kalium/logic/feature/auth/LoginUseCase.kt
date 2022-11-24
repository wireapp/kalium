package com.wire.kalium.logic.feature.auth

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.data.auth.login.LoginRepository
import com.wire.kalium.logic.data.auth.login.ProxyCredentials
import com.wire.kalium.logic.data.user.SsoId
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.map
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.exceptions.isBadRequest
import com.wire.kalium.network.exceptions.isInvalidCredentials

sealed class AuthenticationResult {
    data class Success(
        val authData: AuthTokens,
        val ssoID: SsoId?,
        val serverConfigId: String,
        val proxyCredentials: ProxyCredentials?
    ) : AuthenticationResult()

    sealed class Failure : AuthenticationResult() {
        object SocketError : Failure()
        object InvalidCredentials : Failure()
        object InvalidUserIdentifier : Failure()
        class Generic(val genericFailure: CoreFailure) : Failure()
    }
}

interface LoginUseCase {
    /**
     * Login with user credentials and return the session
     * Be noticed that session won't be stored locally, to store it
     * @see AddAuthenticatedUserUseCase
     */
    suspend operator fun invoke(
        userIdentifier: String,
        password: String,
        shouldPersistClient: Boolean
    ): AuthenticationResult
}

internal class LoginUseCaseImpl internal constructor(
    private val loginRepository: LoginRepository,
    private val validateEmailUseCase: ValidateEmailUseCase,
    private val validateUserHandleUseCase: ValidateUserHandleUseCase,
    private val serverConfig: ServerConfig,
    private val proxyCredentials: ProxyCredentials?
) : LoginUseCase {
    override suspend operator fun invoke(
        userIdentifier: String,
        password: String,
        shouldPersistClient: Boolean
    ): AuthenticationResult {
        // remove White Spaces around userIdentifier
        val cleanUserIdentifier = userIdentifier.trim()

        return when {
            validateEmailUseCase(cleanUserIdentifier) -> {
                loginRepository.loginWithEmail(cleanUserIdentifier, password, shouldPersistClient)
            }

            validateUserHandleUseCase(cleanUserIdentifier).isValidAllowingDots -> {
                loginRepository.loginWithHandle(cleanUserIdentifier, password, shouldPersistClient)
            }

            else -> return AuthenticationResult.Failure.InvalidUserIdentifier
        }.map { (authTokens, ssoId) -> AuthenticationResult.Success(authTokens, ssoId, serverConfig.id, proxyCredentials) }
            .fold({
                when (it) {
                    is NetworkFailure.ProxyError -> AuthenticationResult.Failure.SocketError
                    is NetworkFailure.ServerMiscommunication -> handleServerMiscommunication(it)
                    is NetworkFailure.NoNetworkConnection -> AuthenticationResult.Failure.Generic(it)
                    else -> AuthenticationResult.Failure.Generic(it)
                }
            }, {
                it
            })
    }

    private fun handleServerMiscommunication(error: NetworkFailure.ServerMiscommunication): AuthenticationResult.Failure {
        return if (error.kaliumException is KaliumException.InvalidRequestError &&
            (error.kaliumException.isInvalidCredentials() || error.kaliumException.isBadRequest())
        ) {
            AuthenticationResult.Failure.InvalidCredentials
        } else {
            AuthenticationResult.Failure.Generic(error)
        }
    }
}
