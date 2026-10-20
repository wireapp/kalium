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
@file:Suppress("konsist.useCasesShouldNotAccessDaoLayerDirectly")

@file:OptIn(DelicateKaliumApi::class)

package com.wire.kalium.logic.feature.backup

import com.wire.kalium.logic.data.asset.KaliumFileSystem
import com.wire.kalium.logic.data.backup.CryptoStateBackupRemoteRepository
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.client.CryptoTransactionProvider
import com.wire.kalium.logic.data.event.EventRepository
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.di.RootPathsProvider
import com.wire.kalium.logic.feature.UserSessionScopeProvider
import com.wire.kalium.logic.feature.session.UpgradeCurrentSessionUseCase
import com.wire.kalium.logic.util.SecurityHelper
import com.wire.kalium.logic.util.SecurityHelperImpl
import com.wire.kalium.persistence.backup.DatabaseExporter
import com.wire.kalium.persistence.backup.DatabaseImporter
import com.wire.kalium.persistence.backup.ObfuscatedCopyExporter
import com.wire.kalium.persistence.dbPassphrase.PassphraseStorage
import com.wire.kalium.persistence.kmmSettings.GlobalPrefProvider
import com.wire.kalium.userstorage.di.UserStorage
import com.wire.kalium.util.DelicateKaliumApi
import dev.zacsweers.metro.GraphExtension
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.Qualifier
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.sync.Mutex

internal abstract class BackupFacadeLifetime private constructor()

@Qualifier
internal annotation class LegacyBackupBinding(val value: String)

internal interface LegacyBackupDependencies {
    val userId: UserId
    val clientIdProvider: CurrentClientIdProvider
    val userRepository: UserRepository
    val clientRepository: ClientRepository
    val eventRepository: EventRepository
    val upgradeCurrentSession: UpgradeCurrentSessionUseCase
    val kaliumFileSystem: KaliumFileSystem
    val userStorage: UserStorage
    val cryptoTransactionProvider: CryptoTransactionProvider
    val globalPreferences: GlobalPrefProvider
    val cryptoStateBackupRemoteRepository: CryptoStateBackupRemoteRepository
    val rootPathsProvider: RootPathsProvider
    val originatingScope: CoroutineScope
    val userSessionScopeProvider: UserSessionScopeProvider
    val passphraseStorage: PassphraseStorage get() = globalPreferences.passphraseStorage
    fun databaseExporter(): DatabaseExporter = userStorage.database.databaseExporter
    fun databaseImporter(): DatabaseImporter = userStorage.database.databaseImporter
    fun obfuscatedCopyExporter(): ObfuscatedCopyExporter = userStorage.database.obfuscatedCopyExporter
}

internal interface LegacyBackupApi {
    @get:LegacyBackupBinding("userId")
    val backupUserId: UserId

    @get:LegacyBackupBinding("clientIdProvider")
    val backupClientIdProvider: CurrentClientIdProvider

    @get:LegacyBackupBinding("userRepository")
    val backupUserRepository: UserRepository

    @get:LegacyBackupBinding("clientRepository")
    val backupClientRepository: ClientRepository

    @get:LegacyBackupBinding("eventRepository")
    val backupEventRepository: EventRepository

    @get:LegacyBackupBinding("upgradeCurrentSession")
    val backupUpgradeCurrentSession: UpgradeCurrentSessionUseCase

    @get:LegacyBackupBinding("kaliumFileSystem")
    val backupKaliumFileSystem: KaliumFileSystem

    @get:LegacyBackupBinding("userStorage")
    val backupUserStorage: UserStorage

    @get:LegacyBackupBinding("cryptoTransactionProvider")
    val backupCryptoTransactionProvider: CryptoTransactionProvider

    @get:LegacyBackupBinding("globalPreferences")
    val backupGlobalPreferences: GlobalPrefProvider

    @get:LegacyBackupBinding("cryptoStateBackupRemoteRepository")
    val backupCryptoStateBackupRemoteRepository: CryptoStateBackupRemoteRepository

    @get:LegacyBackupBinding("rootPathsProvider")
    val backupRootPathsProvider: RootPathsProvider

    @get:LegacyBackupBinding("originatingScope")
    val backupOriginatingScope: CoroutineScope

    @get:LegacyBackupBinding("userSessionScopeProvider")
    val backupUserSessionScopeProvider: UserSessionScopeProvider

