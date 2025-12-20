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
@file:Suppress("konsist.useCasesShouldNotAccessNetworkLayerDirectly", "konsist.useCasesShouldNotAccessDaoLayerDirectly")

package com.wire.kalium.logic.feature.backup

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.wrapApiRequest
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.fold
import com.wire.kalium.common.functional.map
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.data.asset.KaliumFileSystem
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.RootPathsProvider
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.util.createCompressedFile
import com.wire.kalium.network.api.base.authenticated.backup.MessageSyncApi
import okio.Path
import okio.Path.Companion.toPath
import okio.Source

/**
 * Use case for backing up the client's cryptographic state to the remote backup service.
 * Zips the Proteus and MLS cryptographic directories and uploads them to the server.
 */
interface BackupCryptoStateUseCase {
    /**
     * Creates a backup of the client's cryptographic state and uploads it to the remote service
     * @param lastUploadedHash Optional hash of the last uploaded backup to avoid duplicate uploads
     * @return Either a CoreFailure or the SHA-256 hash (hex encoded) of the uploaded backup
     */
    suspend operator fun invoke(lastUploadedHash: String? = null): Either<CoreFailure, String>
}

internal class BackupCryptoStateUseCaseImpl(
    private val selfUserId: UserId,
    private val currentClientIdProvider: CurrentClientIdProvider,
    private val messageSyncApi: MessageSyncApi,
    private val rootPathsProvider: RootPathsProvider,
    private val kaliumFileSystem: KaliumFileSystem,
    private val kaliumConfigs: KaliumConfigs
) : BackupCryptoStateUseCase {

    private val logger = kaliumLogger.withTextTag("BackupCryptoState")

    override suspend fun invoke(lastUploadedHash: String?): Either<CoreFailure, String> {
        // Validate preconditions before attempting backup
        val validationResult = validatePreconditions()
        if (validationResult != null) {
            return validationResult
        }

        logger.i("Starting cryptographic state backup for user: ${selfUserId.value}")

        return try {
            // Create temporary zip file
            val tempZipPath = createTempZipFile()

            // Zip crypto directories
            zipCryptoDirectories(tempZipPath).flatMap { zipFile ->
                // Calculate hash of the zip file
                calculateHash(zipFile).flatMap { hash ->
                    // Check if hash matches last uploaded hash
                    if (hash == lastUploadedHash) {
                        logger.i("Crypto state unchanged (hash: ${hash.take(8)}...), skipping upload")
                        cleanupTempFile(zipFile)
                        Either.Right(hash)
                    } else {
                        logger.i("Crypto state changed (new hash: ${hash.take(8)}...), uploading")
                        // Upload to server
                        uploadZipFile(zipFile).map { hash }.also {
                            // Clean up temp file
                            cleanupTempFile(zipFile)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logger.e("Failed to backup crypto state: ${e.message}", e)
            Either.Left(CoreFailure.Unknown(e))
        }
    }

    /**
     * Validates all preconditions required for crypto state backup.
     * Returns null if all checks pass, or Either.Left with appropriate failure if any check fails.
     */
    private suspend fun validatePreconditions(): Either<CoreFailure, String>? {
        // 1. Check if feature flag is enabled
        if (!kaliumConfigs.messageSynchronizationEnabled) {
            logger.d("Message synchronization disabled, skipping crypto state backup")
            return Either.Left(CoreFailure.Unknown(IllegalStateException("Feature disabled")))
        }

        // 2. Check if client is registered
        val clientIdResult = currentClientIdProvider()
        if (clientIdResult is Either.Left) {
            logger.d("No client registered, skipping crypto state backup: ${clientIdResult.value}")
            return clientIdResult
        }

        val clientId = (clientIdResult as Either.Right).value
        logger.d("Client registered: ${clientId.value}")

        // 3. Check if crypto folders exist and are not empty
        val proteusPath = rootPathsProvider.rootProteusPath(selfUserId).toPath()
        val mlsPath = rootPathsProvider.rootMLSPath(selfUserId).toPath()

        val proteusExists = kaliumFileSystem.exists(proteusPath)
        val mlsExists = kaliumFileSystem.exists(mlsPath)

        if (!proteusExists && !mlsExists) {
            logger.d("No crypto directories exist, skipping crypto state backup")
            return Either.Left(CoreFailure.Unknown(IllegalStateException("No crypto directories")))
        }

        // Check if at least one directory has content
        val hasProteusContent = proteusExists && isFolderNotEmpty(proteusPath)
        val hasMlsContent = mlsExists && isFolderNotEmpty(mlsPath)

        if (!hasProteusContent && !hasMlsContent) {
            logger.d("Crypto directories are empty, skipping crypto state backup")
            return Either.Left(CoreFailure.Unknown(IllegalStateException("Crypto directories empty")))
        }

        logger.d("Preconditions validated: Proteus=${hasProteusContent}, MLS=${hasMlsContent}")
        return null // All checks passed
    }

    /**
     * Checks if a folder contains any files or subdirectories.
     */
    private suspend fun isFolderNotEmpty(path: Path): Boolean {
        return try {
            val entries = kaliumFileSystem.listDirectories(path)
            entries.isNotEmpty()
        } catch (e: Exception) {
            logger.w("Failed to check folder content: ${e.message}")
            false
        }
    }

    private suspend fun zipCryptoDirectories(outputPath: Path): Either<CoreFailure, Path> {
        val proteusPath = rootPathsProvider.rootProteusPath(selfUserId).toPath()
        val mlsPath = rootPathsProvider.rootMLSPath(selfUserId).toPath()

        // Collect all files from both directories
        val filesToZip = mutableListOf<Pair<Source, String>>()

        // Add Proteus files with "proteus/" prefix
        if (kaliumFileSystem.exists(proteusPath)) {
            collectFilesRecursively(proteusPath, "proteus", filesToZip)
        }

        // Add MLS files with "mls/" prefix
        if (kaliumFileSystem.exists(mlsPath)) {
            collectFilesRecursively(mlsPath, "mls", filesToZip)
        }

        if (filesToZip.isEmpty()) {
            logger.w("No crypto files found to backup")
            return Either.Left(CoreFailure.Unknown(IllegalStateException("No crypto data to backup")))
        }

        logger.d("Zipping ${filesToZip.size} crypto files")

        // Create zip using existing utility
        return kaliumFileSystem.sink(outputPath).use { sink ->
            createCompressedFile(filesToZip, sink).map { size ->
                logger.i("Created crypto state zip: $size bytes")
                outputPath
            }
        }
    }

    private suspend fun collectFilesRecursively(
        directory: Path,
        prefix: String,
        output: MutableList<Pair<Source, String>>
    ) {
        val entries = kaliumFileSystem.listDirectories(directory)

        entries.forEach { entry ->
            val metadata = okio.FileSystem.SYSTEM.metadata(entry)
            val fileName = entry.name

            if (metadata.isDirectory) {
                // Recursively collect from subdirectory
                collectFilesRecursively(entry, "$prefix/$fileName", output)
            } else {
                // Add file to output
                val source = kaliumFileSystem.source(entry)
                val relativeName = "$prefix/$fileName"
                output.add(source to relativeName)
            }
        }
    }

    private fun calculateHash(zipPath: Path): Either<CoreFailure, String> {
        return try {
            val hashBytes = com.wire.kalium.cryptography.utils.calcFileSHA256(
                kaliumFileSystem.source(zipPath)
            )
            if (hashBytes != null) {
                val hash = hashBytes.joinToString("") { "%02x".format(it) }
                Either.Right(hash)
            } else {
                Either.Left(CoreFailure.Unknown(IllegalStateException("Failed to calculate hash")))
            }
        } catch (e: Exception) {
            logger.e("Failed to calculate hash: ${e.message}", e)
            Either.Left(CoreFailure.Unknown(e))
        }
    }

    private suspend fun uploadZipFile(zipPath: Path): Either<CoreFailure, Unit> {
        val fileSize = okio.FileSystem.SYSTEM.metadata(zipPath).size ?: 0L
        logger.i("Uploading crypto state backup: $fileSize bytes")

        return wrapApiRequest {
            messageSyncApi.uploadStateBackup(
                userId = selfUserId.value,
                backupDataSource = { kaliumFileSystem.source(zipPath) },
                backupSize = fileSize
            )
        }.map {
            logger.i("Crypto state backup uploaded successfully")
        }
    }

    private fun createTempZipFile(): Path {
        // Use KaliumFileSystem's cache path
        val cacheDir = kaliumFileSystem.rootCachePath
        return cacheDir / "crypto_state_backup_${System.currentTimeMillis()}.zip"
    }

    private fun cleanupTempFile(path: Path) {
        try {
            if (kaliumFileSystem.exists(path)) {
                kaliumFileSystem.delete(path)
                logger.d("Cleaned up temp backup file")
            }
        } catch (e: Exception) {
            logger.w("Failed to cleanup temp backup file: ${e.message}")
        }
    }
}
