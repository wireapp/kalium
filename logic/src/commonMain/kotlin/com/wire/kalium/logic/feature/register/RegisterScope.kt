package com.wire.kalium.logic.feature.register

import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.data.register.RegisterAccountRepository

class RegisterScope internal constructor(
    private val registerAccountRepository: RegisterAccountRepository,
    private val serverConfig: ServerConfig
) {
    val register get() = RegisterAccountUseCase(registerAccountRepository, serverConfig)
    val requestActivationCode get() = RequestActivationCodeUseCase(registerAccountRepository)
    val activate get() = VerifyActivationCodeUseCase(registerAccountRepository)
}