    @get:LegacyBackupBinding("securityHelper")
    val backupSecurityHelper: SecurityHelper

    @get:LegacyBackupBinding("create")
    val backupCreate: CreateBackupUseCase

    @get:LegacyBackupBinding("verify")
    val backupVerify: VerifyBackupUseCase

    @get:LegacyBackupBinding("restore")
    val backupRestore: RestoreBackupUseCase

    @get:LegacyBackupBinding("createUnEncryptedCopy")
    val backupCreateUnEncryptedCopy: CreateObfuscatedCopyUseCase

    @get:LegacyBackupBinding("backupCryptoDB")
    val backupBackupCryptoDB: BackupCryptoDBUseCase

    @get:LegacyBackupBinding("backupAndUploadCryptoStateMutex")
    val backupBackupAndUploadCryptoStateMutex: Mutex

    @get:LegacyBackupBinding("backupAndUploadCryptoState")
    val backupBackupAndUploadCryptoState: BackupAndUploadCryptoStateUseCase

    @get:LegacyBackupBinding("downloadCryptoState")
    val backupDownloadCryptoState: DownloadCryptoStateUseCase

    @get:LegacyBackupBinding("extractCryptoState")
    val backupExtractCryptoState: ExtractCryptoStateUseCase

    @get:LegacyBackupBinding("setLastDeviceId")
    val backupSetLastDeviceId: SetLastDeviceIdUseCase

    @get:LegacyBackupBinding("applyCryptoState")
    val backupApplyCryptoState: ApplyCryptoStateUseCase

    @get:LegacyBackupBinding("restoreCryptoState")
    val backupRestoreCryptoState: RestoreCryptoStateUseCase

    @get:LegacyBackupBinding("passphraseStorage")
    val backupPassphraseStorage: PassphraseStorage

    @get:LegacyBackupBinding("databaseExporter")
    val backupDatabaseExporter: DatabaseExporter

    @get:LegacyBackupBinding("databaseImporter")
    val backupDatabaseImporter: DatabaseImporter

    @get:LegacyBackupBinding("obfuscatedCopyExporter")
    val backupObfuscatedCopyExporter: ObfuscatedCopyExporter

}

/** Individual bindings retained only by an existing fresh facade owner. */
@Suppress("TooManyFunctions", "LongParameterList", "LargeClass")
internal interface LegacyBackupBindings : LegacyBackupApi {
    @Provides
    @SingleIn(BackupFacadeLifetime::class)
    @LegacyBackupBinding("userId")
    fun provideBackupUserId(dependencies: LegacyBackupDependencies): UserId =
        dependencies.userId

    @Provides
    @SingleIn(BackupFacadeLifetime::class)
    @LegacyBackupBinding("clientIdProvider")
    fun provideBackupClientIdProvider(dependencies: LegacyBackupDependencies): CurrentClientIdProvider =
        dependencies.clientIdProvider

    @Provides
    @SingleIn(BackupFacadeLifetime::class)
    @LegacyBackupBinding("userRepository")
    fun provideBackupUserRepository(dependencies: LegacyBackupDependencies): UserRepository =
        dependencies.userRepository

    @Provides
    @SingleIn(BackupFacadeLifetime::class)
    @LegacyBackupBinding("clientRepository")
    fun provideBackupClientRepository(dependencies: LegacyBackupDependencies): ClientRepository =
        dependencies.clientRepository

    @Provides
    @SingleIn(BackupFacadeLifetime::class)
    @LegacyBackupBinding("eventRepository")
    fun provideBackupEventRepository(dependencies: LegacyBackupDependencies): EventRepository =
        dependencies.eventRepository

    @Provides
    @SingleIn(BackupFacadeLifetime::class)
    @LegacyBackupBinding("upgradeCurrentSession")
    fun provideBackupUpgradeCurrentSession(dependencies: LegacyBackupDependencies): UpgradeCurrentSessionUseCase =
        dependencies.upgradeCurrentSession

    @Provides
    @SingleIn(BackupFacadeLifetime::class)
    @LegacyBackupBinding("kaliumFileSystem")
    fun provideBackupKaliumFileSystem(dependencies: LegacyBackupDependencies): KaliumFileSystem =
        dependencies.kaliumFileSystem

