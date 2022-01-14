package com.wire.kalium.logic.feature.auth

import com.wire.kalium.logic.data.auth.login.LoginRepository
import com.wire.kalium.logic.data.auth.login.LoginRepositoryImpl
import com.wire.kalium.logic.data.session.SessionMapper
import com.wire.kalium.logic.data.session.SessionMapperImpl
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.session.SessionRepositoryImpl
import com.wire.kalium.logic.feature.session.SessionScope
import com.wire.kalium.network.LoginNetworkContainer
import com.wire.kalium.persistence.client.SessionLocalDataSource

expect class AuthenticationScope : AuthenticationScopeCommon

abstract class AuthenticationScopeCommon(
    private val loginNetworkContainer: LoginNetworkContainer,
    private val clientLabel: String
) {

    protected abstract val sessionLocalDataSource: SessionLocalDataSource

    private val sessionMapper: SessionMapper get() = SessionMapperImpl()

    private val loginRepository: LoginRepository get() = LoginRepositoryImpl(loginNetworkContainer.loginApi, clientLabel)

    private val sessionRepository: SessionRepository
        get() = SessionRepositoryImpl(
            sessionMapper = sessionMapper,
            sessionLocalDataSource = sessionLocalDataSource
        )

    private val validateEmailUseCase: ValidateEmailUseCase get() = ValidateEmailUseCase()
    private val validateUserHandleUseCase: ValidateUserHandleUseCase get() = ValidateUserHandleUseCase()

    val loginUsingEmail: LoginUseCase
        get() = LoginUseCase(
            loginRepository,
            sessionRepository,
            validateEmailUseCase,
            validateUserHandleUseCase
        )

    val getSessions: GetSessionsUseCase get() = GetSessionsUseCase(sessionRepository)
    val session: SessionScope get() = SessionScope(sessionRepository)
}
