package com.wire.kalium.logic.feature.auth

import com.wire.kalium.logic.configuration.GetServerConfigUseCase
import com.wire.kalium.logic.configuration.ServerConfigDataSource
import com.wire.kalium.logic.configuration.ServerConfigRepository
import com.wire.kalium.logic.data.auth.login.LoginRepository
import com.wire.kalium.logic.data.auth.login.LoginRepositoryImpl
import com.wire.kalium.logic.data.register.RegisterAccountDataSource
import com.wire.kalium.logic.data.register.RegisterAccountRepository
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.feature.register.RegisterScope
import com.wire.kalium.logic.feature.session.GetSessionsUseCase
import com.wire.kalium.logic.feature.session.SessionScope
import com.wire.kalium.network.LoginNetworkContainer
import com.wire.kalium.persistence.db.GlobalDatabaseProvider

class AuthenticationScope(
    private val clientLabel: String, private val sessionRepository: SessionRepository, private val globalDatabase: GlobalDatabaseProvider
) {

    protected val loginNetworkContainer: LoginNetworkContainer by lazy {
        LoginNetworkContainer()
    }

    private val serverConfigRepository: ServerConfigRepository get() = ServerConfigDataSource(loginNetworkContainer.serverConfigApi, globalDatabase.serverConfigurationDAO)

    private val loginRepository: LoginRepository get() = LoginRepositoryImpl(loginNetworkContainer.loginApi, clientLabel)

    private val registerAccountRepository: RegisterAccountRepository
        get() = RegisterAccountDataSource(
            loginNetworkContainer.registerApi
        )

    val validateEmailUseCase: ValidateEmailUseCase get() = ValidateEmailUseCaseImpl()
    val validateUserHandleUseCase: ValidateUserHandleUseCase get() = ValidateUserHandleUseCaseImpl()
    val validatePasswordUseCase: ValidatePasswordUseCase get() = ValidatePasswordUseCaseImpl()

    val addAuthenticatedAccount: AddAuthenticatedUserUseCase get() = AddAuthenticatedUserUseCase(sessionRepository)
    val login: LoginUseCase get() = LoginUseCaseImpl(loginRepository, validateEmailUseCase, validateUserHandleUseCase)
    val getSessions: GetSessionsUseCase get() = GetSessionsUseCase(sessionRepository)
    val getServerConfig: GetServerConfigUseCase get() = GetServerConfigUseCase(serverConfigRepository)
    val session: SessionScope get() = SessionScope(sessionRepository)
    val register: RegisterScope get() = RegisterScope(registerAccountRepository)
}
