package com.wire.kalium.logic.feature.backup

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.asset.KaliumFileSystem
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.util.checkIfCompressedFileContainsFileTypes
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
    private val kaliumFileSystem: KaliumFileSystem
) : VerifyBackupUseCase {

    override suspend operator fun invoke(compressedBackupFilePath: Path): VerifyBackupResult =
        checkIfCompressedFileContainsFileTypes(
            compressedBackupFilePath,
            kaliumFileSystem,
            listOf(
                BackupConstants.BACKUP_ENCRYPTED_EXTENSION,
                BackupConstants.BACKUP_DB_EXTENSION,
                BackupConstants.BACKUP_METADATA_EXTENSION
            )
        ).fold({
            VerifyBackupResult.Failure.Generic(it)
        }, {
            when {
                it[BackupConstants.BACKUP_ENCRYPTED_EXTENSION] == true ->
                    VerifyBackupResult.Success.Encrypted

                it[BackupConstants.BACKUP_DB_EXTENSION] == true && it[BackupConstants.BACKUP_METADATA_EXTENSION] == true ->
                    VerifyBackupResult.Success.NotEncrypted

                else ->
                    VerifyBackupResult.Failure.InvalidBackupFile
            }
        })
}

sealed class VerifyBackupResult {
    sealed class Success : VerifyBackupResult() {
        object Encrypted : Success()
        object NotEncrypted : Success()
    }

    sealed class Failure : VerifyBackupResult() {
        object InvalidBackupFile : Failure()
        data class Generic(val error: CoreFailure) : Failure()
    }
}
