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

import com.wire.kalium.persistence.kmmSettings.GlobalPrefProvider
import com.wire.kalium.util.DelicateKaliumApi

/** Compatibility facade over its own fresh operation graph. */
public class BackupScope internal constructor(private val api: LegacyBackupApi) {
    init {
        // Preserve eager per-facade helper and mutex creation; use cases remain lazy.
        api.backupSecurityHelper
        api.backupBackupAndUploadCryptoStateMutex
    }

    public val create: CreateBackupUseCase
        get() = api.backupCreate

    public val verify: VerifyBackupUseCase
        get() = api.backupVerify

    public val restore: RestoreBackupUseCase
        get() = api.backupRestore

    @DelicateKaliumApi("this is NOT a backup feature, but a feature to create an unencrypted and obfuscated copy of the database")
    public val createUnEncryptedCopy: CreateObfuscatedCopyUseCase
        get() = api.backupCreateUnEncryptedCopy

    internal val backupCryptoDB: BackupCryptoDBUseCase
        get() = api.backupBackupCryptoDB

    public val backupAndUploadCryptoState: BackupAndUploadCryptoStateUseCase
        get() = api.backupBackupAndUploadCryptoState

    public val setLastDeviceId: SetLastDeviceIdUseCase
        get() = api.backupSetLastDeviceId

    public val restoreCryptoState: RestoreCryptoStateUseCase
        get() = api.backupRestoreCryptoState

    internal val globalPreferences: GlobalPrefProvider
        get() = api.backupGlobalPreferences

}
