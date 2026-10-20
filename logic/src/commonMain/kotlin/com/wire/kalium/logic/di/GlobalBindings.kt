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

@file:OptIn(InternalKaliumApi::class)

package com.wire.kalium.logic.di

import com.wire.kalium.logic.feature.session.SessionGraph
import com.wire.kalium.logic.GlobalKaliumScope
import com.wire.kalium.logic.configuration.notification.NotificationTokenDataSource
import com.wire.kalium.logic.configuration.notification.NotificationTokenRepository
import com.wire.kalium.logic.configuration.server.CustomServerConfigDataSource
import com.wire.kalium.logic.configuration.server.CustomServerConfigRepository
import com.wire.kalium.logic.data.client.UserClientRepositoryProvider
import com.wire.kalium.logic.data.client.UserClientRepositoryProviderImpl
import com.wire.kalium.logic.data.session.SessionDataSource
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.feature.UserSessionScopeProvider
import com.wire.kalium.logic.feature.appVersioning.ObserveIfAppUpdateRequiredUseCase
import com.wire.kalium.logic.feature.appVersioning.ObserveIfAppUpdateRequiredUseCaseImpl
import com.wire.kalium.logic.feature.asset.AudioNormalizedLoudnessBuilder
import com.wire.kalium.logic.feature.auth.AddAuthenticatedUserUseCase
import com.wire.kalium.logic.feature.auth.AuthenticationScopeProvider
import com.wire.kalium.logic.feature.auth.LogoutCallbackManager
import com.wire.kalium.logic.feature.auth.ObserveLoginContextUseCase
import com.wire.kalium.logic.feature.auth.ValidateEmailUseCase
import com.wire.kalium.logic.feature.auth.ValidateEmailUseCaseImpl
import com.wire.kalium.logic.feature.auth.ValidatePasswordUseCase
import com.wire.kalium.logic.feature.auth.ValidatePasswordUseCaseImpl
import com.wire.kalium.logic.feature.auth.ValidateUserHandleUseCase
import com.wire.kalium.logic.feature.auth.ValidateUserHandleUseCaseImpl
import com.wire.kalium.logic.feature.auth.autoVersioningAuth.AuthenticationScopeForConfigIdUseCase
import com.wire.kalium.logic.feature.auth.sso.ValidateSSOCodeUseCase
import com.wire.kalium.logic.feature.auth.sso.ValidateSSOCodeUseCaseImpl
import com.wire.kalium.logic.feature.client.ClearNewClientsForUserUseCase
import com.wire.kalium.logic.feature.client.ClearNewClientsForUserUseCaseImpl
import com.wire.kalium.logic.feature.client.ObserveNewClientsUseCase
import com.wire.kalium.logic.feature.client.ObserveNewClientsUseCaseImpl
import com.wire.kalium.logic.feature.featureConfig.ObserveIsAppLockEditableUseCase
import com.wire.kalium.logic.feature.featureConfig.ObserveIsAppLockEditableUseCaseImpl
import com.wire.kalium.logic.feature.notificationToken.SaveNotificationTokenUseCase
import com.wire.kalium.logic.feature.notificationToken.SaveNotificationTokenUseCaseImpl
import com.wire.kalium.logic.feature.server.GetServerConfigUseCase
import com.wire.kalium.logic.feature.server.IsCrossBackendLoginBlockedUseCase
import com.wire.kalium.logic.feature.server.ServerConfigForAccountUseCase
import com.wire.kalium.logic.feature.server.UpdateApiVersionsUseCase
import com.wire.kalium.logic.feature.server.UpdateApiVersionsUseCaseImpl
import com.wire.kalium.logic.feature.session.DeleteSessionUseCase
import com.wire.kalium.logic.feature.session.DoesValidNomadAccountExistUseCase
import com.wire.kalium.logic.feature.session.DoesValidSessionExistUseCase
import com.wire.kalium.logic.feature.session.GetAllSessionsUseCase
import com.wire.kalium.logic.feature.session.GetSessionsUseCase
import com.wire.kalium.logic.feature.session.IsCurrentSessionNomadAccountUseCase
import com.wire.kalium.logic.feature.session.ObserveSessionsUseCase
import com.wire.kalium.logic.feature.session.SessionScope
import com.wire.kalium.logic.feature.user.ObserveValidAccountsUseCase
import com.wire.kalium.logic.feature.user.ObserveValidAccountsUseCaseImpl
import com.wire.kalium.logic.feature.user.webSocketStatus.ObservePersistentWebSocketConnectionStatusUseCase
import com.wire.kalium.logic.feature.user.webSocketStatus.ObservePersistentWebSocketConnectionStatusUseCaseImpl
import com.wire.kalium.logic.feature.user.webSocketStatus.SetPersistentWebSocketForAllUsersUseCase
import com.wire.kalium.logic.feature.user.webSocketStatus.SetPersistentWebSocketForAllUsersUseCaseImpl
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.sync.GlobalWorkScheduler
import com.wire.kalium.logic.sync.WorkSchedulerProvider
import com.wire.kalium.logic.sync.periodic.UpdateApiVersionsScheduler
import com.wire.kalium.logic.sync.periodic.UpdateApiVersionsWorker
import com.wire.kalium.network.networkContainer.UnboundNetworkContainer
import com.wire.kalium.network.networkContainer.UnboundNetworkContainerCommon
import com.wire.kalium.network.utils.MockUnboundNetworkClient
import com.wire.kalium.persistence.db.GlobalDatabaseBuilder
import com.wire.kalium.persistence.kmmSettings.GlobalPrefProvider
import com.wire.kalium.util.InternalKaliumApi
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.Qualifier
import dev.zacsweers.metro.SingleIn

