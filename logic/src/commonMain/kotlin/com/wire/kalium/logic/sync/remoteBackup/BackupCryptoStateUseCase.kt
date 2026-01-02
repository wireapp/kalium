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

package com.wire.kalium.logic.sync.remoteBackup

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.wrapApiRequest
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.getOrElse
import com.wire.kalium.common.functional.map
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.cryptography.utils.calcFileSHA256
import com.wire.kalium.logic.data.asset.KaliumFileSystem
import com.wire.kalium.logic.data.client.MLSClientProvider
import com.wire.kalium.logic.data.client.ProteusClientProvider
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.event.EventRepository
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.RootPathsProvider
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.util.createCompressedFile
import com.wire.kalium.network.api.base.authenticated.backup.MessageSyncApi
import io.ktor.util.encodeBase64
import io.mockative.Mockable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okio.Buffer
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.SYSTEM
import okio.Source
import okio.use

/**
 * Metadata included in the crypto state backup zip file.
 */
@Serializable
data class CryptoStateBackupMetadata(
    @SerialName("version")
    val version: Int,
    @SerialName("client_id")
    val clientId: String,
    @SerialName("mls_db_passphrase")
    val mlsDbPassphrase: String,
    @SerialName("proteus_db_passphrase")
    val proteusDbPassphrase: String,
    @SerialName("last_event_id")
    val lastEventId: String? = null
) {
    companion object {
        const val CURRENT_VERSION = 1
        const val METADATA_FILE_NAME = "metadata.json"
    }
}

/**
 * Use case for backing up the client's cryptographic state to the remote backup service.
 * Zips the Proteus and MLS cryptographic directories and uploads them to the server.
 */
