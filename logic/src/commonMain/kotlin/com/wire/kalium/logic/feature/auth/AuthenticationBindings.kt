/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
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

@file:Suppress("konsist.useCasesShouldNotAccessDaoLayerDirectly", "konsist.useCasesShouldNotAccessNetworkLayerDirectly")
@file:OptIn(InternalKaliumApi::class)

package com.wire.kalium.logic.feature.auth

import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.configuration.appVersioning.AppVersionRepository
import com.wire.kalium.logic.configuration.appVersioning.AppVersionRepositoryImpl
import com.wire.kalium.logic.configuration.server.CustomServerConfigDataSource
import com.wire.kalium.logic.configuration.server.CustomServerConfigRepository
import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.configuration.server.ServerConfigDataSource
import com.wire.kalium.logic.configuration.server.ServerConfigRepository
import com.wire.kalium.logic.data.auth.login.LoginRepository
import com.wire.kalium.logic.data.auth.login.LoginRepositoryImpl
import com.wire.kalium.logic.data.auth.login.ProxyCredentials
import com.wire.kalium.logic.data.auth.login.SSOLoginRepository
import com.wire.kalium.logic.data.auth.login.SSOLoginRepositoryImpl
import com.wire.kalium.logic.data.auth.settings.PendingLoginSystemSettingsRepository
import com.wire.kalium.logic.data.auth.settings.PendingLoginSystemSettingsRepositoryImpl
import com.wire.kalium.logic.data.auth.settings.UnauthorizedSettingsRepository
import com.wire.kalium.logic.data.auth.settings.UnauthorizedSettingsRepositoryImpl
import com.wire.kalium.logic.data.auth.verification.SecondFactorVerificationRepository
import com.wire.kalium.logic.data.auth.verification.SecondFactorVerificationRepositoryImpl
import com.wire.kalium.logic.data.register.RegisterAccountDataSource
import com.wire.kalium.logic.data.register.RegisterAccountRepository
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.feature.appVersioning.CheckIfUpdateRequiredUseCase
import com.wire.kalium.logic.feature.appVersioning.CheckIfUpdateRequiredUseCaseImpl
import com.wire.kalium.logic.feature.auth.sso.SSOLoginGraph
import com.wire.kalium.logic.feature.auth.sso.SSOLoginGraphCapture
import com.wire.kalium.logic.feature.auth.sso.SSOLoginScope
import com.wire.kalium.logic.feature.auth.verification.RequestSecondFactorVerificationCodeUseCase
import com.wire.kalium.logic.feature.register.RegisterGraph
import com.wire.kalium.logic.feature.register.RegisterGraphCapture
import com.wire.kalium.logic.feature.register.RegisterScope
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.network.networkContainer.TransientAuthenticatedNetworkContainer
import com.wire.kalium.network.networkContainer.UnauthenticatedNetworkContainer
import com.wire.kalium.network.utils.MockUnboundNetworkClient
import com.wire.kalium.persistence.db.GlobalDatabaseBuilder
import com.wire.kalium.util.InternalKaliumApi
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

/** Authentication is independent of global runtime startup and authenticated account ownership. */
internal abstract class AuthenticationLifetime private constructor()

internal interface AuthenticationGraphInputs {
    val userAgent: String
    val serverConfig: ServerConfig
    val proxyCredentials: ProxyCredentials?
    val globalDatabase: GlobalDatabaseBuilder
    val kaliumConfigs: KaliumConfigs
}

/** Immutable external values retained by one cache entry; this opens no resources. */
internal class AuthenticationGraphInputValues(
    override val userAgent: String,
    override val serverConfig: ServerConfig,
    override val proxyCredentials: ProxyCredentials?,
    override val globalDatabase: GlobalDatabaseBuilder,
    override val kaliumConfigs: KaliumConfigs,
) : AuthenticationGraphInputs

