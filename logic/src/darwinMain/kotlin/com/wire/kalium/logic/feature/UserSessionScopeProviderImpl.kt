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

package com.wire.kalium.logic.feature

import com.wire.kalium.logic.GlobalKaliumScope
import com.wire.kalium.logic.data.asset.AssetsStorageFolder
import com.wire.kalium.logic.data.asset.CacheFolder
import com.wire.kalium.logic.data.asset.DBFolder
import com.wire.kalium.logic.data.asset.DataStoragePaths
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.PlatformUserStorageProperties
import com.wire.kalium.logic.di.RootPathsProvider
import com.wire.kalium.logic.di.UserStorageProvider
import com.wire.kalium.logic.feature.auth.AuthenticationScopeProvider
import com.wire.kalium.logic.feature.call.GlobalCallManager
import com.wire.kalium.logic.featureFlags.FeatureSupportImpl
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.network.NetworkStateObserver
import com.wire.kalium.logic.network.SessionManagerImpl
import com.wire.kalium.logic.sync.UserSessionWorkSchedulerImpl
import com.wire.kalium.network.networkContainer.AuthenticatedNetworkContainer
import com.wire.kalium.persistence.kmmSettings.GlobalPrefProvider
import com.wire.kalium.network.api.base.model.UserId as UserIdDTO

@Suppress("LongParameterList")
internal actual class UserSessionScopeProviderImpl(
    private val authenticationScopeProvider: AuthenticationScopeProvider,
    private val rootPathsProvider: RootPathsProvider,
    private val globalScope: GlobalKaliumScope,
    private val kaliumConfigs: KaliumConfigs,
    private val globalPreferences: GlobalPrefProvider,
    private val globalCallManager: GlobalCallManager,
    private val userStorageProvider: UserStorageProvider,
    private val networkStateObserver: NetworkStateObserver,
) : UserSessionScopeProviderCommon(globalCallManager, userStorageProvider) {
    override fun create(userId: UserId): UserSessionScope {
        val rootAccountPath = rootPathsProvider.rootAccountPath(userId)
        val rootProteusPath = rootPathsProvider.rootProteusPath(userId)
        val rootStoragePath = "$rootAccountPath/storage"
        val rootFileSystemPath = AssetsStorageFolder("$rootStoragePath/files")
        val rootCachePath = CacheFolder("$rootAccountPath/cache")
        val dbPath = DBFolder("$rootAccountPath/database")
        val dataStoragePaths = DataStoragePaths(rootFileSystemPath, rootCachePath, dbPath)
        val sessionManager = SessionManagerImpl(
            globalScope.sessionRepository, userId,
            tokenStorage = globalPreferences.authTokenStorage
        )
        val networkContainer: AuthenticatedNetworkContainer = AuthenticatedNetworkContainer.create(
            sessionManager,
            UserIdDTO(userId.value, userId.domain)
        )
        val featureSupport = FeatureSupportImpl(kaliumConfigs, sessionManager.serverConfig().metaData.commonApiVersion.version)
        val proteusClientProvider = ProteusClientProviderImpl(rootProteusPath, userId, globalPreferences.passphraseStorage, kaliumConfigs)

        val userSessionWorkScheduler = UserSessionWorkSchedulerImpl(userId)

        val userDataSource = AuthenticatedDataSourceSet(
            rootAccountPath,
            networkContainer,
            authenticationScopeProvider.provide(
                sessionManager.getServerConfig(),
                sessionManager.getProxyCredentials()
            ),
            proteusClientProvider,
            userSessionWorkScheduler
        )
        return UserSessionScope(
            PlatformUserStorageProperties(rootPathsProvider.rootPath, rootStoragePath),
            userId,
            userDataSource,
            globalScope,
            globalCallManager,
            globalPreferences,
            sessionManager,
            dataStoragePaths,
            kaliumConfigs,
            featureSupport,
            userStorageProvider,
            this,
            networkStateObserver
        )
    }

}
