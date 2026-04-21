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
@file:Suppress("TooGenericExceptionCaught")

package com.wire.kalium.logic.feature.backup

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.fold
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.common.logger.nomadTrace
import com.wire.kalium.logic.data.asset.KaliumFileSystem
import com.wire.kalium.logic.data.client.CryptoBackupMetadata
import com.wire.kalium.logic.data.client.CryptoTransactionProvider
import com.wire.kalium.logic.data.event.EventRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.util.createCompressedFile
import com.wire.kalium.util.DateTimeUtil
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okio.Buffer
import okio.Path
import okio.Path.Companion.toPath
import okio.Source
import okio.buffer
import okio.use
import kotlin.io.encoding.Base64
import kotlin.random.Random

/**
 * Performs the backup of the cryptographic database by exporting it, creating a metadata file, and packaging them into a ZIP file.
 */
public interface BackupCryptoDBUseCase {
    public suspend operator fun invoke(): BackupCryptoDBResult
}

internal class BackupCryptoDBUseCaseImpl(
    private val userId: UserId,
    private val cryptoTransactionProvider: CryptoTransactionProvider,
    private val eventRepository: EventRepository,
    private val kaliumFileSystem: KaliumFileSystem,
    private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl,
    private val tempBackupDirNameProvider: () -> String = {
        val timeStamp = DateTimeUtil.currentSimpleDateTimeString().replace(":", "-")
        "${TEMP_CRYPTO_BACKUP_DIR}_${timeStamp}_${Random.nextInt()}"
    },
) : BackupCryptoDBUseCase {

    @Suppress("LongMethod")
    override suspend fun invoke(): BackupCryptoDBResult = withContext(dispatchers.default) {
        try {
            val backupName = createBackupFileName()
            val tempBackupName = createTempBackupFileName(backupName)
            val (cryptoBackupRootPath, mlsBackupPath, proteusBackupPath) = createBackupDirectories()

            // Read event anchor BEFORE crypto exports to ensure anchor <= MLS state
            val lastProcessedEventId = when (val result = eventRepository.lastSavedEventId()) {
                is Either.Right -> result.value
                is Either.Left -> when (result.value) {
                    is StorageFailure.DataNotFound -> when (val fetchResult = eventRepository.fetchMostRecentEventId()) {
                        is Either.Right -> fetchResult.value
                        is Either.Left -> return@withContext BackupCryptoDBResult.Failure(fetchResult.value)
                    }
                    else -> return@withContext BackupCryptoDBResult.Failure(result.value)
                }
            }

            if (kaliumFileSystem.exists(mlsBackupPath)) {
                kaliumFileSystem.delete(mlsBackupPath)
            }
            val (mlsExportData, mlsDbBytes) = createMLSBackup(mlsBackupPath).fold(
                {
                    kaliumLogger.e("Failed to create MLS backup")
                    return@withContext BackupCryptoDBResult.Failure(it)
                },
                { it }
            )
            if (kaliumFileSystem.exists(proteusBackupPath)) {
                kaliumFileSystem.delete(proteusBackupPath)
            }
            val (proteusExportData, proteusDbBytes) = createProteusBackup(proteusBackupPath).fold(
                {
                    kaliumLogger.e("Failed to create Proteus backup")
                    return@withContext BackupCryptoDBResult.Failure(it)
                },
                { it }
            )

            val tempBackupPath = cryptoBackupRootPath.resolve(tempBackupName)
            val backupFilePath = kaliumFileSystem.tempFilePath(backupName)
            kaliumLogger.nomadTrace(
                stage = "backup.capture_event_id",
                fields = mapOf(
                    "userId" to userId.toLogString(),
                    "lastProcessedEventId" to lastProcessedEventId
                )
            )
            val metadataPath = createMetadataFile(
                cryptoBackupRootPath = cryptoBackupRootPath,
                mlsExportData = mlsExportData,
                proteusExportData = proteusExportData,
                lastProcessedEventId = lastProcessedEventId
            )
            createBackupZip(mlsDbBytes, proteusDbBytes, metadataPath, tempBackupPath).fold(
                { error -> BackupCryptoDBResult.Failure(error) },
                {
                    persistBackup(tempBackupPath, backupFilePath)
                    val backupSize = kaliumFileSystem.size(backupFilePath)
                    kaliumLogger.nomadTrace(
                        stage = "backup.file.ready",
                        fields = mapOf(
                            "userId" to userId.toLogString(),
                            "backupPath" to backupFilePath.toString(),
                            "backupSizeBytes" to backupSize,
                            "lastProcessedEventId" to lastProcessedEventId
                        )
                    )
                    deleteOlderBackups(backupFilePath.parent!!, backupFilePath)
                    BackupCryptoDBResult.Success(
                        backupFilePath = backupFilePath,
                        backupName = backupName,
                        lastProcessedEventId = lastProcessedEventId
                    )
                }
            )
        } catch (e: Exception) {
            kaliumLogger.e("CoreCrypto export failed", e)
            BackupCryptoDBResult.Failure(StorageFailure.Generic(e))
        } finally {
            kaliumFileSystem.deleteContents(cryptoBackupRootPath)
        }
    }

    private suspend fun createMLSBackup(mlsBackupPath: Path): Either<CoreFailure, Pair<CryptoBackupMetadata, ByteArray>> =
        cryptoTransactionProvider.mlsTransaction("backup_read_mls") { _ ->
            cryptoTransactionProvider.mlsClientProvider
                .exportCryptoDB(mlsBackupPath.toString())
                .fold(
                    {
                        kaliumLogger.e("MLS backup export failed with error: $it")
                        Either.Left(it)
                    },
                    { exportData ->
                        try {
                            val bytes = kaliumFileSystem.source(exportData.dbPath.toPath()).use {
                                it.buffer().readByteArray()
                            }
                            Either.Right(exportData to bytes)
                        } catch (e: Exception) {
                            Either.Left(StorageFailure.Generic(e))
                        }
                    }
                )
        }

    private suspend fun createProteusBackup(proteusBackupPath: Path): Either<CoreFailure, Pair<CryptoBackupMetadata, ByteArray>> =
        cryptoTransactionProvider.proteusTransaction("backup_read_proteus") { _ ->
            cryptoTransactionProvider.proteusClientProvider
                .exportCryptoDB(proteusBackupPath.toString())
                .fold(
                    { Either.Left(it) },
                    { exportData ->
                        try {
                            val bytes = kaliumFileSystem.source(exportData.dbPath.toPath()).use {
                                it.buffer().readByteArray()
                            }
                            Either.Right(exportData to bytes)
                        } catch (e: Exception) {
                            Either.Left(StorageFailure.Generic(e))
                        }
                    }
                )
        }

    private fun createBackupDirectories(): Triple<Path, Path, Path> {
        val cryptoBackupRootPath = kaliumFileSystem.tempFilePath(tempBackupDirNameProvider())
        kaliumFileSystem.createDirectories(cryptoBackupRootPath)
        val mlsBackupPath = cryptoBackupRootPath.resolve(MLS_KEYSTORE_NAME)
        val proteusBackupPath = cryptoBackupRootPath.resolve(PROTEUS_KEYSTORE_NAME)
        return Triple(cryptoBackupRootPath, mlsBackupPath, proteusBackupPath)
    }

    private fun createBackupFileName(): String {
        val timeStamp = DateTimeUtil.currentSimpleDateTimeString()
        return "${CRYPTO_BACKUP_PREFIX}_${userId}_${timeStamp.replace(":", "-")}.zip"
    }

    private fun createTempBackupFileName(backupName: String): String = "$backupName.tmp"

    private fun persistBackup(tempBackupPath: Path, backupFilePath: Path) {
        kaliumFileSystem.copy(tempBackupPath, backupFilePath)
        kaliumFileSystem.delete(tempBackupPath)
    }

    private suspend fun deleteOlderBackups(backupRootPath: Path, latestBackupPath: Path) {
        val backups = runCatching { kaliumFileSystem.listDirectories(backupRootPath) }
            .getOrElse { emptyList() }
            .filter { it.name.startsWith("${CRYPTO_BACKUP_PREFIX}_${userId}_") }
            .filter { it.name.endsWith(".zip") }
            .filter { it != latestBackupPath }

        backups.forEach { backup ->
            runCatching { kaliumFileSystem.delete(backup) }
                .onFailure { kaliumLogger.w("Failed to delete old backup at $backup", it) }
        }
    }

    private fun createMetadataFile(
        cryptoBackupRootPath: Path,
        mlsExportData: CryptoBackupMetadata,
        proteusExportData: CryptoBackupMetadata,
        lastProcessedEventId: String,
    ): Path {
        val metadata = CryptoStateBackupMetadata(
            version = CryptoStateBackupMetadata.CURRENT_VERSION,
            clientId = mlsExportData.clientId.value,
            lastProcessedEventId = lastProcessedEventId,
            mlsDbPassphrase = Base64.encode(mlsExportData.passphrase),
            proteusDbPassphrase = Base64.encode(proteusExportData.passphrase)
        )
        val metadataJson = Json.encodeToString(metadata)

        val metadataFilePath = cryptoBackupRootPath.resolve(BackupConstants.BACKUP_METADATA_FILE_NAME)
        kaliumFileSystem.sink(metadataFilePath).buffer().use {
            it.write(metadataJson.encodeToByteArray())
        }
        return metadataFilePath
    }

    private fun createBackupZip(
        mlsDbBytes: ByteArray,
        proteusDbBytes: ByteArray,
        metadataPath: Path,
        backupZipPath: Path
    ): Either<CoreFailure, Unit> {
        return try {
            val backupSink = kaliumFileSystem.sink(backupZipPath)
            val filesList = listOf(
                kaliumFileSystem.source(metadataPath) to BackupConstants.BACKUP_METADATA_FILE_NAME,
                Buffer().apply { write(mlsDbBytes) } as Source to MLS_KEYSTORE_NAME,
                Buffer().apply { write(proteusDbBytes) } as Source to PROTEUS_KEYSTORE_NAME
            )

            createCompressedFile(filesList, backupSink).fold(
                { error -> Either.Left(error) },
                { Either.Right(Unit) }
            )
        } catch (e: Exception) {
            kaliumLogger.e("CoreCrypto backup ZIP creation failed", e)
            Either.Left(StorageFailure.Generic(e))
        }
    }

    companion object {
        const val CRYPTO_BACKUP_PREFIX = "crypto_backup"
        const val MLS_KEYSTORE_NAME = "keystore-mls"
        const val PROTEUS_KEYSTORE_NAME = "keystore-proteus"
        const val TEMP_CRYPTO_BACKUP_DIR = "crypto_backup_temp"
    }
}

public sealed interface BackupCryptoDBResult {
    public data class Success(
        val backupFilePath: Path,
        val backupName: String,
        val lastProcessedEventId: String
    ) : BackupCryptoDBResult
    public data class Failure(val error: CoreFailure) : BackupCryptoDBResult
}