internal interface AuthenticationApi {
    val serverConfig: ServerConfig
    val serverConfigRepository: ServerConfigRepository
    val ssoLoginRepository: SSOLoginRepository
    val secondFactorVerificationRepository: SecondFactorVerificationRepository
    val getLoginFlowForDomainUseCase: GetLoginFlowForDomainUseCase
    val login: LoginUseCase
    val requestSecondFactorVerificationCode: RequestSecondFactorVerificationCodeUseCase
    val registerScope: RegisterScope
    val ssoLoginScope: SSOLoginScope
    val checkIfUpdateRequired: CheckIfUpdateRequiredUseCase
    val domainLookup: DomainLookupUseCase
    val isNomadProfilesEnabled: IsNomadProfilesEnabledUseCase
}

/**
 * All bindings and factories are common-only, so this root needs no platform aggregation.
 * AuthenticationScopeProvider creates this graph inside computeIfAbsent and retains it through
 * its cached facade. It never opens GlobalKaliumScope or an authenticated user graph.
 */
@DependencyGraph(AuthenticationLifetime::class)
internal interface AuthenticationGraph : AuthenticationApi, AuthenticationBindings {
    val registerGraphFactory: RegisterGraph.Factory
    val ssoLoginGraphFactory: SSOLoginGraph.Factory

    @DependencyGraph.Factory
    interface Factory {
        fun create(@Provides inputs: AuthenticationGraphInputs): AuthenticationGraph
    }
}

/** Only network and mutable 2FA storage are retained; the former getter bindings stay fresh. */
@Suppress("TooManyFunctions")
internal interface AuthenticationBindings {
    @Provides
    fun provideServerConfig(inputs: AuthenticationGraphInputs): ServerConfig = inputs.serverConfig

    @Provides
    @SingleIn(AuthenticationLifetime::class)
    fun provideUnauthenticatedNetworkContainer(inputs: AuthenticationGraphInputs): UnauthenticatedNetworkContainer =
        UnauthenticatedNetworkContainer.create(
            serverConfigDTO = MapperProvider.serverConfigMapper().toDTO(inputs.serverConfig),
            proxyCredentials = inputs.proxyCredentials?.let { MapperProvider.sessionMapper().fromModelToProxyCredentialsDTO(it) },
            userAgent = inputs.userAgent,
            developmentApiEnabled = inputs.kaliumConfigs.developmentApiEnabled,
            certificatePinning = inputs.kaliumConfigs.certPinningConfig,
            mockEngine = inputs.kaliumConfigs.mockedRequests?.let { MockUnboundNetworkClient.createMockEngine(it) },
            httpTrafficObserver = inputs.kaliumConfigs.httpTrafficObserver,
        )

    @Provides
    fun provideServerConfigRepository(
        inputs: AuthenticationGraphInputs,
        network: UnauthenticatedNetworkContainer,
    ): ServerConfigRepository = ServerConfigDataSource(inputs.globalDatabase.serverConfigurationDAO, network.remoteVersion)

    @Provides
    fun provideLoginRepository(network: UnauthenticatedNetworkContainer): LoginRepository =
        LoginRepositoryImpl(network.loginApi, network.getDomainRegistrationApi)

    @Provides
    fun provideRegisterAccountRepository(network: UnauthenticatedNetworkContainer): RegisterAccountRepository =
        RegisterAccountDataSource(network.registerApi)

    @Provides
    fun provideCustomServerConfigRepository(
        inputs: AuthenticationGraphInputs,
        network: UnauthenticatedNetworkContainer,
    ): CustomServerConfigRepository = CustomServerConfigDataSource(
        network.remoteVersion,
        network.serverConfigApi,
        inputs.kaliumConfigs.developmentApiEnabled,
        inputs.globalDatabase.serverConfigurationDAO,
    )

    @Provides
    fun provideSSOLoginRepository(network: UnauthenticatedNetworkContainer): SSOLoginRepository =
        SSOLoginRepositoryImpl(network.sso, network.domainLookupApi)

    @Provides
    @SingleIn(AuthenticationLifetime::class)
    fun provideSecondFactorVerificationRepository(network: UnauthenticatedNetworkContainer): SecondFactorVerificationRepository =
        SecondFactorVerificationRepositoryImpl(network.verificationCodeApi)

    @Provides
    fun provideValidateEmail(): ValidateEmailUseCase = ValidateEmailUseCaseImpl()

    @Provides
    fun provideValidateUserHandle(): ValidateUserHandleUseCase = ValidateUserHandleUseCaseImpl()

