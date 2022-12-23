package com.wire.kalium.logic.feature.auth

import com.wire.kalium.logic.configuration.appVersioning.AppVersionRepository
import com.wire.kalium.logic.configuration.appVersioning.AppVersionRepositoryImpl
import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.data.auth.login.LoginRepository
import com.wire.kalium.logic.data.auth.login.LoginRepositoryImpl
import com.wire.kalium.logic.data.auth.login.ProxyCredentials
import com.wire.kalium.logic.data.auth.login.SSOLoginRepository
import com.wire.kalium.logic.data.auth.login.SSOLoginRepositoryImpl
import com.wire.kalium.logic.data.register.RegisterAccountDataSource
import com.wire.kalium.logic.data.register.RegisterAccountRepository
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.feature.appVersioning.CheckIfUpdateRequiredUseCase
import com.wire.kalium.logic.feature.appVersioning.CheckIfUpdateRequiredUseCaseImpl
import com.wire.kalium.logic.feature.auth.sso.SSOLoginScope
import com.wire.kalium.logic.feature.register.RegisterScope
import com.wire.kalium.network.networkContainer.UnauthenticatedNetworkContainer
import io.ktor.util.collections.ConcurrentMap

class AuthenticationScopeProvider() {

    private val authenticationScopeStorage: ConcurrentMap<Pair<ServerConfig, ProxyCredentials?>, AuthenticationScope> by lazy {
        ConcurrentMap()
    }

    fun provide(serverConfig: ServerConfig, proxyCredentials: ProxyCredentials?): AuthenticationScope =
        authenticationScopeStorage.computeIfAbsent(serverConfig to proxyCredentials) {
            AuthenticationScope(
                serverConfig,
                proxyCredentials
            )
        }
}

class AuthenticationScope(
    private val serverConfig: ServerConfig,
    private val proxyCredentials: ProxyCredentials?
) {

    private val unauthenticatedNetworkContainer: UnauthenticatedNetworkContainer by lazy {
        UnauthenticatedNetworkContainer.create(
            MapperProvider.serverConfigMapper().toDTO(serverConfig),
            proxyCredentials?.let { MapperProvider.sessionMapper().fromModelToProxyCredentialsDTO(it) }
        )
    }
    private val loginRepository: LoginRepository
        get() = LoginRepositoryImpl(unauthenticatedNetworkContainer.loginApi)

    private val registerAccountRepository: RegisterAccountRepository
        get() = RegisterAccountDataSource(
            unauthenticatedNetworkContainer.registerApi
        )
    private val ssoLoginRepository: SSOLoginRepository
        get() = SSOLoginRepositoryImpl(unauthenticatedNetworkContainer.sso)

    private val validateEmailUseCase: ValidateEmailUseCase get() = ValidateEmailUseCaseImpl()
    private val validateUserHandleUseCase: ValidateUserHandleUseCase get() = ValidateUserHandleUseCaseImpl()

    private val appVersionRepository: AppVersionRepository
        get() = AppVersionRepositoryImpl(unauthenticatedNetworkContainer.appVersioningApi)

    val login: LoginUseCase
        get() = LoginUseCaseImpl(
            loginRepository,
            validateEmailUseCase,
            validateUserHandleUseCase,
            serverConfig,
            proxyCredentials
        )
    val registerScope: RegisterScope
        get() = RegisterScope(registerAccountRepository, serverConfig, proxyCredentials)
    val ssoLoginScope: SSOLoginScope
        get() = SSOLoginScope(ssoLoginRepository, serverConfig, proxyCredentials)
    val checkIfUpdateRequired: CheckIfUpdateRequiredUseCase
        get() = CheckIfUpdateRequiredUseCaseImpl(appVersionRepository)
}
