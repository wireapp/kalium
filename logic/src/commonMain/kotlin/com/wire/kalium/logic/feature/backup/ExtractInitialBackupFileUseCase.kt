package com.wire.kalium.logic.feature.backup

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.asset.KaliumFileSystem
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.util.extractCompressedFile
import okio.Path

interface ExtractInitialBackupFileUseCase {
    /**
     * Extracts the initial backup file from the provided compressed backup file path.
     * @param importedBackupPath The absolute file system path to the compressed backup file.
     * @return A [ExtractInitialBackupResult] indicating the Failure or Success with the root [Path] of the extracted files and a boolean
     * indicating whether the returned paths belong to an encrypted or non-encrypted backup.
     */
    suspend operator fun invoke(importedBackupPath: Path): ExtractInitialBackupResult
}

internal class ExtractInitialBackupFileUseCaseImpl(
    private val kaliumFileSystem: KaliumFileSystem
) : ExtractInitialBackupFileUseCase {

    override suspend operator fun invoke(importedBackupPath: Path): ExtractInitialBackupResult {
        val tempCompressedBackupFileSource = kaliumFileSystem.source(importedBackupPath)
        val extractedBackupFilesRootPath = createExtractedBackupFilesRootPath()

        return extractCompressedFile(tempCompressedBackupFileSource, extractedBackupFilesRootPath, kaliumFileSystem).fold({
            onRestoreError(it)
        }, {
            val encryptedBackupRootPath = getEncryptedBackupFilePath(extractedBackupFilesRootPath)
            ExtractInitialBackupResult.Success(encryptedBackupRootPath ?: extractedBackupFilesRootPath, encryptedBackupRootPath != null)
        })
    }

    private fun createExtractedBackupFilesRootPath(): Path {
        val extractedBackupFilesRootPath = kaliumFileSystem.tempFilePath(EXTRACTED_BACKUP_FILES_PATH)

        // Delete any previously existing files in the extractedBackupRootFilesPath
        if (kaliumFileSystem.exists(extractedBackupFilesRootPath)) {
            kaliumFileSystem.deleteContents(extractedBackupFilesRootPath)
        }
        kaliumFileSystem.createDirectory(extractedBackupFilesRootPath)

        return extractedBackupFilesRootPath
    }

    private fun onRestoreError(coreFailure: CoreFailure): ExtractInitialBackupResult = ExtractInitialBackupResult.Failure(coreFailure)

    private suspend fun getEncryptedBackupFilePath(extractedBackupFilesRootPath: Path): Path? =
        kaliumFileSystem.listDirectories(extractedBackupFilesRootPath).firstOrNull { it.name.contains(".cc20") }
}

sealed class ExtractInitialBackupResult {
    data class Success(val extractedBackupRootPath: Path, val isEncrypted: Boolean) : ExtractInitialBackupResult()
    data class Failure(val error: CoreFailure) : ExtractInitialBackupResult()
}

private const val EXTRACTED_BACKUP_FILES_PATH = "extractedBackupFiles"
