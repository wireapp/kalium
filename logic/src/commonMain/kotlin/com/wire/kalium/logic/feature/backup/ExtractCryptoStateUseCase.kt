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
package com.wire.kalium.logic.feature.backup

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.fold
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.data.asset.KaliumFileSystem
import com.wire.kalium.logic.util.ExtractFilesParam
import com.wire.kalium.logic.util.extractCompressedFile
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import io.mockative.Mockable
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okio.Path
import okio.buffer
import okio.use

/**
 * Extracts the crypto state backup from a downloaded zip file.
 */
@Mockable
public interface ExtractCryptoStateUseCase {
    /**
     * Extracts the crypto state backup from the given zip file.
     * @param backupFilePath The path to the downloaded crypto state backup zip file.
     * @return [ExtractCryptoStateResult.Success] with the extracted data,
     * or [ExtractCryptoStateResult.Failure] if the extraction failed.
     */
    public suspend operator fun invoke(backupFilePath: Path): ExtractCryptoStateResult
}

internal class ExtractCryptoStateUseCaseImpl(
    private val kaliumFileSystem: KaliumFileSystem,
    private val kaliumDispatcher: KaliumDispatcher = KaliumDispatcherImpl
) : ExtractCryptoStateUseCase {

    @Suppress("TooGenericExceptionCaught", "LongMethod")
    override suspend fun invoke(backupFilePath: Path): ExtractCryptoStateResult = withContext(kaliumDispatcher.io) {
        val extractedDir = kaliumFileSystem.tempFilePath(EXTRACTED_CRYPTO_BACKUP_DIR)

        try {
            val backupFileExists = kaliumFileSystem.exists(backupFilePath)
            kaliumLogger.i("$TAG Backup file path: $backupFilePath, exists: $backupFileExists")

            if (!backupFileExists) {
                kaliumLogger.e("$TAG Backup file does not exist at: $backupFilePath")
                return@withContext ExtractCryptoStateResult.Failure(
                    CoreFailure.Unknown(IllegalStateException("Backup file does not exist"))
                )
            }

            kaliumFileSystem.createDirectories(extractedDir)

            val extractResult = extractCompressedFile(
                inputSource = kaliumFileSystem.source(backupFilePath),
                outputRootPath = extractedDir,
                param = ExtractFilesParam.All,
                fileSystem = kaliumFileSystem
            )

            extractResult.fold(
                { error ->
                    kaliumLogger.e("$TAG Failed to extract crypto state backup: $error")
                    cleanup(extractedDir)
                    ExtractCryptoStateResult.Failure(error)
                },
                { extractedSize ->
                    kaliumLogger.i("$TAG Successfully extracted crypto state backup ($extractedSize bytes)")

                    // Parse metadata
                    val metadata = parseMetadata(extractedDir)
                    if (metadata == null) {
                        kaliumLogger.e("$TAG Failed to parse crypto state backup metadata.")
                        cleanup(extractedDir)
                        return@withContext ExtractCryptoStateResult.Failure(
                            CoreFailure.Unknown(IllegalStateException("Missing or invalid metadata file"))
                        )
                    }

                    // Find MLS and Proteus keystore files
                    val mlsKeystorePath = extractedDir.resolve(MLS_KEYSTORE_NAME)
                    val proteusKeystorePath = extractedDir.resolve(PROTEUS_KEYSTORE_NAME)

                    if (!kaliumFileSystem.exists(mlsKeystorePath) || !kaliumFileSystem.exists(proteusKeystorePath)) {
                        kaliumLogger.e("$TAG Missing keystore files in crypto state backup")
                        cleanup(extractedDir)
                        return@withContext ExtractCryptoStateResult.Failure(
                            CoreFailure.Unknown(IllegalStateException("Missing keystore files"))
                        )
                    }

                    ExtractCryptoStateResult.Success(
                        extractedDir = extractedDir,
                        metadata = metadata,
                        mlsKeystorePath = mlsKeystorePath,
                        proteusKeystorePath = proteusKeystorePath
                    )
                }
            )
        } catch (e: Exception) {
            kaliumLogger.e("$TAG Exception while extracting crypto state backup", e)
            cleanup(extractedDir)
            ExtractCryptoStateResult.Failure(CoreFailure.Unknown(e))
        }
    }

    private fun parseMetadata(extractedDir: Path): CryptoStateBackupMetadata? {
        val metadataPath = extractedDir.resolve(BackupConstants.BACKUP_METADATA_FILE_NAME)
        return if (kaliumFileSystem.exists(metadataPath)) {
            try {
                kaliumFileSystem.source(metadataPath).buffer().use { source ->
                    val metadataJson = source.readUtf8()
                    Json.decodeFromString<CryptoStateBackupMetadata>(metadataJson)
                }
            } catch (e: Exception) {
                kaliumLogger.e("$TAG Failed to parse metadata file: ${e.message}", e)
                null
            }
        } else {
            kaliumLogger.e("$TAG Metadata file not found at expected path: $metadataPath")
            null
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun cleanup(extractedDir: Path) {
        try {
            kaliumFileSystem.deleteContents(extractedDir)
            kaliumFileSystem.delete(extractedDir)
        } catch (e: Exception) {
            kaliumLogger.w("$TAG Failed to cleanup extracted directory", e)
        }
    }

    companion object {
        const val EXTRACTED_CRYPTO_BACKUP_DIR = "extracted_crypto_backup"
        const val MLS_KEYSTORE_NAME = "keystore-mls"
        const val PROTEUS_KEYSTORE_NAME = "keystore-proteus"
        const val TAG = "[ExtractCryptoStateUseCase]"
    }
}

public sealed class ExtractCryptoStateResult {
    public data class Success(
        val extractedDir: Path,
        val metadata: CryptoStateBackupMetadata,
        val mlsKeystorePath: Path,
        val proteusKeystorePath: Path
    ) : ExtractCryptoStateResult()

    public data class Failure(val error: CoreFailure) : ExtractCryptoStateResult()
}
