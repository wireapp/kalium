package com.wire.kalium.logic.feature.auth

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.configuration.ServerConfig
import com.wire.kalium.logic.data.auth.login.LoginRepository
import com.wire.kalium.logic.data.user.SelfUser
import com.wire.kalium.logic.functional.suspending
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.exceptions.isInvalidCredentials

sealed class LoginResult {
    data class Success(val userSession: AuthSession, val userInfo: SelfUser) : LoginResult()

    sealed class Failure : LoginResult() {
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
    ): LoginResult
}

internal class LoginUseCaseImpl(
    private val loginRepository: LoginRepository,
    private val validateEmailUseCase: ValidateEmailUseCase,
    private val validateUserHandleUseCase: ValidateUserHandleUseCase

) : LoginUseCase {
    override suspend operator fun invoke(
        userIdentifier: String,
        password: String,
        shouldPersistClient: Boolean,
        serverConfig: ServerConfig
    ): LoginResult = suspending {
        // remove White Spaces around userIdentifier
        val cleanUserIdentifier = userIdentifier.trim()

        when {
            validateEmailUseCase(cleanUserIdentifier) -> {
                loginRepository.loginWithEmail(cleanUserIdentifier, password, shouldPersistClient, serverConfig)
            }
            validateUserHandleUseCase(cleanUserIdentifier) -> {
                loginRepository.loginWithHandle(cleanUserIdentifier, password, shouldPersistClient, serverConfig)
            }
            else -> return@suspending LoginResult.Failure.InvalidUserIdentifier
        }.coFold({
            when (it) {
                is NetworkFailure.ServerMiscommunication -> handleServerMiscommunication(it)
                is NetworkFailure.NoNetworkConnection -> LoginResult.Failure.Generic(it)
            }
        }, {
            LoginResult.Success(it.second, it.first)
        })
    }

    private fun handleServerMiscommunication(error: NetworkFailure.ServerMiscommunication): LoginResult.Failure {
        return if (error.kaliumException is KaliumException.InvalidRequestError && error.kaliumException.isInvalidCredentials()) {
            LoginResult.Failure.InvalidCredentials
        } else {
            LoginResult.Failure.Generic(error)
        }
    }
}