    @Provides
    fun provideAppVersionRepository(network: UnauthenticatedNetworkContainer): AppVersionRepository =
        AppVersionRepositoryImpl(network.appVersioningApi)

    @Provides
    fun provideUnauthorizedSettingsRepository(network: UnauthenticatedNetworkContainer): UnauthorizedSettingsRepository =
        UnauthorizedSettingsRepositoryImpl(network.unauthorizedSettingsApi)

    @Provides
    fun provideGetLoginFlowForDomain(
        loginRepository: LoginRepository,
        ssoLoginRepository: SSOLoginRepository,
        customServerConfigRepository: CustomServerConfigRepository,
    ): GetLoginFlowForDomainUseCase = GetLoginFlowForDomainUseCase(loginRepository, ssoLoginRepository, customServerConfigRepository)

    @Provides
    fun provideLogin(
        loginRepository: LoginRepository,
        validateEmail: ValidateEmailUseCase,
        validateUserHandle: ValidateUserHandleUseCase,
        inputs: AuthenticationGraphInputs,
        secondFactorVerificationRepository: SecondFactorVerificationRepository,
    ): LoginUseCase = LoginUseCaseImpl(
        loginRepository,
        validateEmail,
        validateUserHandle,
        inputs.serverConfig,
        inputs.proxyCredentials,
        secondFactorVerificationRepository,
    )

    @Provides
    fun provideRequestSecondFactorVerificationCode(
        repository: SecondFactorVerificationRepository,
    ): RequestSecondFactorVerificationCodeUseCase = RequestSecondFactorVerificationCodeUseCase(repository)

    @Provides
    fun provideRegisterScope(
        repository: RegisterAccountRepository,
        inputs: AuthenticationGraphInputs,
        factory: RegisterGraph.Factory,
    ): RegisterScope = RegisterScope(factory.create(RegisterGraphCapture(repository, inputs.serverConfig, inputs.proxyCredentials)))

    @Provides
    fun provideSSOLoginScope(
        repository: SSOLoginRepository,
        inputs: AuthenticationGraphInputs,
        pendingLoginSystemSettingsRepository: PendingLoginSystemSettingsRepository,
        factory: SSOLoginGraph.Factory,
    ): SSOLoginScope = SSOLoginScope(
        factory.create(SSOLoginGraphCapture(repository, inputs.serverConfig, inputs.proxyCredentials, pendingLoginSystemSettingsRepository))
    )

    @Provides
    fun providePendingLoginSystemSettingsRepository(inputs: AuthenticationGraphInputs): PendingLoginSystemSettingsRepository =
        PendingLoginSystemSettingsRepositoryImpl(
            containerFactory = { session ->
                // A dedicated container/engine per pending request. The repository closes it in
                // finally, including cancellation; resolving this binding opens no temporary network.
                TransientAuthenticatedNetworkContainer.create(
                    session = session,
                    serverConfigDTO = MapperProvider.serverConfigMapper().toDTO(inputs.serverConfig),
                    proxyCredentials = inputs.proxyCredentials?.let { MapperProvider.sessionMapper().fromModelToProxyCredentialsDTO(it) },
                    userAgent = inputs.userAgent,
                    certificatePinning = inputs.kaliumConfigs.certPinningConfig,
                    mockEngine = inputs.kaliumConfigs.mockedRequests?.let { MockUnboundNetworkClient.createMockEngine(it) },
                    kaliumLogger = kaliumLogger,
                    httpTrafficObserver = inputs.kaliumConfigs.httpTrafficObserver,
                )
            }
        )

    @Provides
    fun provideCheckIfUpdateRequired(repository: AppVersionRepository): CheckIfUpdateRequiredUseCase =
        CheckIfUpdateRequiredUseCaseImpl(repository)

    @Provides
    fun provideDomainLookup(
        customServerConfigRepository: CustomServerConfigRepository,
        ssoLoginRepository: SSOLoginRepository,
    ): DomainLookupUseCase = DomainLookupUseCase(customServerConfigRepository, ssoLoginRepository)

    @Provides
    fun provideIsNomadProfilesEnabled(repository: UnauthorizedSettingsRepository): IsNomadProfilesEnabledUseCase =
        IsNomadProfilesEnabledUseCase(repository)
}
