package com.wire.kalium.logic

import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.data.auth.login.ProxyCredentials
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.di.PlatformRootPathsProvider
import com.wire.kalium.logic.di.PlatformUserStorageProvider
import com.wire.kalium.logic.di.RootPathsProvider
import com.wire.kalium.logic.di.UserStorageProvider
import com.wire.kalium.logic.feature.UserSessionScope
import com.wire.kalium.logic.feature.UserSessionScopeProvider
import com.wire.kalium.logic.feature.auth.AuthenticationScope
import com.wire.kalium.logic.feature.auth.autoVersioningAuth.AutoVersionAuthScopeUseCase
import com.wire.kalium.logic.feature.call.GlobalCallManager
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.sync.GlobalWorkScheduler
import com.wire.kalium.logic.sync.periodic.UpdateApiVersionsScheduler
import com.wire.kalium.persistence.db.GlobalDatabaseProvider
import com.wire.kalium.persistence.kmmSettings.GlobalPrefProvider

expect class CoreLogic : CoreLogicCommon

abstract class CoreLogicCommon internal constructor(
    // TODO: can client label be replaced with clientConfig.deviceName() ?
    protected val clientLabel: String,
    protected val rootPath: String,
    protected val kaliumConfigs: KaliumConfigs,
    protected val idMapper: IdMapper = MapperProvider.idMapper()
) {
    protected abstract val globalPreferences: Lazy<GlobalPrefProvider>
    protected abstract val globalDatabase: Lazy<GlobalDatabaseProvider>
    protected abstract val userSessionScopeProvider: Lazy<UserSessionScopeProvider>
    protected val userStorageProvider: UserStorageProvider = PlatformUserStorageProvider()

    val rootPathsProvider: RootPathsProvider = PlatformRootPathsProvider(rootPath)

    fun getGlobalScope(): GlobalKaliumScope =
        GlobalKaliumScope(globalDatabase, globalPreferences, kaliumConfigs, userSessionScopeProvider)

    @Suppress("MemberVisibilityCanBePrivate") // Can be used by other targets like iOS and JS
    fun getAuthenticationScope(serverConfig: ServerConfig, proxyCredentials: ProxyCredentials? = null): AuthenticationScope =
        // TODO(logic): make it lazier
        AuthenticationScope(clientLabel, serverConfig, proxyCredentials)

    @Suppress("MemberVisibilityCanBePrivate") // Can be used by other targets like iOS and JS
    abstract fun getSessionScope(userId: UserId): UserSessionScope

    abstract fun deleteSessionScope(userId: UserId) // TODO remove when proper use case is ready

    // TODO: make globalScope a singleton
    inline fun <T> globalScope(action: GlobalKaliumScope.() -> T): T = getGlobalScope().action()

    inline fun <T> authenticationScope(serverConfig: ServerConfig, action: AuthenticationScope.() -> T): T =
        getAuthenticationScope(serverConfig).action()

    inline fun <T> sessionScope(userId: UserId, action: UserSessionScope.() -> T): T = getSessionScope(userId).action()

    protected abstract val globalCallManager: GlobalCallManager

    protected abstract val globalWorkScheduler: GlobalWorkScheduler

    val updateApiVersionsScheduler: UpdateApiVersionsScheduler get() = globalWorkScheduler

    fun versionedAuthenticationScope(serverLinks: ServerConfig.Links): AutoVersionAuthScopeUseCase =
        AutoVersionAuthScopeUseCase(serverLinks, this)
}
