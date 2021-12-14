package com.wire.kalium.logic.feature.auth

import com.wire.kalium.logic.data.login.LoginRepository
import com.wire.kalium.logic.data.session.InMemorySessionRepository
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.network.LoginNetworkContainer

class AuthenticationScope(
    private val loginNetworkContainer: LoginNetworkContainer, private val clientLabel: String
) {
    private val loginRepository: LoginRepository get() = LoginRepository(loginNetworkContainer.loginApi, clientLabel)
    private val sessionRepository: SessionRepository get() = InMemorySessionRepository()

    val loginUsingEmail: LoginUsingEmailUseCase get() = LoginUsingEmailUseCase(loginRepository, sessionRepository)
    val getSessions: GetSessionsUseCase get() = GetSessionsUseCase(sessionRepository)
}
