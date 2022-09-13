package com.wire.kalium.logic.feature.register

import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.configuration.server.ServerConfigRepository
import com.wire.kalium.logic.data.register.RegisterAccountRepository

class RegisterScope internal constructor(
    private val registerAccountRepository: RegisterAccountRepository,
    private val serverConfigRepository: ServerConfigRepository,
    private val serverLinks: ServerConfig.Links
) {
    val register get() = RegisterAccountUseCase(registerAccountRepository, serverConfigRepository, serverLinks)
    val requestActivationCode get() = RequestActivationCodeUseCase(registerAccountRepository)
    val activate get() = VerifyActivationCodeUseCase(registerAccountRepository)
}
