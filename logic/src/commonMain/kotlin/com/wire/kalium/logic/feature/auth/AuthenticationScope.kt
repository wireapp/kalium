package com.wire.kalium.logic.feature.auth

import com.wire.kalium.logic.GlobalKaliumScope
import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.data.auth.login.LoginRepository
import com.wire.kalium.logic.data.auth.login.LoginRepositoryImpl
import com.wire.kalium.logic.data.auth.login.SSOLoginRepository
import com.wire.kalium.logic.data.auth.login.SSOLoginRepositoryImpl
import com.wire.kalium.logic.data.register.RegisterAccountDataSource
import com.wire.kalium.logic.data.register.RegisterAccountRepository
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.feature.auth.sso.SSOLoginScope
import com.wire.kalium.logic.feature.register.RegisterScope
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.network.networkContainer.UnauthenticatedNetworkContainer

class AuthenticationScope(
    private val clientLabel: String,
    private val backendLinks: ServerConfig,
    private val globalScope: GlobalKaliumScope,
    private val kaliumConfigs: KaliumConfigs
) {

    private val unauthenticatedNetworkContainer: UnauthenticatedNetworkContainer by lazy {
        UnauthenticatedNetworkContainer.create(
            MapperProvider.serverConfigMapper().toDTO(backendLinks)
        )
    }
    private val loginRepository: LoginRepository
        get() = LoginRepositoryImpl(unauthenticatedNetworkContainer.loginApi, clientLabel)

    private val registerAccountRepository: RegisterAccountRepository
        get() = RegisterAccountDataSource(
            unauthenticatedNetworkContainer.registerApi
        )
    private val ssoLoginRepository: SSOLoginRepository
        get() = SSOLoginRepositoryImpl(unauthenticatedNetworkContainer.sso)

    private val validateEmailUseCase: ValidateEmailUseCase get() = ValidateEmailUseCaseImpl()
    private val validateUserHandleUseCase: ValidateUserHandleUseCase get() = ValidateUserHandleUseCaseImpl()

    val login: LoginUseCase
        get() = LoginUseCaseImpl(
            loginRepository,
            validateEmailUseCase,
            validateUserHandleUseCase,
            backendLinks
        )
    val registerScope: RegisterScope
        get() = RegisterScope(registerAccountRepository, backendLinks)
    val ssoLoginScope: SSOLoginScope
        get() = SSOLoginScope(ssoLoginRepository, backendLinks)
}
