/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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

import com.wire.kalium.cryptography.backup.BackupHeader.HeaderDecodingErrors
import com.wire.kalium.cryptography.backup.BackupHeader.HeaderDecodingErrors.INVALID_FORMAT
import com.wire.kalium.cryptography.backup.BackupHeader.HeaderDecodingErrors.INVALID_USER_ID
import com.wire.kalium.cryptography.backup.BackupHeader.HeaderDecodingErrors.INVALID_VERSION
import com.wire.kalium.cryptography.backup.Passphrase
import com.wire.kalium.cryptography.utils.ChaCha20Decryptor.decryptBackupFile
import com.wire.kalium.logic.data.asset.KaliumFileSystem
import com.wire.kalium.logic.data.event.toMigratedMessage
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.message.MigratedMessage
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.feature.CurrentClientIdProvider
import com.wire.kalium.logic.feature.backup.BackupConstants.BACKUP_ENCRYPTED_EXTENSION
import com.wire.kalium.logic.feature.backup.BackupConstants.BACKUP_WEB_MESSAGES_FILE_NAME
import com.wire.kalium.logic.feature.backup.BackupConstants.createBackupFileName
import com.wire.kalium.logic.feature.backup.RestoreBackupResult.BackupRestoreFailure.BackupIOFailure
import com.wire.kalium.logic.feature.backup.RestoreBackupResult.BackupRestoreFailure.DecryptionFailure
import com.wire.kalium.logic.feature.backup.RestoreBackupResult.BackupRestoreFailure.IncompatibleBackup
import com.wire.kalium.logic.feature.backup.RestoreBackupResult.BackupRestoreFailure.InvalidUserId
import com.wire.kalium.logic.feature.backup.RestoreBackupResult.Failure
import com.wire.kalium.logic.feature.message.PersistMigratedMessagesUseCase
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.mapLeft
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.sync.incremental.RestartSlowSyncProcessForRecoveryUseCase
import com.wire.kalium.logic.util.extractCompressedFile
import com.wire.kalium.logic.wrapStorageRequest
import com.wire.kalium.network.api.base.authenticated.notification.EventContentDTO
import com.wire.kalium.network.tools.KtxSerializer
import com.wire.kalium.persistence.backup.DatabaseImporter
import com.wire.kalium.util.DateTimeUtil
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.DecodeSequenceMode
import kotlinx.serialization.json.decodeToSequence
import okio.Path
import okio.Source
import okio.buffer
import okio.use

interface RestoreBackupUseCase {

    /**
     * Restores a valid previously created backup file into the current database, respecting the current data if there is any overlap.
     * @param backupFilePath The absolute file system path to the backup file.
     * @param password the password used to encrypt the original backup file. Null if the file was not encrypted.
     * @return A [RestoreBackupResult] indicating the success or failure of the operation.
     */
    suspend operator fun invoke(backupFilePath: Path, password: String?): RestoreBackupResult
}