@Qualifier
internal annotation class GlobalBinding(val value: String)

/** External instances and configuration retained by the existing CoreLogic owner. */
internal interface GlobalGraphInputs {
    val userAgent: String
    val globalDatabase: GlobalDatabaseBuilder
    val globalPreferences: GlobalPrefProvider
    val kaliumConfigs: KaliumConfigs
    val userSessionScopeProvider: Lazy<UserSessionScopeProvider>
    val authenticationScopeProvider: AuthenticationScopeProvider
    val logoutCallbackManager: LogoutCallbackManager
    val workSchedulerProvider: WorkSchedulerProvider
    val audioNormalizedLoudnessBuilder: AudioNormalizedLoudnessBuilder
}

/** The existing facade is the coroutine/scheduler owner; resolving it never constructs another graph. */
internal fun interface GlobalGraphOwner {
    fun scope(): GlobalKaliumScope
}

internal interface GlobalApi {
    @get:GlobalBinding("audioNormalizedLoudnessBuilder")
    val globalAudioNormalizedLoudnessBuilder: AudioNormalizedLoudnessBuilder

    @get:GlobalBinding("workSchedulerProvider")
    val globalWorkSchedulerProvider: WorkSchedulerProvider

    @get:GlobalBinding("logoutCallbackManager")
    val globalLogoutCallbackManager: LogoutCallbackManager

    @get:GlobalBinding("unboundNetworkContainer")
    val globalUnboundNetworkContainer: UnboundNetworkContainer

    @get:GlobalBinding("sessionRepository")
    val globalSessionRepository: SessionRepository

    @get:GlobalBinding("observePersistentWebSocketConnectionStatus")
    val globalObservePersistentWebSocketConnectionStatus: ObservePersistentWebSocketConnectionStatusUseCase

    @get:GlobalBinding("setAllPersistentWebSocketEnabled")
    val globalSetAllPersistentWebSocketEnabled: SetPersistentWebSocketForAllUsersUseCase

    @get:GlobalBinding("notificationTokenRepository")
    val globalNotificationTokenRepository: NotificationTokenRepository

