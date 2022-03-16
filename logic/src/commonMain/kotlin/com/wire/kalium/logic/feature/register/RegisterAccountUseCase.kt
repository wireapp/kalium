package com.wire.kalium.logic.feature.register

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.configuration.ServerConfig
import com.wire.kalium.logic.data.register.RegisterAccountRepository
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.user.SelfUser
import com.wire.kalium.logic.feature.auth.AuthSession

sealed class RegisterParam(
    firstName: String,
    lastName: String,
    val email: String,
    val password: String,
) {
    val name: String

    init {
        name = "$firstName $lastName"
    }

    class PrivateAccount(
        firstName: String,
        LastName: String,
        email: String,
        password: String,
        val emailActivationCode: String
    ) : RegisterParam(firstName, LastName, email, password)
}

class RegisterAccountUseCase(
    private val registerAccountRepository: RegisterAccountRepository,
    private val sessionRepository: SessionRepository
) {
    suspend operator fun invoke(param: RegisterParam, serverConfig: ServerConfig): RegisterResult {
        return when (param) {
            is RegisterParam.PrivateAccount -> {
                with(param) {
                    registerAccountRepository.registerWithEmail(email, emailActivationCode, name, password, serverConfig)
                }
            }
        }.fold(
            {
                RegisterResult.Failure.Generic(it)
            }, {
                sessionRepository.storeSession(it.second)
                sessionRepository.updateCurrentSession(it.second.userId)
                RegisterResult.Success(it)
            })
    }
}

sealed class RegisterResult {
    class Success(val value: Pair<SelfUser, AuthSession>) : RegisterResult()
    sealed class Failure : RegisterResult() {
        class Generic(val failure: NetworkFailure) : RegisterResult()
    }
}
