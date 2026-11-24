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
import com.wire.kalium.logic.feature.asset.AudioNormalizedLoudnessBuilder
import com.wire.kalium.logic.feature.asset.AudioNormalizedLoudnessBuilderImpl
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
import com.wire.kalium.persistence.db.protectedGlobalDatabaseProvider
import com.wire.kalium.persistence.kmmSettings.GlobalPrefProvider
import com.wire.kalium.persistence.protection.JvmStorageKeyStore
import com.wire.kalium.persistence.util.FileNameUtil
import com.wire.kalium.usernetwork.di.PlatformUserAuthenticatedNetworkProvider
import com.wire.kalium.usernetwork.di.UserAuthenticatedNetworkProvider
import com.wire.kalium.userstorage.di.PlatformUserStorageProvider
import com.wire.kalium.userstorage.di.UserStorageProvider
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS

/**
 * @sample samples.logic.CoreLogicSamples.versionedAuthScope
 */
public actual class CoreLogic private constructor(
    rootPath: String,
    kaliumConfigs: KaliumConfigs,
    userAgent: String,
    useInMemoryStorage: Boolean,
    internal val userAuthenticatedNetworkProvider: UserAuthenticatedNetworkProvider,
    storageKeyStore: JvmStorageKeyStore?,
) : CoreLogicCommon(
    rootPath = rootPath,
    kaliumConfigs = kaliumConfigs,
    userAgent = userAgent
) {
    public constructor(
        rootPath: String,
        kaliumConfigs: KaliumConfigs,
        userAgent: String,
        useInMemoryStorage: Boolean = false,
        userAuthenticatedNetworkProvider: UserAuthenticatedNetworkProvider = PlatformUserAuthenticatedNetworkProvider(),
    ) : this(rootPath, kaliumConfigs, userAgent, useInMemoryStorage, userAuthenticatedNetworkProvider, null)

    internal constructor(
        rootPath: String,
        kaliumConfigs: KaliumConfigs,
        userAgent: String,
        storageKeyStore: JvmStorageKeyStore,
    ) : this(
        rootPath,
        kaliumConfigs,
        userAgent,
        false,
        object : UserAuthenticatedNetworkProvider(useInstanceCache = true) {},
        storageKeyStore,
    )

    init {
        if (storageKeyStore != null) {
            check(Files.notExists(File(rootPath, FileNameUtil.appPrefFile()).toPath(), NOFOLLOW_LINKS)) {
                "Existing plaintext settings require a separate migration"
            }
        }
    }

    actual override val globalDatabaseBuilder: GlobalDatabaseBuilder =
        if (storageKeyStore == null) {
            globalDatabaseProvider(
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
        } else {
            protectedGlobalDatabaseProvider(File("$rootPath/global-storage"), storageKeyStore, KaliumDispatcherImpl.io)
        }

    actual override val globalPreferences: GlobalPrefProvider =
        if (storageKeyStore == null) {
            GlobalPrefProvider(rootPath = rootPath, shouldEncryptData = kaliumConfigs.shouldEncryptData())
        } else {
            GlobalPrefProvider.protectedStorage(globalDatabaseBuilder)
        }

    internal override val userStorageProvider: UserStorageProvider = storageKeyStore
        ?.let { PlatformUserStorageProvider.protectedStorage(it) }
        ?: super.userStorageProvider

    public actual override fun getSessionScope(userId: UserId): UserSessionScope =
        userSessionScopeProvider.value.getOrCreate(userId)

    actual override suspend fun deleteSessionScope(userId: UserId) {
        userSessionScopeProvider.value.get(userId)?.cancel()
        userSessionScopeProvider.value.delete(userId)
    }

    public actual override val networkStateObserver: NetworkStateObserver =
        kaliumConfigs.mockNetworkStateObserver ?: NetworkStateObserverImpl()
    actual override val globalCallManager: GlobalCallManager = GlobalCallManager(
        scope = CoroutineScope(KaliumDispatcherImpl.io),
        networkStateObserver = networkStateObserver,
        platformContext = PlatformContext(),
    )

    actual override val workSchedulerProvider: WorkSchedulerProvider = WorkSchedulerProviderImpl()
    actual override val userSessionScopeProvider: Lazy<UserSessionScopeProvider> = lazy {
        UserSessionScopeProviderImpl(
            authenticationScopeProvider,
            rootPathsProvider,
            getGlobalScope(),
            kaliumConfigs,
            globalPreferences,
            globalCallManager,
            globalDatabaseBuilder,
            userStorageProvider,
            userAuthenticatedNetworkProvider,
            networkStateObserver,
            logoutCallbackManager,
            userAgent,
            useInMemoryStorage,
            syncShutdownCoordinator,
        )
    }
    public actual override val audioNormalizedLoudnessBuilder: AudioNormalizedLoudnessBuilder = AudioNormalizedLoudnessBuilderImpl()

    public companion object {
        /**
         * Opens system-key-protected global settings and SQLite databases in a dedicated profile.
         * The built-in provider requires macOS. Existing plaintext profiles require migration.
         */
        public fun withProtectedPersistence(rootPath: String, kaliumConfigs: KaliumConfigs, userAgent: String): CoreLogic =
            CoreLogic(rootPath, kaliumConfigs, userAgent, JvmStorageKeyStore.system())
    }
}

@Suppress("MayBeConst")
internal actual val clientPlatform: String = "jvm"
