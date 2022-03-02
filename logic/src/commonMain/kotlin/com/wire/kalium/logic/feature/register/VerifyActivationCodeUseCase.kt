package com.wire.kalium.logic.feature.register

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.configuration.ServerConfig
import com.wire.kalium.logic.data.register.RegisterAccountRepository

class VerifyActivationCodeUseCase(
    private val registerAccountRepository: RegisterAccountRepository
) {
    suspend operator fun invoke(email: String, code: String, serverConfig: ServerConfig): VerifyActivationCodeResult {

        return registerAccountRepository.verifyActivationCode(email, code, serverConfig.apiBaseUrl)
            .fold({
                VerifyActivationCodeResult.Failure.Generic(it)
            }, {
                VerifyActivationCodeResult.Success
            })
    }
}

sealed class VerifyActivationCodeResult {
    object Success : VerifyActivationCodeResult()
    sealed class Failure : VerifyActivationCodeResult() {
        class Generic(val failure: NetworkFailure) : Failure()
    }
}
