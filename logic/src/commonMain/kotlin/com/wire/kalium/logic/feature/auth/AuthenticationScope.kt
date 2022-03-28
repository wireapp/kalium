package com.wire.kalium.logic.feature.auth

import com.wire.kalium.logic.configuration.GetServerConfigUseCase
import com.wire.kalium.logic.configuration.ServerConfigDataSource
import com.wire.kalium.logic.configuration.ServerConfigRemoteDataSource
import com.wire.kalium.logic.configuration.ServerConfigRemoteRepository
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
import com.wire.kalium.persistence.kmm_settings.EncryptedSettingsHolder
import com.wire.kalium.persistence.kmm_settings.KaliumPreferences
import com.wire.kalium.persistence.kmm_settings.KaliumPreferencesSettings

expect class AuthenticationScope : AuthenticationScopeCommon

abstract class AuthenticationScopeCommon(
    private val clientLabel: String, private val sessionRepository: SessionRepository
) {

    protected val loginNetworkContainer: LoginNetworkContainer by lazy {
        LoginNetworkContainer()
    }

    protected abstract val encryptedSettingsHolder: EncryptedSettingsHolder
    private val kaliumPreferences: KaliumPreferences get() = KaliumPreferencesSettings(encryptedSettingsHolder.encryptedSettings)

    private val serverConfigRemoteRepository: ServerConfigRemoteRepository get() = ServerConfigRemoteDataSource(loginNetworkContainer.serverConfigApi)
    private val serverConfigRepository: ServerConfigRepository get() = ServerConfigDataSource(serverConfigRemoteRepository)


    private val loginRepository: LoginRepository get() = LoginRepositoryImpl(loginNetworkContainer.loginApi, clientLabel)

    private val registerAccountRepository: RegisterAccountRepository
        get() = RegisterAccountDataSource(
            loginNetworkContainer.registerApi
        )

    val validateEmailUseCase: ValidateEmailUseCase get() = ValidateEmailUseCaseImpl()
    val validateUserHandleUseCase: ValidateUserHandleUseCase get() = ValidateUserHandleUseCaseImpl()
    val validatePasswordUseCase: ValidatePasswordUseCase get() = ValidatePasswordUseCaseImpl()

    val login: LoginUseCase get() = LoginUseCaseImpl(loginRepository, sessionRepository, validateEmailUseCase, validateUserHandleUseCase)
    val getSessions: GetSessionsUseCase get() = GetSessionsUseCase(sessionRepository)
    val getServerConfig: GetServerConfigUseCase get() = GetServerConfigUseCase(serverConfigRepository)
    val session: SessionScope get() = SessionScope(sessionRepository)
    val register: RegisterScope get() = RegisterScope(registerAccountRepository)
}
