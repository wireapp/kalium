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
import com.wire.kalium.logic.util.SecurityHelperImpl
import com.wire.kalium.network.NetworkStateObserver
import com.wire.kalium.persistence.config.DesktopProtectedUserSettings
import com.wire.kalium.persistence.db.GlobalDatabaseBuilder
import com.wire.kalium.persistence.db.PlatformDatabaseData
import com.wire.kalium.persistence.db.StorageData
import com.wire.kalium.persistence.db.desktopDatabaseNeedsExistingKey
import com.wire.kalium.persistence.db.globalDatabaseProvider
import com.wire.kalium.persistence.db.migrateDesktopUserDatabase
import com.wire.kalium.persistence.kmmSettings.GlobalPrefProvider
import com.wire.kalium.persistence.util.FileNameUtil
import com.wire.kalium.usernetwork.di.PlatformUserAuthenticatedNetworkProvider
import com.wire.kalium.usernetwork.di.UserAuthenticatedNetworkProvider
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files

/**
 * @sample samples.logic.CoreLogicSamples.versionedAuthScope
 */
public actual class CoreLogic(
    rootPath: String,
    kaliumConfigs: KaliumConfigs,
    userAgent: String,
    useInMemoryStorage: Boolean = false,
    private val userAuthenticatedNetworkProvider: UserAuthenticatedNetworkProvider = PlatformUserAuthenticatedNetworkProvider(),
    private val protectDesktopStorage: Boolean = false,
) : CoreLogicCommon(
    rootPath = rootPath,
    kaliumConfigs = kaliumConfigs,
    userAgent = userAgent
) {

    private val protectedDisk = protectDesktopStorage && !useInMemoryStorage
    private val globalDirectory = File(rootPath, "global-storage")
    private val globalFile = globalDirectory.resolve(FileNameUtil.globalDBName())

    init {
        require(!protectedDisk || kaliumConfigs.shouldEncryptData())
        if (protectedDisk && !Files.notExists(globalFile.toPath())) {
            val legacy = File(rootPath, FileNameUtil.appPrefFile())
            check(legacy.isFile || File(rootPath, legacy.name + ".encrypted").isFile) {
                "Missing global preferences for existing database"
            }
        }
    }

    actual override val globalPreferences: GlobalPrefProvider =
        GlobalPrefProvider(
            rootPath = rootPath,
            shouldEncryptData = kaliumConfigs.shouldEncryptData(),
            protectSettings = protectedDisk
        )

    actual override val globalDatabaseBuilder: GlobalDatabaseBuilder = globalDatabaseProvider(
        platformDatabaseData = PlatformDatabaseData(
            storageData = if (useInMemoryStorage) {
                StorageData.InMemory
            } else {
                StorageData.FileBacked(globalDirectory)
            }
        ),
        passphrase = if (protectedDisk) {
            val helper = SecurityHelperImpl(globalPreferences.passphraseStorage)
            if (desktopDatabaseNeedsExistingKey(globalFile.toPath())) {
                checkNotNull(helper.globalDBSecretOrNull()) { "Missing global database key" }
            } else {
                helper.globalDBKeyMaterial(databaseExists = false).rawKey
            }
        } else null,
        queriesContext = KaliumDispatcherImpl.io
    )

    init {
        if (protectedDisk) migrateStoredAccounts(rootPath)
    }

    private fun migrateStoredAccounts(rootPath: String) {
        val accounts = runBlocking { globalDatabaseBuilder.accountsDAO.allAccountList() }.map { it.userIDEntity }
        val root = File(rootPath).canonicalFile.toPath()
        val legacyNames = accounts.groupingBy(FileNameUtil::userPrefFile).eachCount()
        val helper = SecurityHelperImpl(globalPreferences.passphraseStorage)
        for (entity in accounts) {
            val user = UserId(entity.value, entity.domain)
            val expected = root.resolve(entity.domain).resolve(entity.value).resolve("storage")
            val directory = expected.toFile().canonicalFile.toPath()
            check(directory == expected && directory.startsWith(root) && directory.nameCount == root.nameCount + 3) {
                "Invalid account storage location"
            }
            val file = directory.resolve("main.db")
            if (desktopDatabaseNeedsExistingKey(file)) {
                checkNotNull(helper.userDBOrSecretNull(user)) { "Missing user database key" }
            } else if (!Files.notExists(file)) {
                migrateDesktopUserDatabase(file, helper.userDBSecret(user, databaseExists = false))
            }
            val legacy = FileNameUtil.userPrefFile(entity)
            check(legacyNames[legacy] == 1 || Files.notExists(root.resolve(legacy))) {
                "Ambiguous owner of legacy user preferences"
            }
            DesktopProtectedUserSettings(rootPath, directory.toString()).migrateExisting(entity)
        }
    }

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
            protectedDisk,
        )
    }
    public actual override val audioNormalizedLoudnessBuilder: AudioNormalizedLoudnessBuilder = AudioNormalizedLoudnessBuilderImpl()
}

@Suppress("MayBeConst")
internal actual val clientPlatform: String = "jvm"
