/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
@file:Suppress("konsist.useCasesShouldNotAccessNetworkLayerDirectly")

package com.wire.kalium.logic.feature.backup

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.wrapApiRequest
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.map
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.data.asset.KaliumFileSystem
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.RootPathsProvider
import com.wire.kalium.logic.util.ExtractFilesParam
import com.wire.kalium.logic.util.extractCompressedFile
import com.wire.kalium.network.api.base.authenticated.backup.MessageSyncApi
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.utils.NetworkResponse
import io.mockative.Mockable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okio.Path
import okio.Path.Companion.toPath
import okio.buffer
import okio.use

/**
 * Result of downloading and restoring crypto state backup
 */
sealed class DownloadAndRestoreCryptoStateResult {
    /**
     * No backup exists on the server
     */
    data object NoBackupFound : DownloadAndRestoreCryptoStateResult()

    /**
     * Backup was successfully downloaded and restored
     * @param clientId The client ID from the backup metadata
     */
    data class Success(val clientId: ClientId) : DownloadAndRestoreCryptoStateResult()

    /**
     * Failed to download or restore the backup
     */
    data class Failure(val error: CoreFailure) : DownloadAndRestoreCryptoStateResult()
}

/**
 * Use case for downloading and restoring the cryptographic state backup from the remote service.
 * This is used during login when a backup exists on the server.
 */
@Mockable
interface DownloadAndRestoreCryptoStateUseCase {
    /**
     * Downloads and restores the crypto state backup for the user
     * @return Result indicating whether a backup was found and restored, or if there was no backup
     */
    suspend operator fun invoke(): DownloadAndRestoreCryptoStateResult
}

