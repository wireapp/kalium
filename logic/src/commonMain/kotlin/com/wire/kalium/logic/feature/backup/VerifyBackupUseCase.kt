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

package com.wire.kalium.logic.feature.backup

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.asset.KaliumFileSystem
import com.wire.kalium.common.functional.fold
import com.wire.kalium.logic.util.checkIfCompressedFileContainsFileTypes
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.withContext
import okio.Path

interface VerifyBackupUseCase {
    /**
     * Checks whether the given backup file is encrypted and requires a password.
     * @param compressedBackupFilePath The absolute file system path to the compressed file.
     * @return A [VerifyBackupResult] indicating whether the given backup file contains encrypted file or not or failure.
     */
    suspend operator fun invoke(compressedBackupFilePath: Path): VerifyBackupResult
}

internal class VerifyBackupUseCaseImpl(
    private val kaliumFileSystem: KaliumFileSystem,
    private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl
) : VerifyBackupUseCase {

    override suspend operator fun invoke(compressedBackupFilePath: Path): VerifyBackupResult = withContext(dispatchers.io) {
        checkIfCompressedFileContainsFileTypes(
            compressedBackupFilePath,
            kaliumFileSystem,
            BackupConstants.ACCEPTED_EXTENSIONS
        ).fold({
            VerifyBackupResult.Failure.Generic(it)
        }, {
            when {
                it.keys.any { it !in BackupConstants.ACCEPTED_EXTENSIONS } -> VerifyBackupResult.Failure.InvalidBackupFile

                it[BackupConstants.BACKUP_ENCRYPTED_EXTENSION] == true ->
                    VerifyBackupResult.Success.Encrypted

                it[BackupConstants.BACKUP_DB_EXTENSION] == true && it[BackupConstants.BACKUP_METADATA_EXTENSION] == true ->
                    VerifyBackupResult.Success.NotEncrypted

                it[BackupConstants.BACKUP_METADATA_EXTENSION] == true -> VerifyBackupResult.Success.Web
                else ->
                    VerifyBackupResult.Failure.InvalidBackupFile
            }
        })
    }
}

sealed class VerifyBackupResult {
    sealed class Success : VerifyBackupResult() {
        data object Encrypted : Success()
        data object NotEncrypted : Success()
        data object Web : Success()
    }

    sealed class Failure : VerifyBackupResult() {
        data object InvalidBackupFile : Failure()
        data class Generic(val error: CoreFailure) : Failure()
    }
}
