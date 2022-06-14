package com.wire.kalium.logic.feature.register

import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.data.register.RegisterAccountRepository

class RegisterScope(
    private val registerAccountRepository: RegisterAccountRepository,
    private val serverLinks: ServerConfig.Links
) {
    val register get() = RegisterAccountUseCase(registerAccountRepository, serverLinks)
    val requestActivationCode get() = RequestActivationCodeUseCase(registerAccountRepository)
    val activate get() = VerifyActivationCodeUseCase(registerAccountRepository)
}
