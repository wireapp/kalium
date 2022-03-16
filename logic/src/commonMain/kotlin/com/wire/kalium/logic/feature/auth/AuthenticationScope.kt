package com.wire.kalium.logic.feature.auth

import com.wire.kalium.logic.configuration.GetServerConfigUseCase
import com.wire.kalium.logic.configuration.ServerConfigDataSource
import com.wire.kalium.logic.configuration.ServerConfigMapper
import com.wire.kalium.logic.configuration.ServerConfigMapperImpl
import com.wire.kalium.logic.configuration.ServerConfigRemoteDataSource
import com.wire.kalium.logic.configuration.ServerConfigRemoteRepository
import com.wire.kalium.logic.configuration.ServerConfigRepository
import com.wire.kalium.logic.data.auth.login.LoginRepository
import com.wire.kalium.logic.data.auth.login.LoginRepositoryImpl
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.id.IdMapperImpl
import com.wire.kalium.logic.data.register.RegisterAccountDataSource
import com.wire.kalium.logic.data.register.RegisterAccountRepository
import com.wire.kalium.logic.data.session.SessionMapper
import com.wire.kalium.logic.data.session.SessionMapperImpl
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.user.UserMapper
import com.wire.kalium.logic.data.user.UserMapperImpl
import com.wire.kalium.logic.feature.register.RegisterScope
import com.wire.kalium.logic.feature.session.GetSessionsUseCase
import com.wire.kalium.logic.feature.session.SessionScope
import com.wire.kalium.network.LoginNetworkContainer
import com.wire.kalium.persistence.kmm_settings.EncryptedSettingsHolder
import com.wire.kalium.persistence.kmm_settings.KaliumPreferences
import com.wire.kalium.persistence.kmm_settings.KaliumPreferencesSettings

expect class AuthenticationScope : AuthenticationScopeCommon

abstract class AuthenticationScopeCommon(
    private val clientLabel: String,
    private val sessionRepository: SessionRepository
) {

    protected val loginNetworkContainer: LoginNetworkContainer by lazy {
        LoginNetworkContainer()
    }

    protected abstract val encryptedSettingsHolder: EncryptedSettingsHolder
    private val kaliumPreferences: KaliumPreferences get() = KaliumPreferencesSettings(encryptedSettingsHolder.encryptedSettings)

    private val serverConfigMapper: ServerConfigMapper get() = ServerConfigMapperImpl()
    private val serverConfigRemoteRepository: ServerConfigRemoteRepository
        get() = ServerConfigRemoteDataSource(
            loginNetworkContainer.serverConfigApi,
            serverConfigMapper
        )
    private val serverConfigRepository: ServerConfigRepository get() = ServerConfigDataSource(serverConfigRemoteRepository)

    private val sessionMapper: SessionMapper get() = SessionMapperImpl(serverConfigMapper)
    private val idMapper: IdMapper get() = IdMapperImpl()
    private val userMapper: UserMapper get() = UserMapperImpl(idMapper)

    private val loginRepository: LoginRepository get() = LoginRepositoryImpl(loginNetworkContainer.loginApi, clientLabel, sessionMapper)

    private val registerAccountRepository: RegisterAccountRepository
        get() = RegisterAccountDataSource(
            loginNetworkContainer.registerApi,
            userMapper,
            sessionMapper
        )

    private val validateEmailUseCase: ValidateEmailUseCase get() = ValidateEmailUseCaseImpl()
    private val validateUserHandleUseCase: ValidateUserHandleUseCase get() = ValidateUserHandleUseCaseImpl()

    val login: LoginUseCase
        get() = LoginUseCaseImpl(
            loginRepository,
            sessionRepository,
            validateEmailUseCase,
            validateUserHandleUseCase
        )

    val getSessions: GetSessionsUseCase get() = GetSessionsUseCase(sessionRepository)
    val getServerConfig: GetServerConfigUseCase get() = GetServerConfigUseCase(serverConfigRepository)
    val session: SessionScope get() = SessionScope(sessionRepository)
    val register: RegisterScope get() = RegisterScope(registerAccountRepository, sessionRepository)
}
