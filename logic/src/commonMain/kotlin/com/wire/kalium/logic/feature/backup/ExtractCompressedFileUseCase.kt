package com.wire.kalium.logic.feature.backup

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.asset.KaliumFileSystem
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.util.extractCompressedFile
import okio.Path

interface ExtractCompressedFileUseCase {
    /**
     * Extracts all the files from the provided compressed file path.
     * @param compressedFilePath The absolute file system path to the compressed file.
     * @return A [ExtractCompressedFileResult] indicating the Failure or Success with the root [Path] of the extracted files and a boolean
     * indicating whether the returned paths belong to an encrypted or non-encrypted file.
     */
    suspend operator fun invoke(compressedFilePath: Path): ExtractCompressedFileResult
}

internal class ExtractCompressedFileUseCaseImpl(
    private val kaliumFileSystem: KaliumFileSystem
) : ExtractCompressedFileUseCase {

    override suspend operator fun invoke(compressedFilePath: Path): ExtractCompressedFileResult {
        val tempCompressedFileSource = kaliumFileSystem.source(compressedFilePath)
        val extractedFilesRootPath = createExtractedFilesRootPath()

        return extractCompressedFile(tempCompressedFileSource, extractedFilesRootPath, kaliumFileSystem).fold({
            onRestoreError(it)
        }, {
            val encryptedRootPath = getEncryptedFilePath(extractedFilesRootPath)
            ExtractCompressedFileResult.Success(encryptedRootPath ?: extractedFilesRootPath, encryptedRootPath != null)
        })
    }

    private fun createExtractedFilesRootPath(): Path {
        val extractedFilesRootPath = kaliumFileSystem.tempFilePath(EXTRACTED_FILES_PATH)

        // Delete any previously existing files in the extractedFilesRootPath
        if (kaliumFileSystem.exists(extractedFilesRootPath)) {
            kaliumFileSystem.deleteContents(extractedFilesRootPath)
        }
        kaliumFileSystem.createDirectory(extractedFilesRootPath)

        return extractedFilesRootPath
    }

    private fun onRestoreError(coreFailure: CoreFailure): ExtractCompressedFileResult = ExtractCompressedFileResult.Failure(coreFailure)

    private suspend fun getEncryptedFilePath(extractedFilesRootPath: Path): Path? =
        kaliumFileSystem.listDirectories(extractedFilesRootPath).firstOrNull { it.name.contains(".cc20") }
}

sealed class ExtractCompressedFileResult {
    data class Success(val extractedFilesRootPath: Path, val isEncrypted: Boolean) : ExtractCompressedFileResult()
    data class Failure(val error: CoreFailure) : ExtractCompressedFileResult()
}

private const val EXTRACTED_FILES_PATH = "extractedFiles"
