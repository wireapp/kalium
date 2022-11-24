package com.wire.kalium.logic.feature.register

import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.data.auth.login.ProxyCredentials
import com.wire.kalium.logic.data.register.RegisterAccountRepository

class RegisterScope internal constructor(
    private val registerAccountRepository: RegisterAccountRepository,
    private val serverConfig: ServerConfig,
    private val proxyCredentials: ProxyCredentials?
) {
    val register get() = RegisterAccountUseCase(registerAccountRepository, serverConfig, proxyCredentials)
    val requestActivationCode get() = RequestActivationCodeUseCase(registerAccountRepository)
    val activate get() = VerifyActivationCodeUseCase(registerAccountRepository)
}
