package com.wire.kalium.logic.feature.auth

import com.wire.kalium.logic.data.login.LoginRepository
import com.wire.kalium.logic.data.session.SessionMapper
import com.wire.kalium.logic.data.session.SessionMapperImpl
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.session.SessionRepositoryImpl
import com.wire.kalium.logic.feature.session.SessionScope
import com.wire.kalium.network.LoginNetworkContainer
import com.wire.kalium.persistence.client.SessionLocalDataSource

expect class AuthenticationScope: AuthenticationScopeCommon

abstract class AuthenticationScopeCommon(
    private val loginNetworkContainer: LoginNetworkContainer,
    private val clientLabel: String
) {

    protected abstract val sessionLocalDataSource: SessionLocalDataSource

    private val sessionMapper: SessionMapper get() = SessionMapperImpl()

    private val loginRepository: LoginRepository get() = LoginRepository(loginNetworkContainer.loginApi, clientLabel)

    private val sessionRepository: SessionRepository
        get() = SessionRepositoryImpl(
            sessionMapper = sessionMapper,
            sessionLocalDataSource = sessionLocalDataSource
        )
    val loginUsingEmail: LoginUsingEmailUseCase get() = LoginUsingEmailUseCase(loginRepository, sessionRepository)
    val getSessions: GetSessionsUseCase get() = GetSessionsUseCase(sessionRepository)
    val session: SessionScope get() = SessionScope(sessionRepository)
}
