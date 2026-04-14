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
import com.wire.kalium.logic.feature.session.UpgradeCurrentSessionUseCase
import com.wire.kalium.logic.util.SecurityHelperImpl
import com.wire.kalium.persistence.kmmSettings.GlobalPrefProvider
import com.wire.kalium.userstorage.di.UserStorage
import com.wire.kalium.util.DelicateKaliumApi

@Suppress("LongParameterList")
public class BackupScope internal constructor(
    private val userId: UserId,
    private val clientIdProvider: CurrentClientIdProvider,
    private val userRepository: UserRepository,
    private val clientRepository: ClientRepository,
    private val eventRepository: EventRepository,
    private val upgradeCurrentSession: UpgradeCurrentSessionUseCase,
    private val kaliumFileSystem: KaliumFileSystem,
    private val userStorage: UserStorage,
    private val cryptoTransactionProvider: CryptoTransactionProvider,
    internal val globalPreferences: GlobalPrefProvider,
    private val cryptoStateBackupRemoteRepository: CryptoStateBackupRemoteRepository,
    private val rootPathsProvider: RootPathsProvider,
) {
    private val securityHelper = SecurityHelperImpl(globalPreferences.passphraseStorage)

    public val create: CreateBackupUseCase
        get() = CreateBackupUseCaseImpl(
            userId,
            clientIdProvider,
            userRepository,
            kaliumFileSystem,
            userStorage.database.databaseExporter,
            securityHelper = securityHelper
        )

    public val verify: VerifyBackupUseCase
        get() = VerifyBackupUseCaseImpl(userId, kaliumFileSystem)

    public val restore: RestoreBackupUseCase
        get() = RestoreBackupUseCaseImpl(
            userStorage.database.databaseImporter,
            kaliumFileSystem,
            userId,
            userRepository,
            clientIdProvider,
        )

    @DelicateKaliumApi("this is NOT a backup feature, but a feature to create an unencrypted and obfuscated copy of the database")
    public val createUnEncryptedCopy: CreateObfuscatedCopyUseCase
        get() = CreateObfuscatedCopyUseCase(
            userId,
            clientIdProvider,
            userRepository,
            kaliumFileSystem,
            userStorage.database.obfuscatedCopyExporter,
        )

    internal val backupCryptoDB: BackupCryptoDBUseCase by lazy {
        BackupCryptoDBUseCaseImpl(
            userId,
            cryptoTransactionProvider,
            eventRepository,
            kaliumFileSystem,
        )
    }

    public val backupAndUploadCryptoState: BackupAndUploadCryptoStateUseCase
        get() = BackupAndUploadCryptoStateUseCaseImpl(
            backupCryptoDBUseCase = backupCryptoDB,
            cryptoStateBackupRemoteRepository = cryptoStateBackupRemoteRepository,
            kaliumFileSystem = kaliumFileSystem,
            currentClientIdProvider = clientIdProvider,
        )

    private val downloadCryptoState: DownloadCryptoStateUseCase
        get() = DownloadCryptoStateUseCaseImpl(
            userId,
            cryptoStateBackupRemoteRepository,
            kaliumFileSystem,
        )

    private val extractCryptoState: ExtractCryptoStateUseCase
        get() = ExtractCryptoStateUseCaseImpl(
            kaliumFileSystem = kaliumFileSystem,
        )

    public val setLastDeviceId: SetLastDeviceIdUseCase
        get() = SetLastDeviceIdUseCaseImpl(
            cryptoStateBackupRemoteRepository = cryptoStateBackupRemoteRepository,
        )

    private val applyCryptoState: ApplyCryptoStateUseCase
        get() = ApplyCryptoStateUseCaseImpl(
            userId = userId,
            rootPathsProvider = rootPathsProvider,
            kaliumFileSystem = kaliumFileSystem,
            securityHelper = securityHelper,
            clientRepository = clientRepository,
            eventRepository = eventRepository
        )

    public val restoreCryptoState: RestoreCryptoStateUseCase
        get() = RestoreCryptoStateUseCaseImpl(
            downloadCryptoState = downloadCryptoState,
            extractCryptoState = extractCryptoState,
            setLastDeviceId = setLastDeviceId,
            clientRepository = clientRepository,
            upgradeCurrentSession = upgradeCurrentSession,
            applyCryptoState = applyCryptoState,
        )
}