@Mockable
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
    private val kaliumConfigs: KaliumConfigs,
    private val securityHelper: com.wire.kalium.logic.util.SecurityHelper,
    private val eventRepository: EventRepository,
    private val mlsClientProvider: MLSClientProvider
) : BackupCryptoStateUseCase {

    private val logger = kaliumLogger.withTextTag("BackupCryptoState")

    override suspend fun invoke(lastUploadedHash: String?): Either<CoreFailure, String> {
        // Validate preconditions and get client ID
        val clientIdValidation = validatePreconditionsAndGetClientId()
        if (clientIdValidation is Either.Left) {
            return clientIdValidation
        }

        val clientId = (clientIdValidation as Either.Right).value
        logger.i("Starting cryptographic state backup for user: ${selfUserId.value}, client: ${clientId.value}")

        return try {
            // Create temporary zip file
            val tempZipPath = createTempZipFile()

            // Zip crypto directories with metadata
            zipCryptoDirectories(tempZipPath, clientId.value).flatMap { zipFile ->
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
     * Returns Either.Right(ClientId) if all checks pass, or Either.Left with appropriate failure if any check fails.
     */
    private suspend fun validatePreconditionsAndGetClientId(): Either<CoreFailure, ClientId> {
        // 1. Check if feature flag is enabled and remote backup URL is configured
        if (!kaliumConfigs.cryptoStateBackupEnabled) {
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

        // 3. Check if crypto folders exist
        val proteusPath = rootPathsProvider.rootProteusPath(selfUserId).toPath()
        val mlsPath = rootPathsProvider.rootMLSPath(selfUserId).toPath()

        val proteusExists = kaliumFileSystem.exists(proteusPath)
        val mlsExists = kaliumFileSystem.exists(mlsPath)

        if (!proteusExists && !mlsExists) {
            logger.d("No crypto directories exist, skipping crypto state backup")
            return Either.Left(CoreFailure.Unknown(IllegalStateException("No crypto directories")))
        }

        logger.d("Preconditions validated: Proteus exists=$proteusExists, MLS exists=$mlsExists")
        return Either.Right(clientId) // All checks passed, return client ID
    }

    private suspend fun zipCryptoDirectories(outputPath: Path, clientId: String): Either<CoreFailure, Path> {
        val proteusPath = rootPathsProvider.rootProteusPath(selfUserId).toPath()
        val mlsPath = rootPathsProvider.rootMLSPath(selfUserId).toPath()

        // Collect exported database files
        val filesToZip = mutableListOf<Pair<Source, String>>()

        // Export MLS database copy
        if (kaliumFileSystem.exists(mlsPath)) {
            val mlsExportPath = kaliumFileSystem.rootCachePath / "mls_export.db"
            // Get CoreCrypto instance for MLS
            val mlsCoreCrypto = mlsClientProvider.getCoreCrypto().getOrElse {
                logger.e("Failed to get MLS CoreCrypto instance: $it")
                return Either.Left(it)
            }
            try {
                mlsCoreCrypto.exportDatabaseCopy(mlsExportPath.toString())
                val mlsSource = kaliumFileSystem.source(mlsExportPath)
                filesToZip.add(mlsSource to "mls/mls.db")
                logger.d("Exported MLS database")
            } catch (e: Exception) {
                logger.e("Failed to export MLS database: ${e.message}", e)
                return Either.Left(CoreFailure.Unknown(e))
            }
        }

        // Export Proteus database copy
        if (kaliumFileSystem.exists(proteusPath)) {
            val proteusExportPath = kaliumFileSystem.rootCachePath / "proteus_export.db"
            try {
                // Create a temporary CoreCryptoCentral instance for Proteus export
                val proteusDbSecret = securityHelper.proteusDBSecret(selfUserId, proteusPath.toString())
                val proteusCoreCrypto = com.wire.kalium.cryptography.coreCryptoCentral(
                    rootDir = proteusPath.toString(),
                    passphrase = proteusDbSecret.passphrase
                )
                proteusCoreCrypto.exportDatabaseCopy(proteusExportPath.toString())
                val proteusSource = kaliumFileSystem.source(proteusExportPath)
                filesToZip.add(proteusSource to "proteus/proteus.db")
                logger.d("Exported Proteus database")
            } catch (e: Exception) {
                logger.e("Failed to export Proteus database: ${e.message}", e)
                return Either.Left(CoreFailure.Unknown(e))
            }
        }

        if (filesToZip.isEmpty()) {
            logger.w("No crypto databases found to backup")
            return Either.Left(CoreFailure.Unknown(IllegalStateException("No crypto data to backup")))
        }

        // Retrieve database passphrases
        val mlsPassphrase = securityHelper.mlsDBSecret(selfUserId, mlsPath.toString()).passphrase.encodeBase64()
        val proteusPassphrase = securityHelper.proteusDBSecret(selfUserId, proteusPath.toString()).passphrase.encodeBase64()

        logger.i("Backing up database passphrases: MLS='$mlsPassphrase', Proteus='$proteusPassphrase'")

        // Retrieve last saved event ID (non-critical - continue on failure)
        val lastEventId = when (val result = eventRepository.lastSavedEventId()) {
            is Either.Left -> {
                logger.w("Failed to retrieve last event ID for backup: ${result.value}")
                null
            }
            is Either.Right -> {
                val eventId = result.value
                if (eventId.isNotBlank()) {
                    logger.i("Including last event ID in backup: ${eventId.take(8)}...")
                    eventId
                } else {
                    logger.d("No last event ID found")
                    null
                }
            }
        }

        // Add metadata JSON file to the zip
        val metadata = CryptoStateBackupMetadata(
            version = CryptoStateBackupMetadata.CURRENT_VERSION,
            clientId = clientId,
            mlsDbPassphrase = mlsPassphrase,
            proteusDbPassphrase = proteusPassphrase,
            lastEventId = lastEventId
        )
        val metadataJson = Json.encodeToString(metadata)
        val metadataSource = Buffer().writeUtf8(metadataJson)
        filesToZip.add(metadataSource to CryptoStateBackupMetadata.METADATA_FILE_NAME)

        logger.d("Zipping ${filesToZip.size} crypto files (including metadata)")

        // Create zip using existing utility
        return kaliumFileSystem.sink(outputPath).use { sink ->
            createCompressedFile(filesToZip, sink).map { size ->
                logger.i("Created crypto state zip: $size bytes")
                // Clean up the temporary exported databases
                try {
                    val mlsExportPath = kaliumFileSystem.rootCachePath / "mls_export.db"
                    if (kaliumFileSystem.exists(mlsExportPath)) {
                        kaliumFileSystem.delete(mlsExportPath)
                    }
                    val proteusExportPath = kaliumFileSystem.rootCachePath / "proteus_export.db"
                    if (kaliumFileSystem.exists(proteusExportPath)) {
                        kaliumFileSystem.delete(proteusExportPath)
                    }
                } catch (e: Exception) {
                    logger.w("Failed to cleanup exported databases: ${e.message}")
                }
                outputPath
            }
        }
    }

    private fun calculateHash(zipPath: Path): Either<CoreFailure, String> {
        return try {
            val hashBytes = calcFileSHA256(
                kaliumFileSystem.source(zipPath)
            )
            if (hashBytes != null) {
                val hash = hashBytes.toHexString()
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
        val fileSize = FileSystem.SYSTEM.metadata(zipPath).size ?: 0L
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
        return cacheDir / "crypto_state_backup.zip"
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
