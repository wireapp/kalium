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
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.fold
import com.wire.kalium.common.functional.nullableFold
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.cryptography.backup.BackupCoder
import com.wire.kalium.logic.clientPlatform
import com.wire.kalium.logic.data.asset.KaliumFileSystem
import com.wire.kalium.logic.data.client.CryptoTransactionProvider
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
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

/**
 * Performs the backup of the cryptographic database by exporting it, creating a metadata file, and packaging them into a ZIP file.
 */
public interface BackupCryptoDBUseCase {
    public suspend operator fun invoke(): BackupCryptoDBResult
}

internal class BackupCryptoDBUseCaseImpl(
    private val userId: UserId,
    private val clientIdProvider: CurrentClientIdProvider,
    private val cryptoTransactionProvider: CryptoTransactionProvider,
    private val userRepository: UserRepository,
    private val kaliumFileSystem: KaliumFileSystem,
    private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl,
) : BackupCryptoDBUseCase {

    @Suppress("TooGenericExceptionCaught")
    override suspend fun invoke(): BackupCryptoDBResult = withContext(dispatchers.default) {
        val dbBytes = cryptoTransactionProvider.mlsTransaction("backup_read") { _ ->
            val exportData = cryptoTransactionProvider.mlsClientProvider
                .exportCryptoDB()
                .fold(
                    { return@mlsTransaction Either.Left(it) },
                    { it }
                )

            try {
                val bytes = kaliumFileSystem.source(exportData.dbPath.toPath()).use {
                    it.buffer().readByteArray()
                }

                Either.Right(bytes)
            } catch (e: Exception) {
                Either.Left(StorageFailure.Generic(e))
            }
        }.fold(
            { return@withContext BackupCryptoDBResult.Failure(it) },
            { it }
        )

        try {
            val timeStamp = DateTimeUtil.currentSimpleDateTimeString()
            val backupName = "corecrypto_backup_${userId}_$timeStamp.zip"
            val backupFilePath = kaliumFileSystem.tempFilePath(backupName)
            val metadataPath = createMetadataFile(userId)

            createBackupZip(dbBytes, metadataPath, backupFilePath).fold(
                { error -> BackupCryptoDBResult.Failure(error) },
                {
                    BackupCryptoDBResult.Success(backupFilePath, backupName)
                }
            )
        } catch (e: Exception) {
            kaliumLogger.e("CoreCrypto export failed", e)
            BackupCryptoDBResult.Failure(StorageFailure.Generic(e))
        } finally {
            // todo(ym): add logic to replace last or delete old once a new one is created.
//             kaliumFileSystem.delete(kaliumFileSystem.tempFilePath(BackupConstants.BACKUP_METADATA_FILE_NAME))
        }
    }

    private suspend fun createMetadataFile(userId: UserId): Path {
        val clientId = clientIdProvider().nullableFold({ null }, { it.value })
        val creationTime = DateTimeUtil.currentIsoDateTimeString()
        val metadata = BackupMetadata(
            clientPlatform,
            BackupCoder.version,
            userId.toString(),
            creationTime,
            clientId
        )
        val metadataJson = Json.encodeToString(metadata)

        val metadataFilePath = kaliumFileSystem.tempFilePath(BackupConstants.BACKUP_METADATA_FILE_NAME)
        kaliumFileSystem.sink(metadataFilePath).buffer().use {
            it.write(metadataJson.encodeToByteArray())
        }
        return metadataFilePath
    }

    private fun createBackupZip(
        dbBytes: ByteArray,
        metadataPath: Path,
        backupZipPath: Path
    ): Either<CoreFailure, Unit> {
        return try {
            val backupSink = kaliumFileSystem.sink(backupZipPath)
            val filesList = listOf(
                kaliumFileSystem.source(metadataPath) to BackupConstants.BACKUP_METADATA_FILE_NAME,
                Buffer().apply { write(dbBytes) } as Source to "keystore"
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
}

public sealed interface BackupCryptoDBResult {
    public data class Success(val backupFilePath: Path, val backupName: String) : BackupCryptoDBResult
    public data class Failure(val error: CoreFailure) : BackupCryptoDBResult
}
