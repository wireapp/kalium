/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

package com.wire.kalium.logic.notificationextension

import com.wire.kalium.logic.data.session.SessionDataSource
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.PlatformRootPathsProvider
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.persistence.db.PlatformDatabaseData
import com.wire.kalium.persistence.db.StorageData
import com.wire.kalium.persistence.db.globalDatabaseProvider
import com.wire.kalium.persistence.kmmSettings.ApplePersistenceConfig
import com.wire.kalium.persistence.kmmSettings.GlobalPrefProvider
import com.wire.kalium.persistence.util.configurePersistenceDebug
import com.wire.kalium.usernetwork.di.PlatformUserAuthenticatedNetworkProvider
import com.wire.kalium.userstorage.di.PlatformUserStorageProvider
import com.wire.kalium.util.InternalKaliumApi
import com.wire.kalium.util.KaliumDispatcherImpl

/**
 * Minimal Apple account assembly for bounded notification-extension work.
 *
 * Unlike [com.wire.kalium.logic.CoreLogic], this type has no user-session provider, global call
 * manager, work scheduler or foreground application scope. It exists only while the spike still
 * consumes selected implementation classes from `:logic`; those classes remain extraction targets.
 */
@InternalKaliumApi("Temporary internal NSE assembly while the narrow provider graph is extracted from :logic")
public class NotificationExtensionCoreLogic(
    private val rootPath: String,
    private val keychainConfig: ApplePersistenceConfig,
    private val kaliumConfigs: KaliumConfigs,
    private val userAgent: String
) {
    private val globalPreferences = GlobalPrefProvider(
        keychainConfig = keychainConfig,
        shouldEncryptData = kaliumConfigs.shouldEncryptData()
    )
    private val globalDatabase = globalDatabaseProvider(
        platformDatabaseData = PlatformDatabaseData(StorageData.FileBacked("$rootPath/global-storage")),
        queriesContext = KaliumDispatcherImpl.io,
        passphrase = null
    )
    private val sessionRepository = SessionDataSource(
        accountsDAO = globalDatabase.accountsDAO,
        authTokenStorage = globalPreferences.authTokenStorage,
        serverConfigDAO = globalDatabase.serverConfigurationDAO
    )
    private val rootPathsProvider = PlatformRootPathsProvider(rootPath)
    private val userStorageProvider = PlatformUserStorageProvider()
    private val userAuthenticatedNetworkProvider = PlatformUserAuthenticatedNetworkProvider()

    init {
        configurePersistenceDebug(kaliumConfigs.isDebug)
    }

    /** Creates one passive authenticated receive/decrypt bridge for [userId]. */
    public fun createBridge(userId: UserId): NotificationExtensionLogicBridge =
        AppleNotificationExtensionLogicBridgeFactory(
            rootPath = rootPath,
            keychainConfig = keychainConfig,
            kaliumConfigs = kaliumConfigs,
            userAgent = userAgent,
            globalPreferences = globalPreferences,
            sessionRepository = sessionRepository,
            rootPathsProvider = rootPathsProvider,
            userStorageProvider = userStorageProvider,
            userAuthenticatedNetworkProvider = userAuthenticatedNetworkProvider
        ).create(userId)
}
