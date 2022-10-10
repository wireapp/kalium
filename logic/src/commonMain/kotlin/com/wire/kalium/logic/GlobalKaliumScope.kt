package com.wire.kalium.logic

import com.wire.kalium.logic.configuration.GlobalConfigDataSource
import com.wire.kalium.logic.configuration.GlobalConfigRepository
import com.wire.kalium.logic.configuration.notification.NotificationTokenDataSource
import com.wire.kalium.logic.configuration.notification.NotificationTokenRepository
import com.wire.kalium.logic.configuration.server.ServerConfigDataSource
import com.wire.kalium.logic.configuration.server.ServerConfigRepository
import com.wire.kalium.logic.data.session.SessionDataSource
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.feature.UserSessionScopeProvider
import com.wire.kalium.logic.feature.auth.AddAuthenticatedUserUseCase
import com.wire.kalium.logic.feature.auth.ValidateEmailUseCase
import com.wire.kalium.logic.feature.auth.ValidateEmailUseCaseImpl
import com.wire.kalium.logic.feature.auth.ValidatePasswordUseCase
import com.wire.kalium.logic.feature.auth.ValidatePasswordUseCaseImpl
import com.wire.kalium.logic.feature.auth.ValidateUserHandleUseCase
import com.wire.kalium.logic.feature.auth.ValidateUserHandleUseCaseImpl
import com.wire.kalium.logic.feature.notificationToken.SaveNotificationTokenUseCase
import com.wire.kalium.logic.feature.notificationToken.SaveNotificationTokenUseCaseImpl
import com.wire.kalium.logic.feature.server.FetchApiVersionUseCase
import com.wire.kalium.logic.feature.server.FetchApiVersionUseCaseImpl
import com.wire.kalium.logic.feature.server.GetServerConfigUseCase
import com.wire.kalium.logic.feature.server.ObserveServerConfigUseCase
import com.wire.kalium.logic.feature.server.StoreServerConfigUseCase
import com.wire.kalium.logic.feature.server.StoreServerConfigUseCaseImpl
import com.wire.kalium.logic.feature.server.UpdateApiVersionsUseCase
import com.wire.kalium.logic.feature.server.UpdateApiVersionsUseCaseImpl
import com.wire.kalium.logic.feature.session.DeleteSessionUseCase
import com.wire.kalium.logic.feature.session.GetSessionsUseCase
import com.wire.kalium.logic.feature.session.SessionScope
import com.wire.kalium.logic.feature.user.ObserveValidAccountsUseCase
import com.wire.kalium.logic.feature.user.ObserveValidAccountsUseCaseImpl
import com.wire.kalium.logic.feature.user.loggingStatus.EnableLoggingUseCase
import com.wire.kalium.logic.feature.user.loggingStatus.EnableLoggingUseCaseImpl
import com.wire.kalium.logic.feature.user.loggingStatus.IsLoggingEnabledUseCase
import com.wire.kalium.logic.feature.user.loggingStatus.IsLoggingEnabledUseCaseImpl
import com.wire.kalium.logic.feature.user.webSocketStatus.ObservePersistentWebSocketConnectionStatusUseCase
import com.wire.kalium.logic.feature.user.webSocketStatus.ObservePersistentWebSocketConnectionStatusUseCaseImpl
import com.wire.kalium.logic.feature.user.webSocketStatus.PersistPersistentWebSocketConnectionStatusUseCase
import com.wire.kalium.logic.feature.user.webSocketStatus.PersistPersistentWebSocketConnectionStatusUseCaseImpl
import com.wire.kalium.logic.featureFlags.GetBuildConfigsUseCase
import com.wire.kalium.logic.featureFlags.GetBuildConfigsUseCaseImpl
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.network.networkContainer.UnboundNetworkContainer
import com.wire.kalium.network.networkContainer.UnboundNetworkContainerCommon
import com.wire.kalium.persistence.db.GlobalDatabaseProvider
import com.wire.kalium.persistence.kmmSettings.GlobalPrefProvider

/**
 * Scope that exposes all operations that are user and backend agnostic, like
 * - Storing and retrieving sessions after authenticating
 * - Updating client or device metadata (like push notification token)
 * - Getting back-end information from a deeplink
 *
 * @see [com.wire.kalium.logic.feature.auth.AuthenticationScope]
 * @see [com.wire.kalium.logic.feature.UserSessionScope]
 */

class GlobalKaliumScope(
    private val globalDatabase: Lazy<GlobalDatabaseProvider>,
    private val globalPreferences: Lazy<GlobalPrefProvider>,
    private val kaliumConfigs: KaliumConfigs,
    private val userSessionScopeProvider: Lazy<UserSessionScopeProvider>
) {

    private val unboundNetworkContainer: UnboundNetworkContainer by lazy {
        UnboundNetworkContainerCommon(developmentApiEnabled = kaliumConfigs.developmentApiEnabled)
    }

    internal val serverConfigRepository: ServerConfigRepository
        get() = ServerConfigDataSource(
            unboundNetworkContainer.serverConfigApi,
            globalDatabase.value.serverConfigurationDAO,
            unboundNetworkContainer.remoteVersion,
            kaliumConfigs.developmentApiEnabled
        )

    val sessionRepository: SessionRepository
        get() =
            SessionDataSource(
                globalDatabase.value.accountsDAO,
                globalPreferences.value.authTokenStorage,
                serverConfigRepository
            )

    private val notificationTokenRepository: NotificationTokenRepository
        get() =
            NotificationTokenDataSource(globalPreferences.value.tokenStorage)
    private val globalConfigRepository: GlobalConfigRepository
        get() =
            GlobalConfigDataSource(globalPreferences.value.globalAppConfigStorage)

    val validateEmailUseCase: ValidateEmailUseCase get() = ValidateEmailUseCaseImpl()
    val validateUserHandleUseCase: ValidateUserHandleUseCase get() = ValidateUserHandleUseCaseImpl()
    val validatePasswordUseCase: ValidatePasswordUseCase get() = ValidatePasswordUseCaseImpl()

    val addAuthenticatedAccount: AddAuthenticatedUserUseCase
        get() =
            AddAuthenticatedUserUseCase(sessionRepository, serverConfigRepository)
    val getSessions: GetSessionsUseCase get() = GetSessionsUseCase(sessionRepository)
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
    val enableLogging: EnableLoggingUseCase get() = EnableLoggingUseCaseImpl(globalConfigRepository)
    val isLoggingEnabled: IsLoggingEnabledUseCase get() = IsLoggingEnabledUseCaseImpl(globalConfigRepository)
    val buildConfigs: GetBuildConfigsUseCase get() = GetBuildConfigsUseCaseImpl(kaliumConfigs)
    val persistPersistentWebSocketConnectionStatus: PersistPersistentWebSocketConnectionStatusUseCase
        get() = PersistPersistentWebSocketConnectionStatusUseCaseImpl(
            globalConfigRepository
        )
    val observePersistentWebSocketConnectionStatus: ObservePersistentWebSocketConnectionStatusUseCase
        get() = ObservePersistentWebSocketConnectionStatusUseCaseImpl(
            globalConfigRepository
        )
    val deleteSession: DeleteSessionUseCase
        get() = DeleteSessionUseCase(sessionRepository, userSessionScopeProvider.value)
}
