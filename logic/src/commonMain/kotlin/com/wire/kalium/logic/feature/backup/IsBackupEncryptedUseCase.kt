package com.wire.kalium.logic.feature.backup

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.util.checkIfCompressedFileContainsFileType
import okio.Path

interface IsBackupEncryptedUseCase {
    /**
     * Checks whether the given backup file is encrypted and requires a password.
     * @param compressedBackupFilePath The absolute file system path to the compressed file.
     * @return A [IsBackupEncryptedResult] indicating whether the given backup file contains encrypted file or not or failure.
     */
    suspend operator fun invoke(compressedBackupFilePath: Path): IsBackupEncryptedResult
}

internal class IsBackupEncryptedUseCaseImpl() : IsBackupEncryptedUseCase {

    override suspend operator fun invoke(compressedBackupFilePath: Path): IsBackupEncryptedResult =
        checkIfCompressedFileContainsFileType(compressedBackupFilePath, ".cc20")
            .fold({ onCheckError(it) }, { IsBackupEncryptedResult.Success(it) })

    private fun onCheckError(coreFailure: CoreFailure): IsBackupEncryptedResult =
        IsBackupEncryptedResult.Failure(coreFailure)
}

sealed class IsBackupEncryptedResult {
    data class Success(val isEncrypted: Boolean) : IsBackupEncryptedResult()
    data class Failure(val error: CoreFailure) : IsBackupEncryptedResult()
}
