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
package com.wire.kalium.logic.feature.backup

import com.wire.kalium.cryptography.backup.BackupCoder
import com.wire.kalium.cryptography.backup.Passphrase
import com.wire.kalium.cryptography.utils.ChaCha20Encryptor.encryptBackupFile
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.clientPlatform
import com.wire.kalium.logic.data.asset.KaliumFileSystem
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.nullableFold
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.util.createCompressedFile
import com.wire.kalium.persistence.backup.ObfuscatedCopyExporter
import com.wire.kalium.util.DateTimeUtil
import com.wire.kalium.util.DelicateKaliumApi
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.FileNotFoundException
import okio.Path
import okio.Path.Companion.toPath
import okio.Sink
import okio.Source
import okio.buffer
import okio.use

@DelicateKaliumApi("This class is used for debugging purposes only")
class CreateUnEncryptedCopyUseCase internal constructor(
    private val userId: UserId,
    private val clientIdProvider: CurrentClientIdProvider,
    private val userRepository: UserRepository,
    private val kaliumFileSystem: KaliumFileSystem,
    private val obfuscatedCopyExporter: ObfuscatedCopyExporter,
    private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl,
    private val idMapper: IdMapper = MapperProvider.idMapper(),
) {

    @DelicateKaliumApi("This function is used for debugging purposes only")
    suspend operator fun invoke(password: String?): CreateBackupResult = withContext(dispatchers.default) {
        val userHandle = userRepository.getSelfUser()?.handle?.replace(".", "-")
        val timeStamp = DateTimeUtil.currentSimpleDateTimeString()
        val backupName = createFinalZipName(userHandle, timeStamp)
        val backupFilePath = kaliumFileSystem.tempFilePath(backupName)
        deletePreviousBackupFiles(backupFilePath)

        val plainDBPath =
            obfuscatedCopyExporter.exportToPlainDB()?.toPath()
                ?: return@withContext CreateBackupResult.Failure(StorageFailure.DataNotFound)

        try {
            createBackupFile(userId, plainDBPath, backupFilePath).fold(
                { error -> CreateBackupResult.Failure(error) },
                { (backupFilePath, backupSize) ->
                    if (password != null) {
                        encryptAndCompressFile(backupFilePath, password)
                    } else CreateBackupResult.Success(backupFilePath, backupSize, backupFilePath.name)
                })
        } finally {
            obfuscatedCopyExporter.deleteCopyFile()
        }
    }

    private suspend fun encryptAndCompressFile(backupFilePath: Path, password: String): CreateBackupResult {
        val encryptedBackupFilePath = kaliumFileSystem.tempFilePath(COPY_ENCRYPTED_FILE_NAME)
        val backupEncryptedDataSize = encryptBackup(
            kaliumFileSystem.source(backupFilePath),
            kaliumFileSystem.sink(encryptedBackupFilePath),
            Passphrase(password)
        )
        if (backupEncryptedDataSize == 0L)
            return CreateBackupResult.Failure(StorageFailure.Generic(RuntimeException("Failed to encrypt backup file")))

        val finalBackupFilePath = kaliumFileSystem.tempFilePath("encrypted-${backupFilePath.name}")

        return createCompressedFile(
            listOf(kaliumFileSystem.source(encryptedBackupFilePath) to encryptedBackupFilePath.name),
            kaliumFileSystem.sink(finalBackupFilePath)
        ).fold({
            CreateBackupResult.Failure(StorageFailure.Generic(RuntimeException("Failed to compress encrypted backup file")))
        }, { backupEncryptedCompressedDataSize ->
            deleteTempFiles(backupFilePath, encryptedBackupFilePath)

            if (backupEncryptedCompressedDataSize > 0) {
                CreateBackupResult.Success(finalBackupFilePath, backupEncryptedCompressedDataSize, finalBackupFilePath.name)
            } else {
                CreateBackupResult.Failure(StorageFailure.Generic(RuntimeException("Failed to encrypt backup file")))
            }
        })
    }

    private fun deletePreviousBackupFiles(backupFilePath: Path) {
        if (kaliumFileSystem.exists(backupFilePath))
            kaliumFileSystem.delete(backupFilePath)
    }

    private fun deleteTempFiles(backupFilePath: Path, encryptedBackupFilePath: Path) {
        kaliumFileSystem.delete(backupFilePath)
        kaliumFileSystem.delete(encryptedBackupFilePath)
        kaliumFileSystem.delete(kaliumFileSystem.tempFilePath(COPY_METADATA_FILE_NAME))
    }

    private suspend fun encryptBackup(backupFileSource: Source, encryptedBackupSink: Sink, passphrase: Passphrase) =
        encryptBackupFile(backupFileSource, encryptedBackupSink, idMapper.toCryptoModel(userId), passphrase)

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

        val metadataFilePath = kaliumFileSystem.tempFilePath(COPY_METADATA_FILE_NAME)
        kaliumFileSystem.sink(metadataFilePath).buffer().use {
            it.write(metadataJson.encodeToByteArray())
        }
        return metadataFilePath
    }

    private suspend fun createBackupFile(
        userId: UserId,
        plainDBPath: Path,
        backupZipFilePath: Path
    ): Either<CoreFailure, Pair<Path, Long>> {
        return try {
            val backupSink = kaliumFileSystem.sink(backupZipFilePath)
            val backupMetadataPath = createMetadataFile(userId)
            val filesList = listOf(
                kaliumFileSystem.source(backupMetadataPath) to COPY_METADATA_FILE_NAME,
                kaliumFileSystem.source(plainDBPath) to COPY_USER_DB_NAME
            )

            createCompressedFile(filesList, backupSink).flatMap { compressedFileSize ->
                Either.Right(backupZipFilePath to compressedFileSize)
            }
        } catch (e: FileNotFoundException) {
            kaliumLogger.e("There was an error when fetching the user db data path", e)
            Either.Left(StorageFailure.DataNotFound)
        }
    }

    private fun createFinalZipName(userHandle: String?, timestampIso: String) = // file names cannot have special characters
        "$COPY_FILE_NAME_PREFIX-$userHandle-${timestampIso.replace(":", "-")}.zip"

    private companion object {
        const val COPY_FILE_NAME_PREFIX = "OBFUSCATED_COPY_WBX"
        const val COPY_ENCRYPTED_FILE_NAME = "obfuscated-copy-user-backup.cc20"

        // BACKUP_METADATA_FILE_NAME and BACKUP_USER_DB_NAME must not be changed
        // if there is a need to change them, please create a new file names and add it to the list of acceptedFileNames()
        const val COPY_USER_DB_NAME = "user-backup-database.db"
        const val COPY_METADATA_FILE_NAME = "export.json"
    }
}