    @Provides
    @SingleIn(BackupFacadeLifetime::class)
    @LegacyBackupBinding("userStorage")
    fun provideBackupUserStorage(dependencies: LegacyBackupDependencies): UserStorage =
        dependencies.userStorage

    @Provides
    @SingleIn(BackupFacadeLifetime::class)
    @LegacyBackupBinding("cryptoTransactionProvider")
    fun provideBackupCryptoTransactionProvider(dependencies: LegacyBackupDependencies): CryptoTransactionProvider =
        dependencies.cryptoTransactionProvider

    @Provides
    @SingleIn(BackupFacadeLifetime::class)
    @LegacyBackupBinding("globalPreferences")
    fun provideBackupGlobalPreferences(dependencies: LegacyBackupDependencies): GlobalPrefProvider =
        dependencies.globalPreferences

    @Provides
    @SingleIn(BackupFacadeLifetime::class)
    @LegacyBackupBinding("cryptoStateBackupRemoteRepository")
    fun provideBackupCryptoStateBackupRemoteRepository(dependencies: LegacyBackupDependencies): CryptoStateBackupRemoteRepository =
        dependencies.cryptoStateBackupRemoteRepository

    @Provides
    @SingleIn(BackupFacadeLifetime::class)
    @LegacyBackupBinding("rootPathsProvider")
    fun provideBackupRootPathsProvider(dependencies: LegacyBackupDependencies): RootPathsProvider =
        dependencies.rootPathsProvider

    @Provides
    @SingleIn(BackupFacadeLifetime::class)
    @LegacyBackupBinding("originatingScope")
    fun provideBackupOriginatingScope(dependencies: LegacyBackupDependencies): CoroutineScope =
        dependencies.originatingScope

    @Provides
    @SingleIn(BackupFacadeLifetime::class)
    @LegacyBackupBinding("userSessionScopeProvider")
    fun provideBackupUserSessionScopeProvider(dependencies: LegacyBackupDependencies): UserSessionScopeProvider =
        dependencies.userSessionScopeProvider

    @Provides
    @LegacyBackupBinding("passphraseStorage")
    fun provideBackupPassphraseStorage(dependencies: LegacyBackupDependencies): PassphraseStorage = dependencies.passphraseStorage

    @Provides
    @LegacyBackupBinding("databaseExporter")
    fun provideBackupDatabaseExporter(dependencies: LegacyBackupDependencies): DatabaseExporter = dependencies.databaseExporter()

    @Provides
    @LegacyBackupBinding("databaseImporter")
    fun provideBackupDatabaseImporter(dependencies: LegacyBackupDependencies): DatabaseImporter = dependencies.databaseImporter()

    @Provides
    @LegacyBackupBinding("obfuscatedCopyExporter")
    fun provideBackupObfuscatedCopyExporter(
        dependencies: LegacyBackupDependencies,
    ): ObfuscatedCopyExporter = dependencies.obfuscatedCopyExporter()

    @Provides
    @SingleIn(BackupFacadeLifetime::class)
    @LegacyBackupBinding("securityHelper")
    fun provideBackupSecurityHelper(
        @LegacyBackupBinding("passphraseStorage")
        passphraseStorage: PassphraseStorage,
    ): SecurityHelper =
        SecurityHelperImpl(passphraseStorage)

    @Provides
    @LegacyBackupBinding("create")
    fun provideBackupCreate(
        @LegacyBackupBinding("userId")
        userId: UserId,
        @LegacyBackupBinding("clientIdProvider")
        clientIdProvider: CurrentClientIdProvider,
        @LegacyBackupBinding("userRepository")
        userRepository: UserRepository,
        @LegacyBackupBinding("kaliumFileSystem")
        kaliumFileSystem: KaliumFileSystem,
        @LegacyBackupBinding("securityHelper")
        securityHelper: Lazy<SecurityHelper>,
        @LegacyBackupBinding("databaseExporter")
        databaseExporter: DatabaseExporter,
    ): CreateBackupUseCase =
        CreateBackupUseCaseImpl(
            userId,
            clientIdProvider,
            userRepository,
            kaliumFileSystem,
            databaseExporter,
            securityHelper = securityHelper.value
        )

    @Provides
    @LegacyBackupBinding("verify")
    fun provideBackupVerify(
        @LegacyBackupBinding("userId")
        userId: UserId,
        @LegacyBackupBinding("kaliumFileSystem")
        kaliumFileSystem: KaliumFileSystem,
    ): VerifyBackupUseCase =
        VerifyBackupUseCaseImpl(userId, kaliumFileSystem)

