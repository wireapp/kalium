package com.wire.kalium.logic.feature.auth

import com.wire.kalium.logic.configuration.GetServerConfigUseCase
import com.wire.kalium.logic.configuration.ServerConfigDataSource
import com.wire.kalium.logic.configuration.ServerConfigMapper
import com.wire.kalium.logic.configuration.ServerConfigMapperImpl
import com.wire.kalium.logic.configuration.ServerConfigRepository
import com.wire.kalium.logic.configuration.notification.NotificationTokenDataSource
import com.wire.kalium.logic.configuration.notification.NotificationTokenRepository
import com.wire.kalium.logic.data.auth.login.LoginRepository
import com.wire.kalium.logic.data.auth.login.LoginRepositoryImpl
import com.wire.kalium.logic.data.auth.login.SSOLoginRepository
import com.wire.kalium.logic.data.auth.login.SSOLoginRepositoryImpl
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.id.IdMapperImpl
import com.wire.kalium.logic.data.register.RegisterAccountDataSource
import com.wire.kalium.logic.data.register.RegisterAccountRepository
import com.wire.kalium.logic.data.session.SessionMapper
import com.wire.kalium.logic.data.session.SessionMapperImpl
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.feature.auth.sso.SSOLoginScope
import com.wire.kalium.logic.feature.notification_token.SaveNotificationTokenUseCase
import com.wire.kalium.logic.feature.register.RegisterScope
import com.wire.kalium.logic.feature.session.GetSessionsUseCase
import com.wire.kalium.logic.feature.session.SessionScope
import com.wire.kalium.network.LoginNetworkContainer
import com.wire.kalium.persistence.client.TokenStorage
import com.wire.kalium.persistence.client.TokenStorageImpl
import com.wire.kalium.persistence.db.GlobalDatabaseProvider
import com.wire.kalium.persistence.kmm_settings.KaliumPreferences

class AuthenticationScope(
    private val clientLabel: String, private val sessionRepository: SessionRepository, private val globalDatabase: GlobalDatabaseProvider,
    private val globalPreferences: KaliumPreferences
) {

    protected val loginNetworkContainer: LoginNetworkContainer by lazy {
        LoginNetworkContainer()
    }
    private val serverConfigMapper: ServerConfigMapper get() = ServerConfigMapperImpl()
    private val idMapper: IdMapper get() = IdMapperImpl()
    private val sessionMapper: SessionMapper get() = SessionMapperImpl(serverConfigMapper, idMapper)

    private val tokenStorage: TokenStorage get() = TokenStorageImpl(globalPreferences)


    private val serverConfigRepository: ServerConfigRepository
        get() = ServerConfigDataSource(
            loginNetworkContainer.serverConfigApi,
            globalDatabase.serverConfigurationDAO
        )

    private val loginRepository: LoginRepository get() = LoginRepositoryImpl(loginNetworkContainer.loginApi, clientLabel)
    private val notificationTokenRepository: NotificationTokenRepository get() = NotificationTokenDataSource(tokenStorage)

    private val registerAccountRepository: RegisterAccountRepository
        get() = RegisterAccountDataSource(
            loginNetworkContainer.registerApi
        )
    private val ssoLoginRepository: SSOLoginRepository get() = SSOLoginRepositoryImpl(loginNetworkContainer.sso)

    val validateEmailUseCase: ValidateEmailUseCase get() = ValidateEmailUseCaseImpl()
    val validateUserHandleUseCase: ValidateUserHandleUseCase get() = ValidateUserHandleUseCaseImpl()
    val validatePasswordUseCase: ValidatePasswordUseCase get() = ValidatePasswordUseCaseImpl()

    val addAuthenticatedAccount: AddAuthenticatedUserUseCase get() = AddAuthenticatedUserUseCase(sessionRepository)
    val login: LoginUseCase get() = LoginUseCaseImpl(loginRepository, validateEmailUseCase, validateUserHandleUseCase)
    val getSessions: GetSessionsUseCase get() = GetSessionsUseCase(sessionRepository)
    val getServerConfig: GetServerConfigUseCase get() = GetServerConfigUseCase(serverConfigRepository)
    val session: SessionScope get() = SessionScope(sessionRepository)
    val register: RegisterScope get() = RegisterScope(registerAccountRepository)
    val ssoLoginScope: SSOLoginScope get() = SSOLoginScope(ssoLoginRepository, sessionMapper)
    val saveNotificationToken: SaveNotificationTokenUseCase get() = SaveNotificationTokenUseCase(notificationTokenRepository)
}
