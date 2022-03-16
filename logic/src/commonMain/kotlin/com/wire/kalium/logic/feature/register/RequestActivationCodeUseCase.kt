package com.wire.kalium.logic.feature.register

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.configuration.ServerConfig
import com.wire.kalium.logic.data.register.RegisterAccountRepository

class RequestActivationCodeUseCase(
    private val registerAccountRepository: RegisterAccountRepository
) {
    suspend operator fun invoke(email: String, serverConfig: ServerConfig): RequestActivationCodeResult =
        registerAccountRepository.requestEmailActivationCode(email, serverConfig.apiBaseUrl)
            .fold({
                RequestActivationCodeResult.Failure.Generic(it)
            }, {
                RequestActivationCodeResult.Success
            })

}

sealed class RequestActivationCodeResult {
    object Success : RequestActivationCodeResult()
    sealed class Failure : RequestActivationCodeResult() {
        class Generic(val failure: NetworkFailure) : Failure()
    }
}
