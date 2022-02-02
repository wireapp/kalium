package com.wire.kalium.logic.feature.auth

import com.wire.kalium.logic.data.auth.login.LoginRepository
import com.wire.kalium.logic.data.auth.login.LoginRepositoryImpl
import com.wire.kalium.logic.data.session.SessionMapper
import com.wire.kalium.logic.data.session.SessionMapperImpl
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.session.SessionDataSource
import com.wire.kalium.logic.data.session.local.SessionLocalDataSource
import com.wire.kalium.logic.data.session.local.SessionLocalRepository
import com.wire.kalium.logic.feature.session.SessionScope
import com.wire.kalium.network.LoginNetworkContainer
import com.wire.kalium.persistence.client.SessionDAOImpl
import com.wire.kalium.persistence.client.SessionDAO
import com.wire.kalium.persistence.kmm_settings.EncryptedSettingsHolder
import com.wire.kalium.persistence.kmm_settings.KaliumPreferences
import com.wire.kalium.persistence.kmm_settings.KaliumPreferencesSettings

expect class AuthenticationScope : AuthenticationScopeCommon

abstract class AuthenticationScopeCommon(
    private val clientLabel: String
) {

    protected val loginNetworkContainer: LoginNetworkContainer by lazy {
        LoginNetworkContainer()
    }

    protected abstract val encryptedSettingsHolder: EncryptedSettingsHolder
    private val kaliumPreferences: KaliumPreferences get() = KaliumPreferencesSettings(encryptedSettingsHolder.encryptedSettings)
    private val sessionDao: SessionDAO get() = SessionDAOImpl(kaliumPreferences)

    private val sessionMapper: SessionMapper get() = SessionMapperImpl()

    private val loginRepository: LoginRepository get() = LoginRepositoryImpl(loginNetworkContainer.loginApi, clientLabel)

    private val sessionLocalRepository: SessionLocalRepository get() = SessionLocalDataSource(sessionDao, sessionMapper)
    private val sessionRepository: SessionRepository
        get() = SessionDataSource(sessionLocalRepository)

    private val validateEmailUseCase: ValidateEmailUseCase get() = ValidateEmailUseCaseImpl()
    private val validateUserHandleUseCase: ValidateUserHandleUseCase get() = ValidateUserHandleUseCaseImpl()

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