    @Provides
    @LegacyBackupBinding("restore")
    fun provideBackupRestore(
        @LegacyBackupBinding("userId")
        userId: UserId,
        @LegacyBackupBinding("clientIdProvider")
        clientIdProvider: CurrentClientIdProvider,
        @LegacyBackupBinding("userRepository")
        userRepository: UserRepository,
        @LegacyBackupBinding("kaliumFileSystem")
        kaliumFileSystem: KaliumFileSystem,
        @LegacyBackupBinding("databaseImporter")
        databaseImporter: DatabaseImporter,
    ): RestoreBackupUseCase =
        RestoreBackupUseCaseImpl(
            databaseImporter,
            kaliumFileSystem,
            userId,
            userRepository,
            clientIdProvider,
        )

    @Provides
    @LegacyBackupBinding("createUnEncryptedCopy")
    fun provideBackupCreateUnEncryptedCopy(
        @LegacyBackupBinding("userId")
        userId: UserId,
        @LegacyBackupBinding("clientIdProvider")
        clientIdProvider: CurrentClientIdProvider,
        @LegacyBackupBinding("userRepository")
        userRepository: UserRepository,
        @LegacyBackupBinding("kaliumFileSystem")
        kaliumFileSystem: KaliumFileSystem,
        @LegacyBackupBinding("obfuscatedCopyExporter")
        obfuscatedCopyExporter: ObfuscatedCopyExporter,
    ): CreateObfuscatedCopyUseCase =
        CreateObfuscatedCopyUseCase(
            userId,
            clientIdProvider,
            userRepository,
            kaliumFileSystem,
            obfuscatedCopyExporter,
        )

    @Provides
    @SingleIn(BackupFacadeLifetime::class)
    @LegacyBackupBinding("backupCryptoDB")
    fun provideBackupBackupCryptoDB(
        @LegacyBackupBinding("userId")
        userId: UserId,
        @LegacyBackupBinding("eventRepository")
        eventRepository: EventRepository,
        @LegacyBackupBinding("kaliumFileSystem")
        kaliumFileSystem: KaliumFileSystem,
        @LegacyBackupBinding("cryptoTransactionProvider")
        cryptoTransactionProvider: CryptoTransactionProvider,
    ): BackupCryptoDBUseCase =
        BackupCryptoDBUseCaseImpl(
            userId,
            cryptoTransactionProvider,
            eventRepository,
            kaliumFileSystem,
        )

    @Provides
    @SingleIn(BackupFacadeLifetime::class)
    @LegacyBackupBinding("backupAndUploadCryptoStateMutex")
    fun provideBackupBackupAndUploadCryptoStateMutex(): Mutex =
        Mutex()

    @Provides
    @SingleIn(BackupFacadeLifetime::class)
    @LegacyBackupBinding("backupAndUploadCryptoState")
    fun provideBackupBackupAndUploadCryptoState(
        @LegacyBackupBinding("clientIdProvider")
        clientIdProvider: CurrentClientIdProvider,
        @LegacyBackupBinding("kaliumFileSystem")
        kaliumFileSystem: KaliumFileSystem,
        @LegacyBackupBinding("cryptoStateBackupRemoteRepository")
        cryptoStateBackupRemoteRepository: CryptoStateBackupRemoteRepository,
        @LegacyBackupBinding("backupCryptoDB")
        backupCryptoDB: Lazy<BackupCryptoDBUseCase>,
        @LegacyBackupBinding("backupAndUploadCryptoStateMutex")
        backupAndUploadCryptoStateMutex: Lazy<Mutex>,
    ): BackupAndUploadCryptoStateUseCase =
        BackupAndUploadCryptoStateUseCaseImpl(
            backupCryptoDBUseCase = backupCryptoDB.value,
            cryptoStateBackupRemoteRepository = cryptoStateBackupRemoteRepository,
            kaliumFileSystem = kaliumFileSystem,
            currentClientIdProvider = clientIdProvider,
            executionMutex = backupAndUploadCryptoStateMutex.value,
        )

