/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
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
@file:Suppress("konsist.useCasesShouldNotAccessDaoLayerDirectly", "konsist.useCasesShouldNotAccessNetworkLayerDirectly")

package com.wire.kalium.logic.feature.backup

import com.wire.kalium.cryptography.backup.BackupHeader.HeaderDecodingErrors
import com.wire.kalium.cryptography.backup.BackupHeader.HeaderDecodingErrors.INVALID_FORMAT
import com.wire.kalium.cryptography.backup.BackupHeader.HeaderDecodingErrors.INVALID_USER_ID
import com.wire.kalium.cryptography.backup.BackupHeader.HeaderDecodingErrors.INVALID_VERSION
import com.wire.kalium.cryptography.backup.Passphrase
import com.wire.kalium.cryptography.utils.ChaCha20Decryptor.decryptBackupFile
import com.wire.kalium.logic.data.asset.KaliumFileSystem
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.feature.backup.BackupConstants.BACKUP_ENCRYPTED_EXTENSION
import com.wire.kalium.logic.feature.backup.BackupConstants.acceptedFileNames
import com.wire.kalium.logic.feature.backup.BackupConstants.createBackupFileName
import com.wire.kalium.logic.feature.backup.RestoreBackupResult.BackupRestoreFailure.BackupIOFailure
import com.wire.kalium.logic.feature.backup.RestoreBackupResult.BackupRestoreFailure.DecryptionFailure
import com.wire.kalium.logic.feature.backup.RestoreBackupResult.BackupRestoreFailure.IncompatibleBackup
import com.wire.kalium.logic.feature.backup.RestoreBackupResult.BackupRestoreFailure.InvalidUserId
import com.wire.kalium.logic.feature.backup.RestoreBackupResult.Failure
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.mapLeft
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.util.ExtractFilesParam
import com.wire.kalium.logic.util.extractCompressedFile
import com.wire.kalium.logic.wrapStorageRequest
import com.wire.kalium.network.tools.KtxSerializer
import com.wire.kalium.persistence.backup.DatabaseImporter
import com.wire.kalium.util.DateTimeUtil
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
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
    private val restoreWebBackup: RestoreWebBackupUseCase,
    private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl,
    private val idMapper: IdMapper = MapperProvider.idMapper()
) : RestoreBackupUseCase {

    override suspend operator fun invoke(backupFilePath: Path, password: String?): RestoreBackupResult =
        withContext(dispatchers.io) {
            extractCompressedBackup(backupFilePath.normalized())
                .flatMap { extractedBackupRootPath ->
                    if (password.isNullOrEmpty()) {
                        backupMetadata(extractedBackupRootPath)
                            .flatMap { metadata ->
                                importUnencryptedBackup(extractedBackupRootPath, metadata)
                            }
                    } else {
                        importEncryptedBackup(extractedBackupRootPath, password)
                    }
                }
                .fold({ error ->
                    kaliumLogger.e("$TAG Failed to restore the backup, reason: ${error.failure}")
                    error
                }, {
                    kaliumLogger.i("$TAG Backup restored successfully")
                    RestoreBackupResult.Success
                })
        }

    private suspend fun importUnencryptedBackup(
        extractedBackupRootPath: Path,
        metadata: BackupMetadata,
    ): Either<Failure, Unit> = isValidBackupAuthor(metadata)
        .flatMap { metaData ->
            if (metaData.isWebBackup()) {
                return when (val webBackup = restoreWebBackup(extractedBackupRootPath, metaData)) {
                    is Failure -> Either.Left(webBackup)
                    RestoreBackupResult.Success -> Either.Right(Unit)
                }
            } else {
                val isFromOtherClient = isFromOtherClient(metaData)
                return getDbPathAndImport(extractedBackupRootPath, isFromOtherClient)
            }
        }

    private suspend fun importEncryptedBackup(
        extractedBackupRootPath: Path,
        password: String,
    ): Either<Failure, Unit> {
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

    private fun extractCompressedBackup(backupFilePath: Path): Either<Failure, Path> {
        val tempCompressedFileSource = kaliumFileSystem.source(backupFilePath)
        val extractedFilesRootPath = createExtractedFilesRootPath()
        return extractFiles(tempCompressedFileSource, extractedFilesRootPath)
            .fold({
                kaliumLogger.e("$TAG Failed to extract backup files")
                Either.Left(Failure(BackupIOFailure("Failed to extract backup files")))
            }, {
                Either.Right(extractedFilesRootPath)
            })
    }

    private suspend fun decryptExtractAndImportBackup(
        encryptedFilePath: Path,
        extractedBackupRootPath: Path,
        password: String
    ): Either<Failure, Unit> {
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
            return Either.Left(Failure(mappedDecodingError(decodingError)))
        }

        return if (backupSize > 0) {
            // On successful decryption, we still need to extract the zip file to do sanity checks and get the database file
            extractFiles(kaliumFileSystem.source(extractedBackupPath), extractedBackupRootPath).fold({
                kaliumLogger.e("$TAG Failed to extract encrypted backup files")
                Either.Left(Failure(BackupIOFailure("Failed to extract encrypted backup files")))
            }, {
                kaliumFileSystem.delete(extractedBackupPath)
                backupMetadata(extractedBackupRootPath).flatMap { metadata ->
                    val isFromOtherClient = isFromOtherClient(metadata)
                    getDbPathAndImport(extractedBackupRootPath, isFromOtherClient)
                }
            })
        } else {
            Either.Left(Failure(RestoreBackupResult.BackupRestoreFailure.InvalidPassword))
        }
    }

    private fun mappedDecodingError(decodingError: HeaderDecodingErrors): RestoreBackupResult.BackupRestoreFailure =
        when (decodingError) {
            INVALID_USER_ID -> InvalidUserId
            INVALID_VERSION -> IncompatibleBackup("The provided backup version is lower than the minimum supported version")
            INVALID_FORMAT -> IncompatibleBackup("mappedDecodingError: The provided backup format is not supported")
        }

    private suspend fun checkIsValidEncryption(extractedBackupPath: Path): Either<Failure, Path> =
        with(kaliumFileSystem) {
            listDirectories(extractedBackupPath)
                .firstOrNull { it.name.endsWith(".$BACKUP_ENCRYPTED_EXTENSION") }?.let {
                    Either.Right(it)
                } ?: Either.Left(Failure(DecryptionFailure("No encrypted backup file found")))
        }

    private fun extractFiles(inputSource: Source, extractedBackupRootPath: Path) =
        extractCompressedFile(inputSource, extractedBackupRootPath, ExtractFilesParam.Only(acceptedFileNames()), kaliumFileSystem)

    private suspend fun getDbPathAndImport(
        extractedBackupRootPath: Path,
        isFromOtherClient: Boolean
    ): Either<Failure, Unit> {
        return getBackupDBPath(extractedBackupRootPath)?.let { dbPath ->
            importDBFile(dbPath, isFromOtherClient)
        } ?: Either.Left(Failure(BackupIOFailure("No valid db file found in the backup")))
    }

    private suspend fun importDBFile(userDBPath: Path, isFromOtherClient: Boolean): Either<Failure, Unit> =
        wrapStorageRequest {
            databaseImporter.importFromFile(userDBPath.toString(), isFromOtherClient)
        }.mapLeft { Failure(BackupIOFailure("There was an error when importing the DB")) }

    private suspend fun getBackupDBPath(extractedBackupRootFilesPath: Path): Path? =
        kaliumFileSystem.listDirectories(extractedBackupRootFilesPath).firstOrNull { it.name.contains(".db") }

    private suspend fun backupMetadata(extractedBackupPath: Path): Either<Failure, BackupMetadata> =
        kaliumFileSystem.listDirectories(extractedBackupPath)
            .firstOrNull { it.name == BackupConstants.BACKUP_METADATA_FILE_NAME }
            .let { it ?: return Either.Left(Failure(IncompatibleBackup("backupMetadata: No metadata file found"))) }
            .let { metadataFile ->
                try {
                    kaliumFileSystem.source(metadataFile).buffer()
                        .use { Either.Right(KtxSerializer.json.decodeFromString(it.readUtf8())) }
                } catch (e: SerializationException) {
                    Either.Left(Failure(IncompatibleBackup(e.toString())))
                }
            }

    private fun isValidBackupAuthor(metadata: BackupMetadata): Either<Failure, BackupMetadata> =
        if (metadata.userId == userId.toString() || metadata.userId == userId.value) {
            Either.Right(metadata)
        } else {
            Either.Left(Failure(InvalidUserId))
        }

    private suspend fun isFromOtherClient(metadata: BackupMetadata): Boolean =
        metadata.clientId != currentClientIdProvider().fold({ "" }, { it.value })

    private companion object {
        const val TAG = "[RestoreBackupUseCase]"
    }
}

private const val EXTRACTED_FILES_PATH = "extractedFiles"