@Suppress("TooManyFunctions", "LongParameterList")
internal class RestoreBackupUseCaseImpl(
    private val databaseImporter: DatabaseImporter,
    private val kaliumFileSystem: KaliumFileSystem,
    private val userId: UserId,
    private val userRepository: UserRepository,
    private val currentClientIdProvider: CurrentClientIdProvider,
    private val persistMigratedMessages: PersistMigratedMessagesUseCase,
    private val restartSlowSyncProcessForRecovery: RestartSlowSyncProcessForRecoveryUseCase,
    private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl,
    private val idMapper: IdMapper = MapperProvider.idMapper()
) : RestoreBackupUseCase {

    override suspend operator fun invoke(backupFilePath: Path, password: String?): RestoreBackupResult =
        withContext(dispatchers.io) {
            extractCompressedBackup(backupFilePath.normalized())
                .flatMap { extractedBackupRootPath ->
                    if (password.isNullOrEmpty()) {
                        importUnencryptedBackup(extractedBackupRootPath, this)
                    } else {
                        importEncryptedBackup(extractedBackupRootPath, password)
                    }
                }
                .fold({ Failure(it) }, { RestoreBackupResult.Success })
        }

    private suspend fun importUnencryptedBackup(
        extractedBackupRootPath: Path,
        coroutineScope: CoroutineScope
    ): Either<RestoreBackupResult.BackupRestoreFailure, Unit> {
        return checkIsValidAuthor(extractedBackupRootPath).flatMap { metaData ->
            if (metaData.isWebBackup()) {
                kaliumLogger.d("KBX backup version ${metaData.version}")
                if (metaData.version == "19") {
                    return importWebBackup(extractedBackupRootPath, coroutineScope)
                } else {
                    return Either.Left(IncompatibleBackup("The provided backup format is not supported"))
                }
            } else {
                val isFromOtherClient = isFromOtherClient(metaData)
                return getDbPathAndImport(extractedBackupRootPath, isFromOtherClient)
            }
        }
    }

    private suspend fun importEncryptedBackup(
        extractedBackupRootPath: Path,
        password: String
    ): Either<RestoreBackupResult.BackupRestoreFailure, Unit> {
        return checkIsValidEncryption(extractedBackupRootPath).flatMap { encryptedFilePath ->
            decryptExtractAndImportBackup(encryptedFilePath, extractedBackupRootPath, password)
        }
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

    private fun extractCompressedBackup(backupFilePath: Path): Either<RestoreBackupResult.BackupRestoreFailure, Path> {
        val tempCompressedFileSource = kaliumFileSystem.source(backupFilePath)
        val extractedFilesRootPath = createExtractedFilesRootPath()
        return extractFiles(tempCompressedFileSource, extractedFilesRootPath)
            .fold({
                kaliumLogger.e("Failed to extract backup files")
                Either.Left(BackupIOFailure("Failed to extract backup files"))
            }, {
                Either.Right(extractedFilesRootPath)
            })
    }

    private suspend fun decryptExtractAndImportBackup(
        encryptedFilePath: Path,
        extractedBackupRootPath: Path,
        password: String
    ): Either<RestoreBackupResult.BackupRestoreFailure, Unit> {
        val backupSource = kaliumFileSystem.source(encryptedFilePath)
        val userHandle = userRepository.getSelfUser()?.handle?.map {
            it.toString().replace(".", "-")
        }?.first()
        val timeStamp = DateTimeUtil.currentIsoDateTimeString()
        val backupName = createBackupFileName(userHandle, timeStamp)
        val extractedBackupPath = extractedBackupRootPath / backupName

        val backupSink = kaliumFileSystem.sink(extractedBackupPath)
        val userIdEntity = idMapper.toCryptoModel(userId)
        val (decodingError, backupSize) = decryptBackupFile(
            backupSource,
            backupSink,
            Passphrase(password),
            userIdEntity
        )

        if (decodingError != null) {
            return Either.Left(mappedDecodingError(decodingError))
        }

        return if (backupSize > 0) {
            // On successful decryption, we still need to extract the zip file to do sanity checks and get the database file
            extractFiles(kaliumFileSystem.source(extractedBackupPath), extractedBackupRootPath).fold({
                kaliumLogger.e("Failed to extract encrypted backup files")
                Either.Left(BackupIOFailure("Failed to extract encrypted backup files"))
            }, {
                kaliumFileSystem.delete(extractedBackupPath)
                backupMetadata(extractedBackupRootPath).flatMap { metadata ->
                    val isFromOtherClient = isFromOtherClient(metadata)
                    getDbPathAndImport(extractedBackupRootPath, isFromOtherClient)
                }
            })
        } else {
            Either.Left(RestoreBackupResult.BackupRestoreFailure.InvalidPassword)
        }
    }

    private fun mappedDecodingError(decodingError: HeaderDecodingErrors): RestoreBackupResult.BackupRestoreFailure = when (decodingError) {
        INVALID_USER_ID -> InvalidUserId
        INVALID_VERSION -> IncompatibleBackup("The provided backup version is lower than the minimum supported version")
        INVALID_FORMAT -> IncompatibleBackup("The provided backup format is not supported")
    }

    private suspend fun checkIsValidEncryption(extractedBackupPath: Path): Either<RestoreBackupResult.BackupRestoreFailure, Path> =
        with(kaliumFileSystem) {
            val encryptedFilePath = listDirectories(extractedBackupPath).firstOrNull {
                it.name.substringAfterLast('.', "") == BACKUP_ENCRYPTED_EXTENSION
            }
            return if (encryptedFilePath == null) return Either.Left(DecryptionFailure("No encrypted backup file found"))
            else Either.Right(encryptedFilePath)
        }

    private suspend fun checkIsValidAuthor(extractedBackupRootPath: Path): Either<RestoreBackupResult.BackupRestoreFailure, BackupMetadata> {
        return backupMetadata(extractedBackupRootPath).flatMap { isValidBackupAuthor(it) }
    }

    private fun extractFiles(inputSource: Source, extractedBackupRootPath: Path) =
        extractCompressedFile(inputSource, extractedBackupRootPath, kaliumFileSystem)

    private suspend fun getDbPathAndImport(
        extractedBackupRootPath: Path,
        isFromOtherClient: Boolean
    ): Either<RestoreBackupResult.BackupRestoreFailure, Unit> {
        return getBackupDBPath(extractedBackupRootPath)?.let { dbPath ->
            importDBFile(dbPath, isFromOtherClient)
        } ?: Either.Left(BackupIOFailure("No valid db file found in the backup"))
    }

    @OptIn(ExperimentalSerializationApi::class)
    private suspend fun importWebBackup(
        encryptedFilePath: Path,
        coroutineScope: CoroutineScope
    ): Either<RestoreBackupResult.BackupRestoreFailure, Unit> = with(kaliumFileSystem) {
        listDirectories(encryptedFilePath).firstOrNull { it.name == BACKUP_WEB_MESSAGES_FILE_NAME }?.let { path ->
            source(path).buffer()
                .use {
                    val sequence = KtxSerializer.json.decodeToSequence<EventContentDTO>(
                        it.inputStream(),
                        DecodeSequenceMode.ARRAY_WRAPPED
                    )
                    val iterator = sequence.iterator()

                    val migratedMessagesBatch = mutableListOf<MigratedMessage>()
                    while (iterator.hasNext()) {
                        val eventContentDTO = iterator.next()
                        val migratedMessage = eventContentDTO.toMigratedMessage(userId.domain)
                        if (migratedMessage != null) {
                            migratedMessagesBatch.add(migratedMessage)
                        }

                        // send migrated messages in batches to not face any OOM errors
                        if (migratedMessagesBatch.size == 1000) {
                            persistMigratedMessages(migratedMessagesBatch, coroutineScope)
                            migratedMessagesBatch.clear()
                        }
                    }
                    persistMigratedMessages(migratedMessagesBatch, coroutineScope)
                    migratedMessagesBatch.clear()

                    kaliumLogger.d("$TAG restartSlowSyncProcessForRecovery")
                    restartSlowSyncProcessForRecovery.invoke()
                    Either.Right(Unit)
                }
        } ?: Either.Left(BackupIOFailure("No valid db file found in the backup"))
    }

    private suspend fun importDBFile(userDBPath: Path, isFromOtherClient: Boolean): Either<RestoreBackupResult.BackupRestoreFailure, Unit> =
        wrapStorageRequest {
            databaseImporter.importFromFile(userDBPath.toString(), isFromOtherClient)
        }.mapLeft { BackupIOFailure("There was an error when importing the DB") }

    private suspend fun getBackupDBPath(extractedBackupRootFilesPath: Path): Path? =
        kaliumFileSystem.listDirectories(extractedBackupRootFilesPath).firstOrNull { it.name.contains(".db") }

    private suspend fun backupMetadata(extractedBackupPath: Path): Either<RestoreBackupResult.BackupRestoreFailure, BackupMetadata> =
        with(kaliumFileSystem) {
            listDirectories(extractedBackupPath)
                .firstOrNull { it.name == BackupConstants.BACKUP_METADATA_FILE_NAME }
                ?.let { metadataFile ->
                    try {
                        source(metadataFile).buffer()
                            .use { Either.Right(KtxSerializer.json.decodeFromString(it.readUtf8())) }
                    } catch (e: Exception) {
                        Either.Left(IncompatibleBackup(e.toString()))
                    }
                } ?: Either.Left(IncompatibleBackup("The provided backup format is not supported"))
        }

    private fun isValidBackupAuthor(metadata: BackupMetadata): Either<RestoreBackupResult.BackupRestoreFailure, BackupMetadata> =
        if (metadata.userId == userId.toString() || metadata.userId == userId.value)
            Either.Right(metadata)
        else
            Either.Left(InvalidUserId)

    private suspend fun isFromOtherClient(metadata: BackupMetadata): Boolean =
        metadata.clientId != currentClientIdProvider().fold({ "" }, { it.value })

    private companion object {
        const val TAG = "[RestoreBackupUseCase]"
    }
}

sealed class RestoreBackupResult {
    data class Failure(val failure: BackupRestoreFailure) : RestoreBackupResult()
    object Success : RestoreBackupResult()

    sealed class BackupRestoreFailure(open val cause: String) {
        object InvalidPassword : BackupRestoreFailure("The provided password is invalid")
        object InvalidUserId : BackupRestoreFailure("User id in the backup file does not match the current user id")
        data class IncompatibleBackup(override val cause: String) : BackupRestoreFailure(cause)
        data class BackupIOFailure(override val cause: String) : BackupRestoreFailure(cause)
        data class DecryptionFailure(override val cause: String) : BackupRestoreFailure(cause)
    }
}

private const val EXTRACTED_FILES_PATH = "extractedFiles"