    @get:GlobalBinding("customServerConfigRepository")
    val globalCustomServerConfigRepository: CustomServerConfigRepository

    @get:GlobalBinding("validateEmailUseCase")
    val globalValidateEmailUseCase: ValidateEmailUseCase

    @get:GlobalBinding("validateSSOCodeUseCase")
    val globalValidateSSOCodeUseCase: ValidateSSOCodeUseCase

    @get:GlobalBinding("validateUserHandleUseCase")
    val globalValidateUserHandleUseCase: ValidateUserHandleUseCase

    @get:GlobalBinding("validatePasswordUseCase")
    val globalValidatePasswordUseCase: ValidatePasswordUseCase

    @get:GlobalBinding("observeLoginContext")
    val globalObserveLoginContext: ObserveLoginContextUseCase

    @get:GlobalBinding("addAuthenticatedAccount")
    val globalAddAuthenticatedAccount: AddAuthenticatedUserUseCase

    @get:GlobalBinding("getSessions")
    val globalGetSessions: GetSessionsUseCase

    @get:GlobalBinding("getAllSessions")
    val globalGetAllSessions: GetAllSessionsUseCase

    @get:GlobalBinding("doesValidSessionExist")
    val globalDoesValidSessionExist: DoesValidSessionExistUseCase

    @get:GlobalBinding("doesValidNomadAccountExist")
    val globalDoesValidNomadAccountExist: DoesValidNomadAccountExistUseCase

    @get:GlobalBinding("isCurrentSessionNomadAccount")
    val globalIsCurrentSessionNomadAccount: IsCurrentSessionNomadAccountUseCase

    @get:GlobalBinding("observeValidAccounts")
    val globalObserveValidAccounts: ObserveValidAccountsUseCase

    @get:GlobalBinding("observeAllValidSessionsFlow")
    val globalObserveAllValidSessionsFlow: ObserveSessionsUseCase

    @get:GlobalBinding("session")
    val globalSession: SessionScope

    @get:GlobalBinding("fetchServerConfigFromDeepLink")
    val globalFetchServerConfigFromDeepLink: GetServerConfigUseCase

    @get:GlobalBinding("updateApiVersions")
    val globalUpdateApiVersions: UpdateApiVersionsUseCase

    @get:GlobalBinding("saveNotificationToken")
    val globalSaveNotificationToken: SaveNotificationTokenUseCase

    @get:GlobalBinding("deleteSession")
    val globalDeleteSession: DeleteSessionUseCase

    @get:GlobalBinding("serverConfigForAccounts")
    val globalServerConfigForAccounts: ServerConfigForAccountUseCase

    @get:GlobalBinding("isCrossBackendLoginBlocked")
    val globalIsCrossBackendLoginBlocked: IsCrossBackendLoginBlockedUseCase

    @get:GlobalBinding("authenticationScopeForConfigId")
    val globalAuthenticationScopeForConfigId: AuthenticationScopeForConfigIdUseCase

    @get:GlobalBinding("observeIfAppUpdateRequired")
    val globalObserveIfAppUpdateRequired: ObserveIfAppUpdateRequiredUseCase

    @get:GlobalBinding("userClientRepositoryProvider")
    val globalUserClientRepositoryProvider: UserClientRepositoryProvider

    @get:GlobalBinding("observeNewClientsUseCase")
    val globalObserveNewClientsUseCase: ObserveNewClientsUseCase

    @get:GlobalBinding("clearNewClientsForUser")
    val globalClearNewClientsForUser: ClearNewClientsForUserUseCase

    @get:GlobalBinding("observeIsAppLockEditableUseCase")
    val globalObserveIsAppLockEditableUseCase: ObserveIsAppLockEditableUseCase

    @get:GlobalBinding("updateApiVersionsWorker")
    val globalUpdateApiVersionsWorker: UpdateApiVersionsWorker

    @get:GlobalBinding("globalWorkScheduler")
    val globalGlobalWorkScheduler: GlobalWorkScheduler

