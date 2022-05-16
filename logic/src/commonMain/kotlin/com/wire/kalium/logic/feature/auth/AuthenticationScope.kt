package com.wire.kalium.logic.feature.auth

import com.wire.kalium.logic.configuration.GetServerConfigUseCase
import com.wire.kalium.logic.configuration.ServerConfigDataSource
import com.wire.kalium.logic.configuration.ServerConfigMapper
import com.wire.kalium.logic.configuration.ServerConfigMapperImpl
import com.wire.kalium.logic.configuration.ServerConfigRepository
import com.wire.kalium.logic.configuration.notification.NotificationTokenDataSource
import com.wire.kalium.logic.configuration.notification.NotificationTokenRepository
import com.wire.kalium.logic.configuration.server.ServerConfigDataSource
import com.wire.kalium.logic.configuration.server.ServerConfigMapper
import com.wire.kalium.logic.configuration.server.ServerConfigMapperImpl
import com.wire.kalium.logic.configuration.server.ServerConfigRepository
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
import com.wire.kalium.logic.feature.server_config.GetServerConfigUseCase
import com.wire.kalium.logic.feature.server_config.UpdateApiVersionsUseCase
import com.wire.kalium.logic.feature.server_config.UpdateApiVersionsUseCaseImpl
import com.wire.kalium.logic.feature.session.GetSessionsUseCase
import com.wire.kalium.logic.feature.session.SessionScope
import com.wire.kalium.logic.feature.user.EnableLoggingUseCase
import com.wire.kalium.logic.feature.user.EnableLoggingUseCaseImpl
import com.wire.kalium.logic.feature.user.IsLoggingEnabledUseCase
import com.wire.kalium.logic.feature.user.IsLoggingEnabledUseCaseImpl
import com.wire.kalium.network.UnauthenticatedNetworkContainer
import com.wire.kalium.persistence.client.TokenStorage
import com.wire.kalium.persistence.client.TokenStorageImpl
import com.wire.kalium.persistence.client.UserConfigStorage
import com.wire.kalium.persistence.client.UserConfigStorageImpl
import com.wire.kalium.persistence.db.GlobalDatabaseProvider
import com.wire.kalium.persistence.kmm_settings.KaliumPreferences

class AuthenticationScope(
    private val clientLabel: String, private val sessionRepository: SessionRepository, private val globalDatabase: GlobalDatabaseProvider,
    private val globalPreferences: KaliumPreferences
) {

    private val unauthenticatedNetworkContainer: UnauthenticatedNetworkContainer by lazy {
        UnauthenticatedNetworkContainer()
    }
    private val serverConfigMapper: ServerConfigMapper get() = ServerConfigMapperImpl()
    private val idMapper: IdMapper get() = IdMapperImpl()
    private val sessionMapper: SessionMapper get() = SessionMapperImpl(serverConfigMapper, idMapper)

    private val tokenStorage: TokenStorage get() = TokenStorageImpl(globalPreferences)
    private val userConfigStorage: UserConfigStorage get() = UserConfigStorageImpl(globalPreferences)


    private val serverConfigRepository: ServerConfigRepository
        get() = ServerConfigDataSource(
            unauthenticatedNetworkContainer.serverConfigApi,
            globalDatabase.serverConfigurationDAO,
            loginNetworkContainer.remoteVersion
        )

    private val loginRepository: LoginRepository get() = LoginRepositoryImpl(unauthenticatedNetworkContainer.loginApi, clientLabel)
    private val notificationTokenRepository: NotificationTokenRepository get() = NotificationTokenDataSource(tokenStorage)
    private val userConfigRepository: UserConfigRepository get() = UserConfigDataSource(userConfigStorage)

    private val registerAccountRepository: RegisterAccountRepository
        get() = RegisterAccountDataSource(
            unauthenticatedNetworkContainer.registerApi
        )
    private val ssoLoginRepository: SSOLoginRepository get() = SSOLoginRepositoryImpl(unauthenticatedNetworkContainer.sso)

    val validateEmailUseCase: ValidateEmailUseCase get() = ValidateEmailUseCaseImpl()
    val validateUserHandleUseCase: ValidateUserHandleUseCase get() = ValidateUserHandleUseCaseImpl()
    val validatePasswordUseCase: ValidatePasswordUseCase get() = ValidatePasswordUseCaseImpl()

    val addAuthenticatedAccount: AddAuthenticatedUserUseCase get() = AddAuthenticatedUserUseCase(sessionRepository)
    val login: LoginUseCase get() = LoginUseCaseImpl(loginRepository, validateEmailUseCase, validateUserHandleUseCase)
    val getSessions: GetSessionsUseCase get() = GetSessionsUseCase(sessionRepository)
    val getServerConfig: GetServerConfigUseCase get() = GetServerConfigUseCase(serverConfigRepository)
    val updateApiVersions: UpdateApiVersionsUseCase get() = UpdateApiVersionsUseCaseImpl(serverConfigRepository, serverConfigMapper)
    val session: SessionScope get() = SessionScope(sessionRepository)
    val register: RegisterScope get() = RegisterScope(registerAccountRepository)
    val ssoLoginScope: SSOLoginScope get() = SSOLoginScope(ssoLoginRepository, sessionMapper)
    val saveNotificationToken: SaveNotificationTokenUseCase get() = SaveNotificationTokenUseCase(notificationTokenRepository)
}
