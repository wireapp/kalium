package com.wire.kalium.logic.feature.register

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.configuration.ServerConfig
import com.wire.kalium.logic.data.register.RegisterAccountRepository
import com.wire.kalium.logic.feature.auth.AuthSession
import com.wire.kalium.logic.feature.auth.AuthenticationResult
import com.wire.kalium.logic.feature.auth.LoginUseCase
import com.wire.kalium.logic.functional.suspending

sealed class RegistrationParam(
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
    ) : RegistrationParam(firstName, LastName, email, password)
}

class RegisterAccountUseCase(
    private val registerAccountRepository: RegisterAccountRepository,
    private val loginUseCase: LoginUseCase
) {
    suspend operator fun invoke(param: RegistrationParam, serverConfig: ServerConfig): RegisterResult = suspending {
        return@suspending when (param) {
            is RegistrationParam.PrivateAccount -> {
                with(param) {
                    registerAccountRepository.registerWithEmail(email, emailActivationCode, name, password, serverConfig.apiBaseUrl)
                }
            }
        }.coFold(
            {
                RegisterResult.Failure.Generic(it)
            }, {
                when (val result = loginUseCase(param.email, param.password, true, serverConfig)) {
                    is AuthenticationResult.Success -> {
                        RegisterResult.Success(result.userSession)
                    }
                    is AuthenticationResult.Failure -> {
                        RegisterResult.Failure.Login(result)
                    }
                }
            })
    }
}

sealed class RegisterResult {
    class Success(val value: AuthSession) : RegisterResult()
    sealed class Failure : RegisterResult() {
        class Login(val failure: AuthenticationResult.Failure) : RegisterResult()
        class Generic(val failure: NetworkFailure) : RegisterResult()
    }
}
