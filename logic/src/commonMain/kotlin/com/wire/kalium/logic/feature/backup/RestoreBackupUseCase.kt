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
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.data.client.CurrentClientIdProvider
import com.wire.kalium.logic.feature.backup.BackupConstants.BACKUP_ENCRYPTED_EXTENSION
import com.wire.kalium.logic.feature.backup.BackupConstants.BACKUP_ZIP_FILE_NAME
import com.wire.kalium.logic.feature.backup.RestoreBackupResult.BackupRestoreFailure.BackupIOFailure
import com.wire.kalium.logic.feature.backup.RestoreBackupResult.BackupRestoreFailure.DecryptionFailure
import com.wire.kalium.logic.feature.backup.RestoreBackupResult.BackupRestoreFailure.IncompatibleBackup
import com.wire.kalium.logic.feature.backup.RestoreBackupResult.BackupRestoreFailure.InvalidUserId
import com.wire.kalium.logic.feature.backup.RestoreBackupResult.Failure
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.util.extractCompressedFile
import com.wire.kalium.logic.wrapStorageRequest
import com.wire.kalium.persistence.backup.DatabaseImporter
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
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
    private val currentClientIdProvider: CurrentClientIdProvider,
    private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl,
    private val idMapper: IdMapper = MapperProvider.idMapper()
) : RestoreBackupUseCase {

    override suspend operator fun invoke(backupFilePath: Path, password: String?): RestoreBackupResult =
        withContext(dispatchers.io) {
            extractCompressedBackup(backupFilePath.normalized())
                .flatMap { extractedBackupRootPath ->
                    runSanityChecks(extractedBackupRootPath, password)
                        .map { (encryptedFilePath, isPasswordProtected) ->
                            if (isPasswordProtected) {
                                decryptExtractAndImportBackup(encryptedFilePath!!, extractedBackupRootPath, password!!)
                            } else {
                                val isFromOtherClient = isFromOtherClient(extractedBackupRootPath)
                                getDbPathAndImport(extractedBackupRootPath, isFromOtherClient)
                            }
                        }
                }
                .fold({ it }, { it })
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

    private fun extractCompressedBackup(backupFilePath: Path): Either<Failure, Path> {
        val tempCompressedFileSource = kaliumFileSystem.source(backupFilePath)
        val extractedFilesRootPath = createExtractedFilesRootPath()
        return extractFiles(tempCompressedFileSource, extractedFilesRootPath)
            .fold({
                kaliumLogger.e("Failed to extract backup files")
                Either.Left(Failure(BackupIOFailure("Failed to extract backup files")))
            }, {
                Either.Right(extractedFilesRootPath)
            })
    }

    private suspend fun decryptExtractAndImportBackup(
        encryptedFilePath: Path,
        extractedBackupRootPath: Path,
        password: String
    ): RestoreBackupResult {
        val backupSource = kaliumFileSystem.source(encryptedFilePath)
        val extractedBackupPath = extractedBackupRootPath / BACKUP_ZIP_FILE_NAME
        val backupSink = kaliumFileSystem.sink(extractedBackupPath)
        val userIdEntity = idMapper.toCryptoModel(userId)
        val (decodingError, backupSize) = decryptBackupFile(
            backupSource,
            backupSink,
            Passphrase(password),
            userIdEntity
        )

        if (decodingError != null) {
            return mappedDecodingError(decodingError)
        }

        return if (backupSize > 0) {
            // On successful decryption, we still need to extract the zip file to do sanity checks and get the database file
            extractFiles(kaliumFileSystem.source(extractedBackupPath), extractedBackupRootPath).fold({
                kaliumLogger.e("Failed to extract encrypted backup files")
                Failure(BackupIOFailure("Failed to extract encrypted backup files"))
            }, {
                val isFromOtherClient = isFromOtherClient(extractedBackupRootPath)
                kaliumFileSystem.delete(extractedBackupPath)
                getDbPathAndImport(extractedBackupRootPath, isFromOtherClient)
            })
        } else {
            Failure(RestoreBackupResult.BackupRestoreFailure.InvalidPassword)
        }
    }

    private fun mappedDecodingError(decodingError: HeaderDecodingErrors): RestoreBackupResult = when (decodingError) {
        INVALID_USER_ID -> Failure(InvalidUserId)
        INVALID_VERSION -> Failure(IncompatibleBackup("The provided backup version is lower than the minimum supported version"))
        INVALID_FORMAT -> Failure(IncompatibleBackup("The provided backup format is not supported"))
    }

    private suspend fun runSanityChecks(extractedBackupPath: Path, password: String?): Either<Failure, Pair<Path?, Boolean>> =
        if (password.isNullOrEmpty()) {
            // Backup is not encrypted so we don't need to return the path to the encrypted file
            checkIsValidAuthor(extractedBackupPath).fold({ Either.Left(it) }, { Either.Right(null to false) })
        } else {
            // If the backup is encrypted, the sanity checks are done when decoding the file
            checkIsValidEncryption(extractedBackupPath)
        }

    private suspend fun checkIsValidEncryption(extractedBackupPath: Path): Either<Failure, Pair<Path?, Boolean>> = with(kaliumFileSystem) {
        val encryptedFilePath = listDirectories(extractedBackupPath).firstOrNull {
            it.name.substringAfterLast('.', "") == BACKUP_ENCRYPTED_EXTENSION
        }
        return if (encryptedFilePath == null) return Either.Left(Failure(DecryptionFailure("No encrypted backup file found")))
        else Either.Right(encryptedFilePath to true)
    }

    private suspend fun checkIsValidAuthor(extractedBackupRootPath: Path): Either<Failure, Unit> {
        val isValidBackupAuthor = isValidBackupAuthor(extractedBackupRootPath)
        return if (!isValidBackupAuthor) Either.Left(Failure(InvalidUserId))
        else Either.Right(Unit)
    }

    private fun extractFiles(inputSource: Source, extractedBackupRootPath: Path) =
        extractCompressedFile(inputSource, extractedBackupRootPath, kaliumFileSystem)

    private suspend fun getDbPathAndImport(
        extractedBackupRootPath: Path,
        isFromOtherClient: Boolean
    ): RestoreBackupResult {
        return getBackupDBPath(extractedBackupRootPath)?.let { dbPath ->
            importDBFile(dbPath, isFromOtherClient)
        } ?: Failure(BackupIOFailure("No valid db file found in the backup"))
    }

    private suspend fun importDBFile(userDBPath: Path, isFromOtherClient: Boolean) = wrapStorageRequest {
        databaseImporter.importFromFile(userDBPath.toString(), isFromOtherClient)
    }.fold({ Failure(BackupIOFailure("There was an error when importing the DB")) }, { RestoreBackupResult.Success })

    private suspend fun getBackupDBPath(extractedBackupRootFilesPath: Path): Path? =
        kaliumFileSystem.listDirectories(extractedBackupRootFilesPath).firstOrNull { it.name.contains(".db") }

    private suspend fun backupMetadata(extractedBackupPath: Path): BackupMetadata? = with(kaliumFileSystem) {
        listDirectories(extractedBackupPath)
            .firstOrNull { it.name == BackupConstants.BACKUP_METADATA_FILE_NAME }
            ?.let { metadataFile ->
                source(metadataFile).buffer()
                    .use { Json.decodeFromString<BackupMetadata>(it.readUtf8()) }
            }
    }

    private suspend fun isValidBackupAuthor(extractedBackupPath: Path): Boolean =
        backupMetadata(extractedBackupPath)?.userId == userId.toString()

    private suspend fun isFromOtherClient(extractedBackupPath: Path): Boolean =
        backupMetadata(extractedBackupPath)?.clientId != currentClientIdProvider().fold({ "" }, { it.value })
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