    @get:GlobalBinding("updateApiVersionsScheduler")
    val globalUpdateApiVersionsScheduler: UpdateApiVersionsScheduler
}

/** Individual global bindings; provider caches retain their separate GLOBAL/LOCAL policy. */
@Suppress("TooManyFunctions", "LargeClass")
internal interface GlobalBindings : GlobalApi {
    @Provides
    @GlobalBinding("audioNormalizedLoudnessBuilder")
    fun provideGlobalAudioNormalizedLoudnessBuilder(
        inputs: GlobalGraphInputs,
    ): AudioNormalizedLoudnessBuilder =
        inputs.audioNormalizedLoudnessBuilder

    @Provides
    @GlobalBinding("workSchedulerProvider")
    fun provideGlobalWorkSchedulerProvider(
        inputs: GlobalGraphInputs,
    ): WorkSchedulerProvider =
        inputs.workSchedulerProvider

    @Provides
    @GlobalBinding("logoutCallbackManager")
    fun provideGlobalLogoutCallbackManager(
        inputs: GlobalGraphInputs,
    ): LogoutCallbackManager =
        inputs.logoutCallbackManager

    @Provides
    @SingleIn(KaliumLifetime::class)
    @GlobalBinding("unboundNetworkContainer")
    fun provideGlobalUnboundNetworkContainer(
        inputs: GlobalGraphInputs,
    ): UnboundNetworkContainer {
        return UnboundNetworkContainerCommon(
            inputs.userAgent,
            inputs.kaliumConfigs.ignoreSSLCertificatesForUnboundCalls,
            inputs.kaliumConfigs.certPinningConfig,
            inputs.kaliumConfigs.mockedRequests?.let { MockUnboundNetworkClient.createMockEngine(it) },
            inputs.kaliumConfigs.developmentApiEnabled,
            httpTrafficObserver = inputs.kaliumConfigs.httpTrafficObserver,
        )
    }

    @Provides
    @GlobalBinding("sessionRepository")
    fun provideGlobalSessionRepository(
        inputs: GlobalGraphInputs,
    ): SessionRepository =
        SessionDataSource(
            inputs.globalDatabase.accountsDAO,
            inputs.globalPreferences.authTokenStorage,
            inputs.globalDatabase.serverConfigurationDAO
        )

    @Provides
    @GlobalBinding("observePersistentWebSocketConnectionStatus")
    fun provideGlobalObservePersistentWebSocketConnectionStatus(
        @GlobalBinding("sessionRepository") sessionRepository: SessionRepository,
    ): ObservePersistentWebSocketConnectionStatusUseCase =
        ObservePersistentWebSocketConnectionStatusUseCaseImpl(sessionRepository)

    @Provides
    @GlobalBinding("setAllPersistentWebSocketEnabled")
    fun provideGlobalSetAllPersistentWebSocketEnabled(
        @GlobalBinding("sessionRepository") sessionRepository: SessionRepository,
    ): SetPersistentWebSocketForAllUsersUseCase =
        SetPersistentWebSocketForAllUsersUseCaseImpl(sessionRepository)

    @Provides
    @GlobalBinding("notificationTokenRepository")
    fun provideGlobalNotificationTokenRepository(
        inputs: GlobalGraphInputs,
    ): NotificationTokenRepository =
        NotificationTokenDataSource(inputs.globalPreferences.tokenStorage)

    @Provides
    @GlobalBinding("customServerConfigRepository")
    fun provideGlobalCustomServerConfigRepository(
        inputs: GlobalGraphInputs,
        @GlobalBinding("unboundNetworkContainer") unboundNetworkContainer: UnboundNetworkContainer,
    ): CustomServerConfigRepository =
        CustomServerConfigDataSource(
            unboundNetworkContainer.versionApi,
            unboundNetworkContainer.serverConfigApi,
            developmentApiEnabled = inputs.kaliumConfigs.developmentApiEnabled,
            inputs.globalDatabase.serverConfigurationDAO
        )

