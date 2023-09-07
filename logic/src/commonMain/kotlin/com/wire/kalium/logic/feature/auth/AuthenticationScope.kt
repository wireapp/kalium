/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

package com.wire.kalium.logic.feature.auth

import co.touchlab.stately.collections.ConcurrentMutableMap
import com.wire.kalium.logic.configuration.appVersioning.AppVersionRepository
import com.wire.kalium.logic.configuration.appVersioning.AppVersionRepositoryImpl
import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.configuration.server.ServerConfigRepository
import com.wire.kalium.logic.data.AccessRepository
import com.wire.kalium.logic.data.auth.login.LoginRepository
import com.wire.kalium.logic.data.auth.login.LoginRepositoryImpl
import com.wire.kalium.logic.data.auth.login.ProxyCredentials
import com.wire.kalium.logic.data.auth.login.SSOLoginRepository
import com.wire.kalium.logic.data.auth.login.SSOLoginRepositoryImpl
import com.wire.kalium.logic.data.auth.verification.SecondFactorVerificationRepository
import com.wire.kalium.logic.data.auth.verification.SecondFactorVerificationRepositoryImpl
import com.wire.kalium.logic.data.register.RegisterAccountDataSource
import com.wire.kalium.logic.data.register.RegisterAccountRepository
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.feature.IsUserLoggedInUseCase
import com.wire.kalium.logic.feature.IsUserLoggedInUseCaseImpl
import com.wire.kalium.logic.feature.appVersioning.CheckIfUpdateRequiredUseCase
import com.wire.kalium.logic.feature.appVersioning.CheckIfUpdateRequiredUseCaseImpl
import com.wire.kalium.logic.feature.auth.sso.SSOLoginScope
import com.wire.kalium.logic.feature.auth.verification.RequestSecondFactorVerificationCodeUseCase
import com.wire.kalium.logic.feature.register.RegisterScope
import com.wire.kalium.logic.util.computeIfAbsent
import com.wire.kalium.network.NetworkStateObserver
import com.wire.kalium.network.networkContainer.UnauthenticatedNetworkContainer
import com.wire.kalium.network.session.CertificatePinning

class AuthenticationScopeProvider internal constructor(
    private val userAgent: String
) {

    private val authenticationScopeStorage: ConcurrentMutableMap<Pair<ServerConfig, ProxyCredentials?>,
            AuthenticationScope> by lazy {
        ConcurrentMutableMap()
    }

    internal fun provide(
        serverConfig: ServerConfig,
        proxyCredentials: ProxyCredentials?,
        serverConfigRepository: ServerConfigRepository,
        networkStateObserver: NetworkStateObserver,
        certConfig: () -> CertificatePinning,
        accessRepository: AccessRepository
    ): AuthenticationScope =
        authenticationScopeStorage.computeIfAbsent(serverConfig to proxyCredentials) {
            AuthenticationScope(
                userAgent,
                serverConfig,
                proxyCredentials,
                serverConfigRepository,
                networkStateObserver,
                certConfig,
                accessRepository
            )
        }
}

class AuthenticationScope internal constructor(
    private val userAgent: String,
    private val serverConfig: ServerConfig,
    private val proxyCredentials: ProxyCredentials?,
    private val serverConfigRepository: ServerConfigRepository,
    private val networkStateObserver: NetworkStateObserver,
    certConfig: () -> CertificatePinning,
    private val accessRepository: AccessRepository
) {
    private val unauthenticatedNetworkContainer: UnauthenticatedNetworkContainer by lazy {
        UnauthenticatedNetworkContainer.create(
            networkStateObserver,
            MapperProvider.serverConfigMapper().toDTO(serverConfig),
            proxyCredentials?.let { MapperProvider.sessionMapper().fromModelToProxyCredentialsDTO(it) },
            userAgent,
            certificatePinning = certConfig()
        )
    }

    private val loginRepository: LoginRepository
        get() = LoginRepositoryImpl(unauthenticatedNetworkContainer.loginApi)

    private val registerAccountRepository: RegisterAccountRepository
        get() = RegisterAccountDataSource(
            unauthenticatedNetworkContainer.registerApi
        )
    internal val ssoLoginRepository: SSOLoginRepository
        get() = SSOLoginRepositoryImpl(unauthenticatedNetworkContainer.sso, unauthenticatedNetworkContainer.domainLookupApi)

    internal val secondFactorVerificationRepository: SecondFactorVerificationRepository =
        SecondFactorVerificationRepositoryImpl(unauthenticatedNetworkContainer.verificationCodeApi)

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
            proxyCredentials,
            secondFactorVerificationRepository,
            accessRepository
        )

    val isUserLoggedInUseCase: IsUserLoggedInUseCase
        get() = IsUserLoggedInUseCaseImpl(accessRepository)

    val requestSecondFactorVerificationCode: RequestSecondFactorVerificationCodeUseCase
        get() = RequestSecondFactorVerificationCodeUseCase(secondFactorVerificationRepository)
    val registerScope: RegisterScope
        get() = RegisterScope(registerAccountRepository, serverConfig, proxyCredentials)
    val ssoLoginScope: SSOLoginScope
        get() = SSOLoginScope(ssoLoginRepository, serverConfig, proxyCredentials)
    val checkIfUpdateRequired: CheckIfUpdateRequiredUseCase
        get() = CheckIfUpdateRequiredUseCaseImpl(appVersionRepository)

    val domainLookup: DomainLookupUseCase
        get() = DomainLookupUseCase(
            serverConfigRepository = serverConfigRepository,
            ssoLoginRepository = ssoLoginRepository
        )
}
