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

package com.wire.kalium.logic

import com.wire.kalium.logic.configuration.notification.NotificationTokenDataSource
import com.wire.kalium.logic.configuration.notification.NotificationTokenRepository
import com.wire.kalium.logic.configuration.server.ServerConfigDataSource
import com.wire.kalium.logic.configuration.server.ServerConfigRepository
import com.wire.kalium.logic.data.client.UserClientRepositoryProvider
import com.wire.kalium.logic.data.client.UserClientRepositoryProviderImpl
import com.wire.kalium.logic.data.session.SessionDataSource
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.feature.UserSessionScopeProvider
import com.wire.kalium.logic.feature.appVersioning.ObserveIfAppUpdateRequiredUseCase
import com.wire.kalium.logic.feature.appVersioning.ObserveIfAppUpdateRequiredUseCaseImpl
import com.wire.kalium.logic.feature.auth.AddAuthenticatedUserUseCase
import com.wire.kalium.logic.feature.auth.AuthenticationScopeProvider
import com.wire.kalium.logic.feature.auth.ValidateEmailUseCase
import com.wire.kalium.logic.feature.auth.ValidateEmailUseCaseImpl
import com.wire.kalium.logic.feature.auth.ValidatePasswordUseCase
import com.wire.kalium.logic.feature.auth.ValidatePasswordUseCaseImpl
import com.wire.kalium.logic.feature.auth.ValidateUserHandleUseCase
import com.wire.kalium.logic.feature.auth.ValidateUserHandleUseCaseImpl
import com.wire.kalium.logic.feature.client.ClearNewClientsForUserUseCase
import com.wire.kalium.logic.feature.client.ClearNewClientsForUserUseCaseImpl
import com.wire.kalium.logic.feature.client.ObserveNewClientsUseCase
import com.wire.kalium.logic.feature.client.ObserveNewClientsUseCaseImpl
import com.wire.kalium.logic.feature.notificationToken.SaveNotificationTokenUseCase
import com.wire.kalium.logic.feature.notificationToken.SaveNotificationTokenUseCaseImpl
import com.wire.kalium.logic.feature.rootDetection.CheckSystemIntegrityUseCase
import com.wire.kalium.logic.feature.rootDetection.CheckSystemIntegrityUseCaseImpl
import com.wire.kalium.logic.feature.rootDetection.RootDetectorImpl
import com.wire.kalium.logic.feature.server.FetchApiVersionUseCase
import com.wire.kalium.logic.feature.server.FetchApiVersionUseCaseImpl
import com.wire.kalium.logic.feature.server.GetServerConfigUseCase
import com.wire.kalium.logic.feature.server.ObserveServerConfigUseCase
import com.wire.kalium.logic.feature.server.ServerConfigForAccountUseCase
import com.wire.kalium.logic.feature.server.StoreServerConfigUseCase
import com.wire.kalium.logic.feature.server.StoreServerConfigUseCaseImpl
import com.wire.kalium.logic.feature.server.UpdateApiVersionsUseCase
import com.wire.kalium.logic.feature.server.UpdateApiVersionsUseCaseImpl
import com.wire.kalium.logic.feature.session.DeleteSessionUseCase
import com.wire.kalium.logic.feature.session.DoesValidSessionExistUseCase
import com.wire.kalium.logic.feature.session.GetSessionsUseCase
import com.wire.kalium.logic.feature.session.SessionScope
import com.wire.kalium.logic.feature.user.ObserveValidAccountsUseCase
import com.wire.kalium.logic.feature.user.ObserveValidAccountsUseCaseImpl
import com.wire.kalium.logic.feature.user.webSocketStatus.ObservePersistentWebSocketConnectionStatusUseCase
import com.wire.kalium.logic.feature.user.webSocketStatus.ObservePersistentWebSocketConnectionStatusUseCaseImpl
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.network.NetworkStateObserver
import com.wire.kalium.network.networkContainer.UnboundNetworkContainer
import com.wire.kalium.network.networkContainer.UnboundNetworkContainerCommon
import com.wire.kalium.persistence.db.GlobalDatabaseProvider
import com.wire.kalium.persistence.kmmSettings.GlobalPrefProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlin.coroutines.CoroutineContext

/**
 * Scope that exposes all operations that are user and backend agnostic, like
 * - Storing and retrieving sessions after authenticating
 * - Updating client or device metadata (like push notification token)
 * - Getting back-end information from a deeplink
 *
 * @see [com.wire.kalium.logic.feature.auth.AuthenticationScope]
 * @see [com.wire.kalium.logic.feature.UserSessionScope]
 */