    @Provides
    @GlobalBinding("validateEmailUseCase")
    fun provideGlobalValidateEmailUseCase(): ValidateEmailUseCase =
        ValidateEmailUseCaseImpl()

    @Provides
    @GlobalBinding("validateSSOCodeUseCase")
    fun provideGlobalValidateSSOCodeUseCase(): ValidateSSOCodeUseCase =
        ValidateSSOCodeUseCaseImpl()

    @Provides
    @GlobalBinding("validateUserHandleUseCase")
    fun provideGlobalValidateUserHandleUseCase(): ValidateUserHandleUseCase =
        ValidateUserHandleUseCaseImpl()

    @Provides
    @GlobalBinding("validatePasswordUseCase")
    fun provideGlobalValidatePasswordUseCase(): ValidatePasswordUseCase =
        ValidatePasswordUseCaseImpl()

    @Provides
    @GlobalBinding("observeLoginContext")
    fun provideGlobalObserveLoginContext(
        @GlobalBinding("customServerConfigRepository") customServerConfigRepository: CustomServerConfigRepository,
    ): ObserveLoginContextUseCase =
        ObserveLoginContextUseCase(customServerConfigRepository)

    @Provides
    @GlobalBinding("addAuthenticatedAccount")
    fun provideGlobalAddAuthenticatedAccount(
        inputs: GlobalGraphInputs,
        @GlobalBinding("sessionRepository") sessionRepository: SessionRepository,
    ): AddAuthenticatedUserUseCase =
        AddAuthenticatedUserUseCase(sessionRepository, inputs.globalDatabase.serverConfigurationDAO, inputs.userSessionScopeProvider.value)

    @Provides
    @GlobalBinding("getSessions")
    fun provideGlobalGetSessions(
        @GlobalBinding("sessionRepository") sessionRepository: SessionRepository,
    ): GetSessionsUseCase =
        GetSessionsUseCase(sessionRepository)

    @Provides
    @GlobalBinding("getAllSessions")
    fun provideGlobalGetAllSessions(
        @GlobalBinding("sessionRepository") sessionRepository: SessionRepository,
    ): GetAllSessionsUseCase =
        GetAllSessionsUseCase(sessionRepository)

    @Provides
    @GlobalBinding("doesValidSessionExist")
    fun provideGlobalDoesValidSessionExist(
        @GlobalBinding("sessionRepository") sessionRepository: SessionRepository,
    ): DoesValidSessionExistUseCase =
        DoesValidSessionExistUseCase(sessionRepository)

    @Provides
    @GlobalBinding("doesValidNomadAccountExist")
    fun provideGlobalDoesValidNomadAccountExist(
        @GlobalBinding("sessionRepository") sessionRepository: SessionRepository,
    ): DoesValidNomadAccountExistUseCase =
        DoesValidNomadAccountExistUseCase(sessionRepository)

    @Provides
    @GlobalBinding("isCurrentSessionNomadAccount")
    fun provideGlobalIsCurrentSessionNomadAccount(
        @GlobalBinding("sessionRepository") sessionRepository: SessionRepository,
    ): IsCurrentSessionNomadAccountUseCase =
        IsCurrentSessionNomadAccountUseCase(
            sessionRepository
        )

    @Provides
    @SingleIn(KaliumLifetime::class)
    @GlobalBinding("observeValidAccounts")
    fun provideGlobalObserveValidAccounts(
        inputs: GlobalGraphInputs,
        @GlobalBinding("sessionRepository") sessionRepository: SessionRepository,
    ): ObserveValidAccountsUseCase {
        return ObserveValidAccountsUseCaseImpl(
            sessionRepository,
            inputs.userSessionScopeProvider.value
        )
    }

    @Provides
    @SingleIn(KaliumLifetime::class)
    @GlobalBinding("observeAllValidSessionsFlow")
    fun provideGlobalObserveAllValidSessionsFlow(
        @GlobalBinding("sessionRepository") sessionRepository: SessionRepository,
    ): ObserveSessionsUseCase {
        return ObserveSessionsUseCase(sessionRepository)
    }

