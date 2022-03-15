package com.wire.kalium.logic.feature.register

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.configuration.ServerConfig
import com.wire.kalium.logic.data.register.RegisterAccountRepository
import com.wire.kalium.network.api.model.UserDTO

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
) {
    suspend operator fun invoke(param: RegistrationParam, serverConfig: ServerConfig): RegisterResult {
        return when (param) {
            is RegistrationParam.PrivateAccount -> {
                with(param) {
                    registerAccountRepository.registerWithEmail(email, emailActivationCode, name, password, serverConfig.apiBaseUrl)
                }
            }
        }.fold(
            {
                RegisterResult.Failure.Generic(it)
            }, {
                // TODO: login user
                RegisterResult.Success(it)
            })
    }
}

sealed class RegisterResult {
    class Success(val value: UserDTO) : RegisterResult()
    sealed class Failure : RegisterResult() {
        class Generic(val failure: NetworkFailure) : RegisterResult()
    }
}