internal class DownloadAndRestoreCryptoStateUseCaseImpl(
    private val selfUserId: UserId,
    private val messageSyncApi: MessageSyncApi,
    private val rootPathsProvider: RootPathsProvider,
    private val kaliumFileSystem: KaliumFileSystem,
    kaliumLogger: KaliumLogger = com.wire.kalium.common.logger.kaliumLogger
) : DownloadAndRestoreCryptoStateUseCase {

    private val logger = kaliumLogger.withTextTag("DownloadAndRestoreCryptoState")

    override suspend fun invoke(): DownloadAndRestoreCryptoStateResult {
        logger.i("Checking for crypto state backup for user: ${selfUserId.value}")

        // Create temp file for download
        val tempZipPath = createTempZipFile()

        return try {
            // Download the backup
            val downloadResult = wrapApiRequest {
                val sink = kaliumFileSystem.sink(tempZipPath)
                messageSyncApi.downloadStateBackup(selfUserId.value, sink)
            }

            val result: Either<CoreFailure, ClientId> = downloadResult.flatMap {
                // Extract and restore
                extractAndRestoreCryptoState(tempZipPath)
            }

            cleanupTempFile(tempZipPath)

            when (result) {
                is Either.Left -> {
                    val failure = result.value
                    // Check if it's a 404 (no backup found)
                    if (failure is com.wire.kalium.common.error.NetworkFailure.ServerMiscommunication &&
                        failure.kaliumException is KaliumException.InvalidRequestError
                    ) {
                        val statusCode =
                            (failure.kaliumException as KaliumException.InvalidRequestError).errorResponse.code
                        if (statusCode == 404) {
                            logger.i("No backup found on server")
                            return DownloadAndRestoreCryptoStateResult.NoBackupFound
                        }
                    }
                    logger.e("Failed to download or restore backup: $failure")
                    DownloadAndRestoreCryptoStateResult.Failure(failure)
                }
                is Either.Right -> {
                    val clientId = result.value
                    logger.i("Successfully restored crypto state backup with client ID: ${clientId.value}")
                    DownloadAndRestoreCryptoStateResult.Success(clientId)
                }
            }
        } catch (e: Exception) {
            cleanupTempFile(tempZipPath)
            logger.e("Exception during backup restore: ${e.message}", e)
            DownloadAndRestoreCryptoStateResult.Failure(CoreFailure.Unknown(e))
        }
    }

    private suspend fun extractAndRestoreCryptoState(zipPath: Path): Either<CoreFailure, ClientId> {
        return try {
            // Create temp directory for extraction
            val tempExtractPath = createTempExtractDirectory()

            // Extract the entire ZIP to temp directory
            val extractResult = kaliumFileSystem.source(zipPath).use { source ->
                extractCompressedFile(
                    inputSource = source,
                    outputRootPath = tempExtractPath,
                    param = ExtractFilesParam.All,
                    fileSystem = kaliumFileSystem
                )
            }

            extractResult.flatMap {
                // Read metadata to get client ID
                val metadataPath = tempExtractPath / CryptoStateBackupMetadata.METADATA_FILE_NAME
                val metadata = if (kaliumFileSystem.exists(metadataPath)) {
                    kaliumFileSystem.source(metadataPath).buffer().use { source ->
                        val json = source.readUtf8()
                        Json.decodeFromString<CryptoStateBackupMetadata>(json)
                    }
                } else {
                    throw IllegalStateException("Metadata not found in backup")
                }

                val clientId = ClientId(metadata.clientId)

                // Copy extracted files to final locations
                val tempProteusPath = tempExtractPath / "proteus"
                val tempMlsPath = tempExtractPath / "mls"
                val finalProteusPath = rootPathsProvider.rootProteusPath(selfUserId).toPath()
                val finalMlsPath = rootPathsProvider.rootMLSPath(selfUserId).toPath()

                // Ensure parent directories exist
                kaliumFileSystem.createDirectories(finalProteusPath.parent!!)
                kaliumFileSystem.createDirectories(finalMlsPath.parent!!)

                // Copy Proteus directory
                if (kaliumFileSystem.exists(tempProteusPath)) {
                    copyDirectoryRecursively(tempProteusPath, finalProteusPath)
                    logger.i("Restored Proteus directory")
                }

                // Copy MLS directory
                if (kaliumFileSystem.exists(tempMlsPath)) {
                    copyDirectoryRecursively(tempMlsPath, finalMlsPath)
                    logger.i("Restored MLS directory")
                }

                // Clean up temp extraction directory
                cleanupDirectory(tempExtractPath)

                logger.i("Successfully extracted and restored crypto state backup")
                Either.Right(clientId)
            }
        } catch (e: Exception) {
            logger.e("Failed to extract and restore backup: ${e.message}", e)
            Either.Left(CoreFailure.Unknown(e))
        }
    }

    private fun createTempZipFile(): Path {
        val cacheDir = kaliumFileSystem.rootCachePath
        return cacheDir / "crypto_state_restore_${System.currentTimeMillis()}.zip"
    }

    private fun createTempExtractDirectory(): Path {
        val cacheDir = kaliumFileSystem.rootCachePath
        val tempDir = cacheDir / "crypto_state_extract_${System.currentTimeMillis()}"
        kaliumFileSystem.createDirectories(tempDir)
        return tempDir
    }

    private suspend fun copyDirectoryRecursively(source: Path, destination: Path) {
        if (!kaliumFileSystem.exists(source)) {
            logger.w("Source directory does not exist: $source")
            return
        }

        // Create destination directory
        kaliumFileSystem.createDirectories(destination)

        // List all items in source directory
        val items = try {
            kaliumFileSystem.listDirectories(source)
        } catch (e: Exception) {
            logger.e("Failed to list directory $source: ${e.message}", e)
            emptyList()
        }

        items.forEach { item ->
            val itemName = item.name
            val destItem = destination / itemName

            // Check if it's a directory by trying to list it
            val isDirectory = try {
                kaliumFileSystem.listDirectories(item)
                true
            } catch (e: Exception) {
                false
            }

            if (isDirectory) {
                // Recursively copy subdirectory
                copyDirectoryRecursively(item, destItem)
            } else {
                // Copy file
                try {
                    kaliumFileSystem.copy(item, destItem)
                    logger.d("Copied file: $itemName")
                } catch (e: Exception) {
                    logger.e("Failed to copy file $item: ${e.message}", e)
                }
            }
        }
    }

    private fun cleanupDirectory(path: Path) {
        try {
            if (kaliumFileSystem.exists(path)) {
                kaliumFileSystem.deleteContents(path)
                logger.d("Cleaned up temp directory: $path")
            }
        } catch (e: Exception) {
            logger.w("Failed to cleanup temp directory: ${e.message}")
        }
    }

    private fun cleanupTempFile(path: Path) {
        try {
            if (kaliumFileSystem.exists(path)) {
                kaliumFileSystem.delete(path)
                logger.d("Cleaned up temp restore file")
            }
        } catch (e: Exception) {
            logger.w("Failed to cleanup temp restore file: ${e.message}")
        }
    }
}
