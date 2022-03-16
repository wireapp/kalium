package com.wire.kalium.logic.feature.register

import com.wire.kalium.logic.data.register.RegisterAccountRepository

class RegisterScope (
    private val registerAccountRepository: RegisterAccountRepository
) {
    val register get() = RegisterAccountUseCase(registerAccountRepository)
    val requestActivationCode get() = RequestActivationCodeUseCase(registerAccountRepository)
    val activate get() = VerifyActivationCodeUseCase(registerAccountRepository)
}
