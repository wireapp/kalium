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
@file:Suppress("konsist.useCasesShouldNotAccessDaoLayerDirectly", "konsist.useCasesShouldNotAccessNetworkLayerDirectly")

package com.wire.kalium.logic.feature

import com.wire.kalium.logic.GlobalKaliumScope
import com.wire.kalium.logic.configuration.ClientConfig
import com.wire.kalium.logic.configuration.ClientConfigImpl
import com.wire.kalium.logic.data.asset.DataStoragePaths
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.PlatformUserStorageProperties
import com.wire.kalium.logic.di.RootPathsProvider
import com.wire.kalium.logic.di.UserStorageProvider
import com.wire.kalium.logic.feature.auth.AuthenticationScopeProvider
import com.wire.kalium.logic.feature.call.GlobalCallManager
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.network.NetworkStateObserver
import com.wire.kalium.logic.sync.UserSessionWorkScheduler
import com.wire.kalium.persistence.kmmSettings.GlobalPrefProvider

@Suppress("LongParameterList")
internal fun UserSessionScope(
    platformUserStorageProperties: PlatformUserStorageProperties,
    userId: UserId,
    globalScope: GlobalKaliumScope,
    globalCallManager: GlobalCallManager,
    globalPreferences: GlobalPrefProvider,
    authenticationScopeProvider: AuthenticationScopeProvider,
    userSessionWorkScheduler: UserSessionWorkScheduler,
    rootPathsProvider: RootPathsProvider,
    dataStoragePaths: DataStoragePaths,
    kaliumConfigs: KaliumConfigs,
    userStorageProvider: UserStorageProvider,
    userSessionScopeProvider: UserSessionScopeProvider,
    networkStateObserver: NetworkStateObserver,
    userAgent: String
): UserSessionScope {

    val clientConfig: ClientConfig = ClientConfigImpl()

    return UserSessionScope(
        userAgent,
        userId,
        globalScope,
        globalCallManager,
        globalPreferences,
        authenticationScopeProvider,
        userSessionWorkScheduler,
        rootPathsProvider,
        dataStoragePaths,
        kaliumConfigs,
        userSessionScopeProvider,
        userStorageProvider,
        clientConfig,
        platformUserStorageProperties,
        networkStateObserver
    )
}