    @Provides
    @GlobalBinding("session")
    fun provideGlobalSession(
        @GlobalBinding("sessionRepository") sessionRepository: SessionRepository,
        factory: SessionGraph.Factory,
    ): SessionScope =
        SessionScope(factory.create(sessionRepository))

    @Provides
    @GlobalBinding("fetchServerConfigFromDeepLink")
    fun provideGlobalFetchServerConfigFromDeepLink(
        @GlobalBinding("customServerConfigRepository") customServerConfigRepository: CustomServerConfigRepository,
    ): GetServerConfigUseCase =
        GetServerConfigUseCase(customServerConfigRepository)

    @Provides
    @GlobalBinding("updateApiVersions")
    fun provideGlobalUpdateApiVersions(
        inputs: GlobalGraphInputs,
        @GlobalBinding("sessionRepository") sessionRepository: SessionRepository,
    ): UpdateApiVersionsUseCase =
        UpdateApiVersionsUseCaseImpl(
            sessionRepository,
            inputs.globalPreferences.authTokenStorage,
            { serverConfig, proxyCredentials ->
                inputs.authenticationScopeProvider.provide(
                    serverConfig = serverConfig,
                    proxyCredentials = proxyCredentials,
                    globalDatabase = inputs.globalDatabase,
                    kaliumConfigs = inputs.kaliumConfigs,
                ).serverConfigRepository
            },
        )

    @Provides
    @GlobalBinding("saveNotificationToken")
    fun provideGlobalSaveNotificationToken(
        inputs: GlobalGraphInputs,
        @GlobalBinding("notificationTokenRepository") notificationTokenRepository: NotificationTokenRepository,
        @GlobalBinding("observeValidAccounts") observeValidAccounts: ObserveValidAccountsUseCase,
    ): SaveNotificationTokenUseCase =
        SaveNotificationTokenUseCaseImpl(
            notificationTokenRepository,
            observeValidAccounts,
            inputs.userSessionScopeProvider.value
        )

    @Provides
    @GlobalBinding("deleteSession")
    fun provideGlobalDeleteSession(
        inputs: GlobalGraphInputs,
        @GlobalBinding("sessionRepository") sessionRepository: SessionRepository,
    ): DeleteSessionUseCase =
        DeleteSessionUseCase(sessionRepository, inputs.userSessionScopeProvider.value)

    @Provides
    @GlobalBinding("serverConfigForAccounts")
    fun provideGlobalServerConfigForAccounts(
        inputs: GlobalGraphInputs,
    ): ServerConfigForAccountUseCase =
        ServerConfigForAccountUseCase(inputs.globalDatabase.serverConfigurationDAO)

    @Provides
    @GlobalBinding("isCrossBackendLoginBlocked")
    fun provideGlobalIsCrossBackendLoginBlocked(
        inputs: GlobalGraphInputs,
        @GlobalBinding("session") session: SessionScope,
        @GlobalBinding("serverConfigForAccounts") serverConfigForAccounts: ServerConfigForAccountUseCase,
    ): IsCrossBackendLoginBlockedUseCase {
        val currentSessionUseCase = session.currentSession
        val serverConfigForAccountUseCase = serverConfigForAccounts
        return IsCrossBackendLoginBlockedUseCase(
            kaliumConfigs = inputs.kaliumConfigs,
            currentSession = { currentSessionUseCase() },
            serverConfigForAccount = { userId -> serverConfigForAccountUseCase(userId) },
        )
    }

