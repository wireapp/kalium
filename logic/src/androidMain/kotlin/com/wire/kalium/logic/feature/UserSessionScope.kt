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

package com.wire.kalium.logic.feature

import android.content.Context
import com.wire.kalium.logic.GlobalKaliumScope
import com.wire.kalium.logic.configuration.ClientConfig
import com.wire.kalium.logic.configuration.ClientConfigImpl
import com.wire.kalium.logic.data.asset.DataStoragePaths
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.PlatformUserStorageProperties
import com.wire.kalium.logic.di.RootPathsProvider
import com.wire.kalium.logic.di.UserStorageProvider
import com.wire.kalium.logic.feature.auth.AuthenticationScopeProvider
import com.wire.kalium.logic.feature.auth.LogoutCallback
import com.wire.kalium.logic.feature.call.GlobalCallManager
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.sync.UserSessionWorkScheduler
import com.wire.kalium.logic.util.SecurityHelperImpl
import com.wire.kalium.network.NetworkStateObserver
import com.wire.kalium.persistence.db.GlobalDatabaseProvider
import com.wire.kalium.persistence.kmmSettings.GlobalPrefProvider

@Suppress("LongParameterList")
internal fun UserSessionScope(
    applicationContext: Context,
    userAgent: String,
    userId: UserId,
    globalScope: GlobalKaliumScope,
    globalDatabaseProvider: GlobalDatabaseProvider,
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
    logoutCallback: LogoutCallback,
): UserSessionScope {
    val platformUserStorageProperties =
        PlatformUserStorageProperties(applicationContext, SecurityHelperImpl(globalPreferences.passphraseStorage))

    val clientConfig: ClientConfig = ClientConfigImpl(applicationContext)

    return UserSessionScope(
        userAgent,
        userId,
        globalScope,
        globalCallManager,
        globalDatabaseProvider,
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
        networkStateObserver,
        logoutCallback
    )
}