    @Provides
    @LegacyBackupBinding("downloadCryptoState")
    fun provideBackupDownloadCryptoState(
        @LegacyBackupBinding("userId")
        userId: UserId,
        @LegacyBackupBinding("kaliumFileSystem")
        kaliumFileSystem: KaliumFileSystem,
        @LegacyBackupBinding("cryptoStateBackupRemoteRepository")
        cryptoStateBackupRemoteRepository: CryptoStateBackupRemoteRepository,
    ): DownloadCryptoStateUseCase =
        DownloadCryptoStateUseCaseImpl(
            userId,
            cryptoStateBackupRemoteRepository,
            kaliumFileSystem,
        )

    @Provides
    @LegacyBackupBinding("extractCryptoState")
    fun provideBackupExtractCryptoState(
        @LegacyBackupBinding("kaliumFileSystem")
        kaliumFileSystem: KaliumFileSystem,
    ): ExtractCryptoStateUseCase =
        ExtractCryptoStateUseCaseImpl(
            kaliumFileSystem = kaliumFileSystem,
        )

    @Provides
    @LegacyBackupBinding("setLastDeviceId")
    fun provideBackupSetLastDeviceId(
        @LegacyBackupBinding("cryptoStateBackupRemoteRepository")
        cryptoStateBackupRemoteRepository: CryptoStateBackupRemoteRepository,
    ): SetLastDeviceIdUseCase =
        SetLastDeviceIdUseCaseImpl(
            cryptoStateBackupRemoteRepository = cryptoStateBackupRemoteRepository,
        )

    @Provides
    @LegacyBackupBinding("applyCryptoState")
    fun provideBackupApplyCryptoState(
        @LegacyBackupBinding("userId")
        userId: UserId,
        @LegacyBackupBinding("clientRepository")
        clientRepository: ClientRepository,
        @LegacyBackupBinding("eventRepository")
        eventRepository: EventRepository,
        @LegacyBackupBinding("kaliumFileSystem")
        kaliumFileSystem: KaliumFileSystem,
        @LegacyBackupBinding("rootPathsProvider")
        rootPathsProvider: RootPathsProvider,
        @LegacyBackupBinding("originatingScope")
        originatingScope: CoroutineScope,
        @LegacyBackupBinding("userSessionScopeProvider")
        userSessionScopeProvider: UserSessionScopeProvider,
        @LegacyBackupBinding("securityHelper")
        securityHelper: Lazy<SecurityHelper>,
    ): ApplyCryptoStateUseCase =
        ApplyCryptoStateUseCaseImpl(
            userId = userId,
            rootPathsProvider = rootPathsProvider,
            kaliumFileSystem = kaliumFileSystem,
            securityHelper = securityHelper.value,
            clientRepository = clientRepository,
            eventRepository = eventRepository,
            originatingScope = originatingScope,
            userSessionScopeProvider = userSessionScopeProvider,
        )

    @Provides
    @LegacyBackupBinding("restoreCryptoState")
    fun provideBackupRestoreCryptoState(
        @LegacyBackupBinding("clientRepository")
        clientRepository: ClientRepository,
        @LegacyBackupBinding("upgradeCurrentSession")
        upgradeCurrentSession: UpgradeCurrentSessionUseCase,
        @LegacyBackupBinding("downloadCryptoState")
        downloadCryptoState: Lazy<DownloadCryptoStateUseCase>,
        @LegacyBackupBinding("extractCryptoState")
        extractCryptoState: Lazy<ExtractCryptoStateUseCase>,
        @LegacyBackupBinding("setLastDeviceId")
        setLastDeviceId: Lazy<SetLastDeviceIdUseCase>,
        @LegacyBackupBinding("applyCryptoState")
        applyCryptoState: Lazy<ApplyCryptoStateUseCase>,
    ): RestoreCryptoStateUseCase =
        RestoreCryptoStateUseCaseImpl(
            downloadCryptoState = downloadCryptoState.value,
            extractCryptoState = extractCryptoState.value,
            setLastDeviceId = setLastDeviceId.value,
            clientRepository = clientRepository,
            upgradeCurrentSession = upgradeCurrentSession,
            applyCryptoState = applyCryptoState.value,
        )
}

@GraphExtension(BackupFacadeLifetime::class)
internal interface LegacyBackupGraph : LegacyBackupBindings {
    @GraphExtension.Factory
    interface Factory {
        fun create(@Provides dependencies: LegacyBackupDependencies): LegacyBackupGraph
    }
}
