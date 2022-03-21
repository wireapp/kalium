package com.wire.kalium.logic.feature.register

import com.wire.kalium.logic.data.register.RegisterAccountRepository
import com.wire.kalium.logic.data.session.SessionRepository

class RegisterScope (
    private val registerAccountRepository: RegisterAccountRepository,
    private val sessionRepository: SessionRepository
) {
    val register get() = RegisterAccountUseCase(registerAccountRepository, sessionRepository)
    val requestActivationCode get() = RequestActivationCodeUseCase(registerAccountRepository)
    val activate get() = VerifyActivationCodeUseCase(registerAccountRepository)
}