class GlobalKaliumScope internal constructor(
    userAgent: String,
    private val globalDatabase: Lazy<GlobalDatabaseProvider>,
    private val globalPreferences: Lazy<GlobalPrefProvider>,
    private val kaliumConfigs: KaliumConfigs,
    private val userSessionScopeProvider: Lazy<UserSessionScopeProvider>,
    private val authenticationScopeProvider: AuthenticationScopeProvider,
    private  val networkStateObserver: NetworkStateObserver
) : CoroutineScope {

    override val coroutineContext: CoroutineContext = SupervisorJob()

    val unboundNetworkContainer: UnboundNetworkContainer by lazy {
        UnboundNetworkContainerCommon(
            networkStateObserver,
            kaliumConfigs.developmentApiEnabled,
            userAgent,
            kaliumConfigs.ignoreSSLCertificatesForUnboundCalls
        )
    }

    internal val serverConfigRepository: ServerConfigRepository
        get() = ServerConfigDataSource(
            unboundNetworkContainer.serverConfigApi,
            globalDatabase.value.serverConfigurationDAO,
            unboundNetworkContainer.remoteVersion,
            kaliumConfigs.developmentApiEnabled
        )

    val sessionRepository: SessionRepository
        get() = SessionDataSource(
            globalDatabase.value.accountsDAO,
            globalPreferences.value.authTokenStorage,
            serverConfigRepository,
            kaliumConfigs
        )

    val observePersistentWebSocketConnectionStatus: ObservePersistentWebSocketConnectionStatusUseCase
        get() = ObservePersistentWebSocketConnectionStatusUseCaseImpl(sessionRepository)

    private val notificationTokenRepository: NotificationTokenRepository
        get() =
            NotificationTokenDataSource(globalPreferences.value.tokenStorage)

    val validateEmailUseCase: ValidateEmailUseCase get() = ValidateEmailUseCaseImpl()
    val validateUserHandleUseCase: ValidateUserHandleUseCase get() = ValidateUserHandleUseCaseImpl()
    val validatePasswordUseCase: ValidatePasswordUseCase get() = ValidatePasswordUseCaseImpl()

    val addAuthenticatedAccount: AddAuthenticatedUserUseCase
        get() =
            AddAuthenticatedUserUseCase(sessionRepository, serverConfigRepository)
    val getSessions: GetSessionsUseCase get() = GetSessionsUseCase(sessionRepository)
    val doesValidSessionExist: DoesValidSessionExistUseCase get() = DoesValidSessionExistUseCase(sessionRepository)
    val observeValidAccounts: ObserveValidAccountsUseCase
        get() = ObserveValidAccountsUseCaseImpl(sessionRepository, userSessionScopeProvider.value)

    val session: SessionScope get() = SessionScope(sessionRepository)
    val fetchServerConfigFromDeepLink: GetServerConfigUseCase get() = GetServerConfigUseCase(serverConfigRepository)
    val fetchApiVersion: FetchApiVersionUseCase get() = FetchApiVersionUseCaseImpl(serverConfigRepository)
    val observeServerConfig: ObserveServerConfigUseCase get() = ObserveServerConfigUseCase(serverConfigRepository)
    val updateApiVersions: UpdateApiVersionsUseCase get() = UpdateApiVersionsUseCaseImpl(serverConfigRepository)
    val storeServerConfig: StoreServerConfigUseCase get() = StoreServerConfigUseCaseImpl(serverConfigRepository)

    val saveNotificationToken: SaveNotificationTokenUseCase
        get() = SaveNotificationTokenUseCaseImpl(
            notificationTokenRepository,
            observeValidAccounts,
            userSessionScopeProvider.value
        )

    val deleteSession: DeleteSessionUseCase
        get() = DeleteSessionUseCase(sessionRepository, userSessionScopeProvider.value)

    val serverConfigForAccounts: ServerConfigForAccountUseCase
        get() =
            ServerConfigForAccountUseCase(serverConfigRepository)

    val observeIfAppUpdateRequired: ObserveIfAppUpdateRequiredUseCase
        get() = ObserveIfAppUpdateRequiredUseCaseImpl(
            serverConfigRepository,
            authenticationScopeProvider,
            userSessionScopeProvider.value,
            networkStateObserver
        )

    val checkSystemIntegrity: CheckSystemIntegrityUseCase
        get() = CheckSystemIntegrityUseCaseImpl(
            kaliumConfigs,
            RootDetectorImpl(),
            sessionRepository
        )

    private val userClientRepositoryProvider: UserClientRepositoryProvider
        get() = UserClientRepositoryProviderImpl(userSessionScopeProvider.value)

    val observeNewClientsUseCase: ObserveNewClientsUseCase
        get() = ObserveNewClientsUseCaseImpl(sessionRepository, observeValidAccounts, userClientRepositoryProvider)

    val clearNewClientsForUser: ClearNewClientsForUserUseCase
        get() = ClearNewClientsForUserUseCaseImpl(userSessionScopeProvider.value)
}
