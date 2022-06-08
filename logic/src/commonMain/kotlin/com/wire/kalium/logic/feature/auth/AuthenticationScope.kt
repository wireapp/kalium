package com.wire.kalium.logic.feature.auth

import com.wire.kalium.logic.GlobalKaliumScope
import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.configuration.server.ServerConfigMapper
import com.wire.kalium.logic.configuration.server.ServerConfigRepository
import com.wire.kalium.logic.data.auth.login.LoginRepository
import com.wire.kalium.logic.data.auth.login.LoginRepositoryImpl
import com.wire.kalium.logic.data.auth.login.SSOLoginRepository
import com.wire.kalium.logic.data.auth.login.SSOLoginRepositoryImpl
import com.wire.kalium.logic.data.register.RegisterAccountDataSource
import com.wire.kalium.logic.data.register.RegisterAccountRepository
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.feature.auth.sso.SSOLoginScope
import com.wire.kalium.logic.feature.register.RegisterScope
import com.wire.kalium.logic.feature.session.GetSessionsUseCase
import com.wire.kalium.logic.feature.session.SessionScope
import com.wire.kalium.logic.feature.user.EnableLoggingUseCase
import com.wire.kalium.logic.feature.user.EnableLoggingUseCaseImpl
import com.wire.kalium.logic.feature.user.IsLoggingEnabledUseCase
import com.wire.kalium.logic.feature.user.IsLoggingEnabledUseCaseImpl
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.featureFlags.GetBuildConfigsUseCase
import com.wire.kalium.logic.featureFlags.GetBuildConfigsUseCaseImpl
import com.wire.kalium.logic.functional.nullableFold
import com.wire.kalium.network.ServerMetaDataManager
import com.wire.kalium.network.UnauthenticatedNetworkContainer
import com.wire.kalium.network.tools.ServerConfigDTO
import com.wire.kalium.persistence.kmm_settings.KaliumPreferences

class AuthenticationScope(
    private val clientLabel: String,
    private val globalPreferences: KaliumPreferences,
    private val backendLinks: ServerConfig.Links,
    private val globalScope: GlobalKaliumScope,
    private val kaliumConfigs: KaliumConfigs
) {

    private val unauthenticatedNetworkContainer: UnauthenticatedNetworkContainer by lazy {
        UnauthenticatedNetworkContainer(
            MapperProvider.serverConfigMapper().toDTO(backendLinks),
            serverMetaDataManager = ServerMetaDataManagerImpl(globalScope.serverConfigRepository)
        )
    }
    private val loginRepository: LoginRepository get() = LoginRepositoryImpl(unauthenticatedNetworkContainer.loginApi, clientLabel)

    private val registerAccountRepository: RegisterAccountRepository
        get() = RegisterAccountDataSource(
            unauthenticatedNetworkContainer.registerApi
        )
    private val ssoLoginRepository: SSOLoginRepository get() = SSOLoginRepositoryImpl(unauthenticatedNetworkContainer.sso)

    val validateEmailUseCase: ValidateEmailUseCase get() = ValidateEmailUseCaseImpl()
    val validateUserHandleUseCase: ValidateUserHandleUseCase get() = ValidateUserHandleUseCaseImpl()
    val validatePasswordUseCase: ValidatePasswordUseCase get() = ValidatePasswordUseCaseImpl()

    val login: LoginUseCase get() = LoginUseCaseImpl(loginRepository, validateEmailUseCase, validateUserHandleUseCase, backendLinks)
    val register: RegisterScope get() = RegisterScope(registerAccountRepository, backendLinks)
    val ssoLoginScope: SSOLoginScope get() = SSOLoginScope(ssoLoginRepository, backendLinks, globalScope.serverConfigRepository)

    /*
    val addAuthenticatedAccount: AddAuthenticatedUserUseCase get() = AddAuthenticatedUserUseCase(sessionRepository)
    val login: LoginUseCase get() = LoginUseCaseImpl(loginRepository, validateEmailUseCase, validateUserHandleUseCase)
    val getSessions: GetSessionsUseCase get() = GetSessionsUseCase(sessionRepository)
    val getServerConfig: GetServerConfigUseCase get() = GetServerConfigUseCase(serverConfigRepository)
    val session: SessionScope get() = SessionScope(sessionRepository)
    val register: RegisterScope get() = RegisterScope(registerAccountRepository)
    val ssoLoginScope: SSOLoginScope get() = SSOLoginScope(ssoLoginRepository, sessionMapper)
    val saveNotificationToken: SaveNotificationTokenUseCase get() = SaveNotificationTokenUseCase(notificationTokenRepository)
    val enableLogging: EnableLoggingUseCase get() = EnableLoggingUseCaseImpl(userConfigRepository)
    val isLoggingEnabled: IsLoggingEnabledUseCase get() = IsLoggingEnabledUseCaseImpl(userConfigRepository)
    val buildConfigs: GetBuildConfigsUseCase get() = GetBuildConfigsUseCaseImpl(kaliumConfigs)
     */
}


class ServerMetaDataManagerImpl internal constructor(
    private val serverConfigRepository: ServerConfigRepository,
    private val serverConfigMapper: ServerConfigMapper = MapperProvider.serverConfigMapper()
) : ServerMetaDataManager {

    override fun getLocalMetaData(backendLinks: ServerConfigDTO.Links): ServerConfigDTO? =
        serverConfigRepository.configByLinks(serverConfigMapper.fromDTO(backendLinks)).nullableFold({
            null
        }, {
            serverConfigMapper.toDTO(it)
        })

    override fun storeBackend(links: ServerConfigDTO.Links, metaData: ServerConfigDTO.MetaData): ServerConfigDTO? {
        return serverConfigRepository.storeConfig(serverConfigMapper.fromDTO(links), serverConfigMapper.fromDTO(metaData)).nullableFold({
            null
        }, {
            serverConfigMapper.toDTO(it)
        })
    }
}