    @Provides
    @GlobalBinding("authenticationScopeForConfigId")
    fun provideGlobalAuthenticationScopeForConfigId(
        inputs: GlobalGraphInputs,
    ): AuthenticationScopeForConfigIdUseCase =
        AuthenticationScopeForConfigIdUseCase(
            serverConfigurationDAO = inputs.globalDatabase.serverConfigurationDAO,
            serverConfigMapper = MapperProvider.serverConfigMapper(),
            authenticationScopeFactory = { serverConfig ->
                inputs.authenticationScopeProvider.provide(
                    serverConfig = serverConfig,
                    proxyCredentials = null,
                    globalDatabase = inputs.globalDatabase,
                    kaliumConfigs = inputs.kaliumConfigs,
                )
            }
        )

    @Provides
    @GlobalBinding("observeIfAppUpdateRequired")
    fun provideGlobalObserveIfAppUpdateRequired(
        inputs: GlobalGraphInputs,
        @GlobalBinding("customServerConfigRepository") customServerConfigRepository: CustomServerConfigRepository,
    ): ObserveIfAppUpdateRequiredUseCase =
        ObserveIfAppUpdateRequiredUseCaseImpl(
            customServerConfigRepository,
            inputs.authenticationScopeProvider,
            inputs.userSessionScopeProvider.value,
            inputs.globalDatabase,
            inputs.kaliumConfigs
        )

    @Provides
    @GlobalBinding("userClientRepositoryProvider")
    fun provideGlobalUserClientRepositoryProvider(
        inputs: GlobalGraphInputs,
    ): UserClientRepositoryProvider =
        UserClientRepositoryProviderImpl(inputs.userSessionScopeProvider.value)

    @Provides
    @GlobalBinding("observeNewClientsUseCase")
    fun provideGlobalObserveNewClientsUseCase(
        @GlobalBinding("sessionRepository") sessionRepository: SessionRepository,
        @GlobalBinding("observeValidAccounts") observeValidAccounts: ObserveValidAccountsUseCase,
        @GlobalBinding("userClientRepositoryProvider") userClientRepositoryProvider: UserClientRepositoryProvider,
    ): ObserveNewClientsUseCase =
        ObserveNewClientsUseCaseImpl(sessionRepository, observeValidAccounts, userClientRepositoryProvider)

    @Provides
    @GlobalBinding("clearNewClientsForUser")
    fun provideGlobalClearNewClientsForUser(
        inputs: GlobalGraphInputs,
    ): ClearNewClientsForUserUseCase =
        ClearNewClientsForUserUseCaseImpl(inputs.userSessionScopeProvider.value)

    @Provides
    @GlobalBinding("observeIsAppLockEditableUseCase")
    fun provideGlobalObserveIsAppLockEditableUseCase(
        inputs: GlobalGraphInputs,
        @GlobalBinding("sessionRepository") sessionRepository: SessionRepository,
    ): ObserveIsAppLockEditableUseCase =
        ObserveIsAppLockEditableUseCaseImpl(inputs.userSessionScopeProvider.value, sessionRepository)

    @Provides
    @SingleIn(KaliumLifetime::class)
    @GlobalBinding("updateApiVersionsWorker")
    fun provideGlobalUpdateApiVersionsWorker(
        @GlobalBinding("updateApiVersions") updateApiVersions: UpdateApiVersionsUseCase,
    ): UpdateApiVersionsWorker {
        return UpdateApiVersionsWorker(updateApiVersions)
    }

    @Provides
    @SingleIn(KaliumLifetime::class)
    @GlobalBinding("globalWorkScheduler")
    fun provideGlobalGlobalWorkScheduler(
        owner: GlobalGraphOwner,
        @GlobalBinding("workSchedulerProvider") workSchedulerProvider: WorkSchedulerProvider,
    ): GlobalWorkScheduler {
        return workSchedulerProvider.globalWorkScheduler(owner.scope())
    }

    @Provides
    @GlobalBinding("updateApiVersionsScheduler")
    fun provideGlobalUpdateApiVersionsScheduler(
        @GlobalBinding("globalWorkScheduler") globalWorkScheduler: GlobalWorkScheduler,
    ): UpdateApiVersionsScheduler =
        globalWorkScheduler
}
