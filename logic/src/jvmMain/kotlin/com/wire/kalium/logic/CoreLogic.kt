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

package com.wire.kalium.logic

import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.UserSessionScope
import com.wire.kalium.logic.feature.UserSessionScopeProvider
import com.wire.kalium.logic.feature.UserSessionScopeProviderImpl
import com.wire.kalium.logic.feature.call.GlobalCallManager
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.network.NetworkStateObserverImpl
import com.wire.kalium.logic.sync.WorkSchedulerProvider
import com.wire.kalium.logic.sync.WorkSchedulerProviderImpl
import com.wire.kalium.logic.util.PlatformContext
import com.wire.kalium.network.NetworkStateObserver
import com.wire.kalium.persistence.db.GlobalDatabaseBuilder
import com.wire.kalium.persistence.db.PlatformDatabaseData
import com.wire.kalium.persistence.db.StorageData
import com.wire.kalium.persistence.db.globalDatabaseProvider
import com.wire.kalium.persistence.kmmSettings.GlobalPrefProvider
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import java.io.File

/**
 * @sample samples.logic.CoreLogicSamples.versionedAuthScope
 */
actual class CoreLogic(
    rootPath: String,
    kaliumConfigs: KaliumConfigs,
    userAgent: String,
    useInMemoryStorage: Boolean = false,
) : CoreLogicCommon(
    rootPath = rootPath, kaliumConfigs = kaliumConfigs, userAgent = userAgent
) {

    override val globalPreferences: GlobalPrefProvider =
        GlobalPrefProvider(
            rootPath = rootPath,
            shouldEncryptData = kaliumConfigs.shouldEncryptData
        )

    override val globalDatabaseBuilder: GlobalDatabaseBuilder = globalDatabaseProvider(
        platformDatabaseData = PlatformDatabaseData(
            storageData = if (useInMemoryStorage) {
                StorageData.InMemory
            } else {
                StorageData.FileBacked(File("$rootPath/global-storage"))
            }
        ),
        passphrase = null,
        queriesContext = KaliumDispatcherImpl.io
    )

    override fun getSessionScope(userId: UserId): UserSessionScope =
        userSessionScopeProvider.value.getOrCreate(userId)

    override suspend fun deleteSessionScope(userId: UserId) {
        userSessionScopeProvider.value.get(userId)?.cancel()
        userSessionScopeProvider.value.delete(userId)
    }

    override val globalCallManager: GlobalCallManager = GlobalCallManager(
        PlatformContext(),
        CoroutineScope(KaliumDispatcherImpl.io)
    )

    override val workSchedulerProvider: WorkSchedulerProvider = WorkSchedulerProviderImpl()
    override val networkStateObserver: NetworkStateObserver = kaliumConfigs.mockNetworkStateObserver ?: NetworkStateObserverImpl()
    override val userSessionScopeProvider: Lazy<UserSessionScopeProvider> = lazy {
        UserSessionScopeProviderImpl(
            authenticationScopeProvider,
            rootPathsProvider,
            getGlobalScope(),
            kaliumConfigs,
            globalPreferences,
            globalCallManager,
            globalDatabaseBuilder,
            userStorageProvider,
            networkStateObserver,
            logoutCallbackManager,
            userAgent,
            useInMemoryStorage
        )
    }
}

@Suppress("MayBeConst")
actual val clientPlatform: String = "jvm"
