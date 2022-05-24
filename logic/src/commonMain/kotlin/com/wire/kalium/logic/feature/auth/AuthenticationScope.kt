package com.wire.kalium.logic.feature.auth

import com.wire.kalium.logic.configuration.UserConfigDataSource
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.configuration.notification.NotificationTokenDataSource
import com.wire.kalium.logic.configuration.notification.NotificationTokenRepository
import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.data.auth.login.LoginRepository
import com.wire.kalium.logic.data.auth.login.LoginRepositoryImpl
import com.wire.kalium.logic.data.auth.login.SSOLoginRepository
import com.wire.kalium.logic.data.auth.login.SSOLoginRepositoryImpl
import com.wire.kalium.logic.data.register.RegisterAccountDataSource
import com.wire.kalium.logic.data.register.RegisterAccountRepository
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.feature.auth.sso.SSOLoginScope
import com.wire.kalium.logic.feature.notificationToken.SaveNotificationTokenUseCase
import com.wire.kalium.logic.feature.register.RegisterScope
import com.wire.kalium.logic.feature.user.EnableLoggingUseCase
import com.wire.kalium.logic.feature.user.EnableLoggingUseCaseImpl
import com.wire.kalium.logic.feature.user.IsLoggingEnabledUseCase
import com.wire.kalium.logic.feature.user.IsLoggingEnabledUseCaseImpl
import com.wire.kalium.network.UnauthenticatedNetworkContainer
import com.wire.kalium.persistence.client.TokenStorage
import com.wire.kalium.persistence.client.TokenStorageImpl
import com.wire.kalium.persistence.client.UserConfigStorage
import com.wire.kalium.persistence.client.UserConfigStorageImpl
import com.wire.kalium.persistence.kmm_settings.KaliumPreferences

class AuthenticationScope(
    private val clientLabel: String,
    private val globalPreferences: KaliumPreferences,
    private val backendLinks: ServerConfig.Links
) {

    private val unauthenticatedNetworkContainer: UnauthenticatedNetworkContainer by lazy {
        // "map backendLinks to WireServerDTO.Links"
        UnauthenticatedNetworkContainer(MapperProvider.serverConfigMapper().toDTO(backendLinks))
    }

    private val tokenStorage: TokenStorage get() = TokenStorageImpl(globalPreferences)
    private val userConfigStorage: UserConfigStorage get() = UserConfigStorageImpl(globalPreferences)


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

    val login: LoginUseCase get() = LoginUseCaseImpl(loginRepository, validateEmailUseCase, validateUserHandleUseCase)
    val register: RegisterScope get() = RegisterScope(registerAccountRepository)
    val ssoLoginScope: SSOLoginScope get() = SSOLoginScope(ssoLoginRepository)
    val saveNotificationToken: SaveNotificationTokenUseCase get() = SaveNotificationTokenUseCase(notificationTokenRepository)
    val enableLogging: EnableLoggingUseCase get() = EnableLoggingUseCaseImpl(userConfigRepository)
    val isLoggingEnabled: IsLoggingEnabledUseCase get() = IsLoggingEnabledUseCaseImpl(userConfigRepository)
}
