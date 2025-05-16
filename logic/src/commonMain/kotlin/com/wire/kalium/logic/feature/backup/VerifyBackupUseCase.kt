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

import com.wire.backup.ingest.BackupPeekResult
import com.wire.backup.ingest.isCreatedBySameUser
import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.fold
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.data.asset.KaliumFileSystem
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.backup.mapper.toBackupQualifiedId
import com.wire.kalium.logic.feature.backup.provider.MPBackupImporterProvider
import com.wire.kalium.logic.feature.backup.provider.MPBackupImporterProviderImpl
import com.wire.kalium.logic.util.checkIfCompressedFileContainsFileTypes
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path
import okio.SYSTEM

interface VerifyBackupUseCase {
    /**
     * Checks whether the given backup file is encrypted and requires a password.
     * @param compressedBackupFilePath The absolute file system path to the compressed file.
     * @return A [VerifyBackupResult] indicating whether the given backup file contains encrypted file or not or failure.
     */
    suspend operator fun invoke(compressedBackupFilePath: Path): VerifyBackupResult
}

internal class VerifyBackupUseCaseImpl(
    private val userId: UserId,
    private val kaliumFileSystem: KaliumFileSystem,
    private val fileSystem: FileSystem = FileSystem.SYSTEM,
    private val backupImporterProvider: MPBackupImporterProvider = MPBackupImporterProviderImpl(fileSystem),
    private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl
) : VerifyBackupUseCase {

    override suspend operator fun invoke(compressedBackupFilePath: Path): VerifyBackupResult = withContext(dispatchers.io) {
        when (val result = verifyMpBackupFile(compressedBackupFilePath)) {
            VerifyBackupResult.Failure.InvalidBackupFile -> verifyRegularBackupFile(compressedBackupFilePath)
            else -> result
        }
    }

    private fun verifyRegularBackupFile(
        compressedBackupFilePath: Path,
    ) = checkIfCompressedFileContainsFileTypes(
        compressedBackupFilePath,
        kaliumFileSystem,
        BackupConstants.ACCEPTED_EXTENSIONS
    ).fold(
        {
            VerifyBackupResult.Failure.Generic(it)
        },
        { result ->
            when {
                result.keys.any { it !in BackupConstants.ACCEPTED_EXTENSIONS } -> VerifyBackupResult.Failure.InvalidBackupFile

                result[BackupConstants.BACKUP_ENCRYPTED_EXTENSION] == true ->
                    VerifyBackupResult.AndroidBackup(true)

                result[BackupConstants.BACKUP_DB_EXTENSION] == true && result[BackupConstants.BACKUP_METADATA_EXTENSION] == true ->
                    VerifyBackupResult.AndroidBackup(false)

                else -> VerifyBackupResult.Failure.InvalidBackupFile
            }
        }
    )

    private suspend fun verifyMpBackupFile(
        backupFilePath: Path,
    ): VerifyBackupResult {

        val mpBackupImporter = backupImporterProvider.providePeekImporter()
        val peek = mpBackupImporter.peekBackupFile(backupFilePath.toString())

        kaliumLogger.i("Peek result: $peek")

        return when (peek) {
            BackupPeekResult.Failure.UnknownFormat -> VerifyBackupResult.Failure.InvalidBackupFile
            is BackupPeekResult.Failure.UnsupportedVersion -> VerifyBackupResult.Failure.UnsupportedVersion(peek.backupVersion)
            is BackupPeekResult.Success ->
                if (peek.isCreatedBySameUser(userId.toBackupQualifiedId())) {
                    VerifyBackupResult.MultiPlatformBackup(peek.isEncrypted)
                } else {
                    VerifyBackupResult.Failure.InvalidUserId
                }
        }
    }
}

enum class BackupFileFormat {
    ANDROID, MULTIPLATFORM
}

sealed class VerifyBackupResult {

    @Suppress("FunctionName")
    companion object {
        internal fun AndroidBackup(isEncrypted: Boolean) = Success(BackupFileFormat.ANDROID, isEncrypted)
        internal fun MultiPlatformBackup(isEncrypted: Boolean) = Success(BackupFileFormat.MULTIPLATFORM, isEncrypted)
    }

    data class Success(val format: BackupFileFormat, val isEncrypted: Boolean) : VerifyBackupResult()

    sealed class Failure : VerifyBackupResult() {
        data object InvalidBackupFile : Failure()
        data class UnsupportedVersion(val version: String) : Failure()
        data class Generic(val error: CoreFailure) : Failure()
        data object InvalidUserId : Failure()
    }
}
