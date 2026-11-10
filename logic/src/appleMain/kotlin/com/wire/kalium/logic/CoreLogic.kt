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

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.AccountExecutionHold
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
import com.wire.kalium.logic.util.databaseKey
import com.wire.kalium.logic.util.databaseSecurityHelper
import com.wire.kalium.network.NetworkStateObserver
import com.wire.kalium.persistence.db.GlobalDatabaseBuilder
import com.wire.kalium.persistence.db.PlatformDatabaseData
import com.wire.kalium.persistence.db.StorageData
import com.wire.kalium.persistence.db.globalDatabaseProvider
import com.wire.kalium.persistence.kmmSettings.ApplePersistenceConfig
import com.wire.kalium.persistence.kmmSettings.GlobalPrefProvider
import com.wire.kalium.persistence.util.FileNameUtil
import com.wire.kalium.usernetwork.di.PlatformUserAuthenticatedNetworkProvider
import com.wire.kalium.usernetwork.di.UserAuthenticatedNetworkProvider
import com.wire.kalium.util.KaliumDispatcherImpl

public actual class CoreLogic(
    rootPath: String,
    /**
     * Apple-specific encrypted-storage configuration. Must be supplied by the consumer
     * (host app). The `serviceName` inside must be stable across app reinstalls —
     * typically the host app's bundle identifier — and must NOT be derived from
     * `NSHomeDirectory()`, which embeds the iOS Application UUID and changes on every
     * reinstall, orphaning all previously stored keychain entries.
     */
    private val keychainConfig: ApplePersistenceConfig,
    kaliumConfigs: KaliumConfigs,
    userAgent: String,
    useInMemoryStorage: Boolean = false,
    private val userAuthenticatedNetworkProvider: UserAuthenticatedNetworkProvider = PlatformUserAuthenticatedNetworkProvider(),
) : CoreLogicCommon(
    rootPath = rootPath,
    kaliumConfigs = kaliumConfigs,
    userAgent = userAgent
) {
    actual override val globalPreferences: GlobalPrefProvider =
        GlobalPrefProvider(
            keychainConfig = keychainConfig,
            shouldEncryptData = kaliumConfigs.shouldEncryptData()
        )

    private val securityHelper = databaseSecurityHelper(globalPreferences.passphraseStorage)

    private val globalStoragePath = "$rootPath/global-storage"

    actual override val globalDatabaseBuilder: GlobalDatabaseBuilder = globalDatabaseProvider(
        platformDatabaseData = PlatformDatabaseData(
            storageData = if (useInMemoryStorage) {
                StorageData.InMemory
            } else {
                StorageData.FileBacked(globalStoragePath)
            }
        ),
        queriesContext = KaliumDispatcherImpl.io,
        passphrase = if (kaliumConfigs.shouldEncryptData() && !useInMemoryStorage) {
            databaseKey(
                databasePath = "$globalStoragePath/${FileNameUtil.globalDBName()}",
                existingKey = securityHelper::globalDBSecretOrNull,
                newKey = { securityHelper.globalDBKeyMaterial(databaseExists = false).rawKey }
            )
        } else {
            null
        },
        // The app and its extensions share the global database, see setSqlCipherKey.
        enableWAL = true
    )

    public actual override val networkStateObserver: NetworkStateObserver = NetworkStateObserverImpl()
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
            keychainConfig,
            syncShutdownCoordinator,
        )
    }

    public actual override fun getSessionScope(userId: UserId): UserSessionScope =
        userSessionScopeProvider.value.getOrCreate(userId)

    actual override suspend fun deleteSessionScope(userId: UserId) = releaseSessionScope(userId)

    /** Revalidate an owned, synced session for notification receiving after app restoration. */
    // Retain the existing public result type for consumer compatibility.
    @Suppress("konsist.kaliumLogicModuleShouldNotExposeEitherTypesInPublicAPI")
    public suspend fun prepareSessionForNotifications(userId: UserId, scope: UserSessionScope): Either<CoreFailure, Unit> =
        userSessionScopeProvider.value.prepareReceiving(userId, scope)

    /**
     * Whether the app runs in the foreground. iOS kills a suspended app that keeps an account's lock, so in the
     * background no account takes its lock unless [holdSessionScope] holds it: opens, logins and logouts wait for the
     * foreground instead. In the foreground an account the background release paused opens again on first use.
     * Starts true; an app that may launch in the background sets false before anything opens an account.
     */
    public fun setForeground(foreground: Boolean) {
        (userSessionScopeProvider.value as UserSessionScopeProviderImpl).setForeground(foreground)
    }

    /**
     * After [setForeground] false and once foreground work has stopped: pauses every account no hold keeps, keeping
     * its session, and releases its lock. Returns whether every release succeeded.
     */
    public suspend fun releaseBackgroundAccounts(): Boolean =
        (userSessionScopeProvider.value as UserSessionScopeProviderImpl).releaseBackgroundAccounts()

    /**
     * Opens [userId] for work with execution time of its own, such as a CallKit call or a background task, and keeps
     * it open in the background until [AccountExecutionHold.release].
     */
    public suspend fun holdSessionScope(userId: UserId): AccountExecutionHold =
        (userSessionScopeProvider.value as UserSessionScopeProviderImpl).hold(userId)

    /**
     * Fully close an account after the host has cancelled and joined its users and collectors.
     * Failure keeps the account unavailable until process exit.
     */
    public suspend fun releaseSessionScope(userId: UserId) {
        userSessionScopeProvider.value.delete(userId)
    }

    actual override val workSchedulerProvider: WorkSchedulerProvider = WorkSchedulerProviderImpl()
    public actual override val audioNormalizedLoudnessBuilder: AudioNormalizedLoudnessBuilder = AudioNormalizedLoudnessBuilderImpl()
    actual override val globalCallManager: GlobalCallManager by lazy {
        GlobalCallManager(getGlobalScope(), networkStateObserver, PlatformContext())
    }
}

@Suppress("MayBeConst")
internal actual val clientPlatform: String = "ios"
