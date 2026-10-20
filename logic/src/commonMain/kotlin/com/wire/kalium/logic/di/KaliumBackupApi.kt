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

package com.wire.kalium.logic.di

import com.wire.kalium.logic.feature.backup.BackupAndUploadCryptoStateUseCase
import com.wire.kalium.logic.feature.backup.CreateBackupUseCase
import com.wire.kalium.logic.feature.backup.CreateObfuscatedCopyUseCase
import com.wire.kalium.logic.feature.backup.LegacyBackupDependencies
import com.wire.kalium.logic.feature.backup.LegacyBackupGraph
import com.wire.kalium.logic.feature.backup.RestoreBackupUseCase
import com.wire.kalium.logic.feature.backup.SetLastDeviceIdUseCase
import com.wire.kalium.logic.feature.backup.VerifyBackupUseCase
import com.wire.kalium.util.DelicateKaliumApi

/** Individual bindings preserve their existing owner; this API does not create a consumer. */
public interface KaliumBackupApi {
    public val backupCreate: CreateBackupUseCase
    public val backupVerify: VerifyBackupUseCase
    public val backupRestore: RestoreBackupUseCase

    @DelicateKaliumApi("Creates an unencrypted obfuscated database copy; this is not a backup.")
    public val backupCreateUnEncryptedCopy: CreateObfuscatedCopyUseCase
    public val backupBackupAndUploadCryptoState: BackupAndUploadCryptoStateUseCase
    public val backupSetLastDeviceId: SetLastDeviceIdUseCase

    /** Every call creates a fresh graph with the original per-facade captures and caches. */
    public fun interface Factory {
        public fun create(): KaliumBackupApi
    }
}

/** Same generated graph/capture boundary as the compatibility facade. */
internal fun createBackupApi(
    factory: LegacyBackupGraph.Factory,
    dependencies: LegacyBackupDependencies,
): KaliumBackupApi = factory.create(dependencies).also {
        // Match BackupScope initialization before publishing this fresh API.
        it.backupSecurityHelper
        it.backupBackupAndUploadCryptoStateMutex
    }
