package com.wire.kalium.logic

import com.wire.kalium.logic.configuration.UserConfigDataSource
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.configuration.notification.NotificationTokenDataSource
import com.wire.kalium.logic.configuration.notification.NotificationTokenRepository
import com.wire.kalium.logic.configuration.server.ServerConfigDataSource
import com.wire.kalium.logic.configuration.server.ServerConfigRepository
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.feature.UserSessionScopeProvider
import com.wire.kalium.logic.feature.auth.AddAuthenticatedUserUseCase
import com.wire.kalium.logic.feature.notificationToken.SaveNotificationTokenUseCase
import com.wire.kalium.logic.feature.server.FetchApiVersionUseCase
import com.wire.kalium.logic.feature.server.FetchApiVersionUseCaseImpl
import com.wire.kalium.logic.feature.server.GetServerConfigUseCase
import com.wire.kalium.logic.feature.server.ObserveServerConfigUseCase
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
import com.wire.kalium.logic.feature.user.webSocketStatus.PersistPersistentWebSocketConnectionStatusUseCase
import com.wire.kalium.logic.feature.user.webSocketStatus.PersistPersistentWebSocketConnectionStatusUseCaseImpl
import com.wire.kalium.logic.feature.user.webSocketStatus.ObservePersistentWebSocketConnectionStatusUseCase
import com.wire.kalium.logic.feature.user.webSocketStatus.ObservePersistentWebSocketConnectionStatusUseCaseImpl
import com.wire.kalium.logic.featureFlags.GetBuildConfigsUseCase
import com.wire.kalium.logic.featureFlags.GetBuildConfigsUseCaseImpl
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.network.UnboundNetworkContainer
import com.wire.kalium.persistence.client.TokenStorage
import com.wire.kalium.persistence.client.TokenStorageImpl
import com.wire.kalium.persistence.client.UserConfigStorage
import com.wire.kalium.persistence.client.UserConfigStorageImpl
import com.wire.kalium.persistence.db.GlobalDatabaseProvider
import com.wire.kalium.persistence.kmm_settings.KaliumPreferences

/**
 * Scope that exposes all operations that are user and backend agnostic, like
 * - Storing and retrieving sessions after authenticating
 * - Updating client or device metadata (like push notification token)
 * - Getting back-end information from a deeplink
 *
 * @see AuthenticationScope
 * @see UserSessionScope
 */

class GlobalKaliumScope(
    private val globalDatabase: Lazy<GlobalDatabaseProvider>,
    private val globalPreferences: Lazy<KaliumPreferences>,
    private val sessionRepository: SessionRepository,
    private val kaliumConfigs: KaliumConfigs,
    private val userSessionScopeProvider: Lazy<UserSessionScopeProvider>
) {

    private val unboundNetworkContainer: UnboundNetworkContainer by lazy {
        UnboundNetworkContainer()
    }

    internal val serverConfigRepository: ServerConfigRepository
        get() = ServerConfigDataSource(
            unboundNetworkContainer.serverConfigApi,
            globalDatabase.value.serverConfigurationDAO,
            unboundNetworkContainer.remoteVersion,
        )
    private val tokenStorage: TokenStorage get() = TokenStorageImpl(globalPreferences.value)
    private val userConfigStorage: UserConfigStorage get() = UserConfigStorageImpl(globalPreferences.value)

    private val notificationTokenRepository: NotificationTokenRepository get() = NotificationTokenDataSource(tokenStorage)
    private val userConfigRepository: UserConfigRepository get() = UserConfigDataSource(userConfigStorage)
    val addAuthenticatedAccount: AddAuthenticatedUserUseCase get() = AddAuthenticatedUserUseCase(sessionRepository)
    val getSessions: GetSessionsUseCase get() = GetSessionsUseCase(sessionRepository)
    val observeValidAccounts: ObserveValidAccountsUseCase
        get() = ObserveValidAccountsUseCaseImpl(sessionRepository, userSessionScopeProvider.value)

    val session: SessionScope get() = SessionScope(sessionRepository)
    val fetchServerConfigFromDeepLink: GetServerConfigUseCase get() = GetServerConfigUseCase(serverConfigRepository)
    val fetchApiVersion: FetchApiVersionUseCase get() = FetchApiVersionUseCaseImpl(serverConfigRepository)
    val observeServerConfig: ObserveServerConfigUseCase get() = ObserveServerConfigUseCase(serverConfigRepository)
    val updateApiVersions: UpdateApiVersionsUseCase get() = UpdateApiVersionsUseCaseImpl(serverConfigRepository)

    val saveNotificationToken: SaveNotificationTokenUseCase
        get() = SaveNotificationTokenUseCase(notificationTokenRepository)
    val enableLogging: EnableLoggingUseCase get() = EnableLoggingUseCaseImpl(userConfigRepository)
    val isLoggingEnabled: IsLoggingEnabledUseCase get() = IsLoggingEnabledUseCaseImpl(userConfigRepository)
    val buildConfigs: GetBuildConfigsUseCase get() = GetBuildConfigsUseCaseImpl(kaliumConfigs)
    val persistPersistentWebSocketConnectionStatus: PersistPersistentWebSocketConnectionStatusUseCase
        get() = PersistPersistentWebSocketConnectionStatusUseCaseImpl(
            userConfigRepository
        )
    val observePersistentWebSocketConnectionStatus: ObservePersistentWebSocketConnectionStatusUseCase
        get() = ObservePersistentWebSocketConnectionStatusUseCaseImpl(
            userConfigRepository
        )
    val deleteSession: DeleteSessionUseCase get() = DeleteSessionUseCase(sessionRepository, userSessionScopeProvider.value)
}
