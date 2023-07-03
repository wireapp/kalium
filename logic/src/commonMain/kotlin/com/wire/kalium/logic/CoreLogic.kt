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
import com.wire.kalium.logic.feature.auth.AuthenticationScopeProvider
import com.wire.kalium.logic.feature.auth.autoVersioningAuth.AutoVersionAuthScopeUseCase
import com.wire.kalium.logic.feature.call.GlobalCallManager
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.network.NetworkStateObserver
import com.wire.kalium.logic.sync.GlobalWorkScheduler
import com.wire.kalium.logic.sync.periodic.UpdateApiVersionsScheduler
import com.wire.kalium.persistence.db.GlobalDatabaseProvider
import com.wire.kalium.persistence.kmmSettings.GlobalPrefProvider

expect class CoreLogic : CoreLogicCommon

abstract class CoreLogicCommon internal constructor(
    protected val rootPath: String,
    protected val userAgent: String,
    protected val kaliumConfigs: KaliumConfigs,
    protected val idMapper: IdMapper = MapperProvider.idMapper()
) {
    protected abstract val globalPreferences: Lazy<GlobalPrefProvider>
    protected abstract val globalDatabase: Lazy<GlobalDatabaseProvider>
    protected abstract val userSessionScopeProvider: Lazy<UserSessionScopeProvider>
    protected val userStorageProvider: UserStorageProvider = PlatformUserStorageProvider()

    val rootPathsProvider: RootPathsProvider = PlatformRootPathsProvider(rootPath)
    protected val authenticationScopeProvider: AuthenticationScopeProvider =
        AuthenticationScopeProvider(userAgent)

    fun getGlobalScope(): GlobalKaliumScope =
        GlobalKaliumScope(
            userAgent,
            globalDatabase,
            globalPreferences,
            kaliumConfigs,
            userSessionScopeProvider,
            authenticationScopeProvider
        )

    @Suppress("MemberVisibilityCanBePrivate") // Can be used by other targets like iOS and JS
    fun getAuthenticationScope(
        serverConfig: ServerConfig,
        proxyCredentials: ProxyCredentials? = null
    ): AuthenticationScope =
        authenticationScopeProvider.provide(serverConfig, proxyCredentials, getGlobalScope().serverConfigRepository)

    @Suppress("MemberVisibilityCanBePrivate") // Can be used by other targets like iOS and JS
    abstract fun getSessionScope(userId: UserId): UserSessionScope

    abstract fun deleteSessionScope(userId: UserId) // TODO remove when proper use case is ready

    // TODO: make globalScope a singleton
    inline fun <T> globalScope(action: GlobalKaliumScope.() -> T): T = getGlobalScope().action()

    inline fun <T> authenticationScope(serverConfig: ServerConfig, action: AuthenticationScope.() -> T): T =
        getAuthenticationScope(serverConfig).action()

    inline fun <T> sessionScope(
        userId: UserId,
        action: UserSessionScope.() -> T
    ): T = getSessionScope(userId).action()

    protected abstract val globalCallManager: GlobalCallManager

    protected abstract val globalWorkScheduler: GlobalWorkScheduler

    val updateApiVersionsScheduler: UpdateApiVersionsScheduler get() = globalWorkScheduler

    fun versionedAuthenticationScope(serverLinks: ServerConfig.Links): AutoVersionAuthScopeUseCase =
        AutoVersionAuthScopeUseCase(kaliumConfigs, serverLinks, this)

    protected abstract val networkStateObserver: NetworkStateObserver
}

expect val clientPlatform: String
