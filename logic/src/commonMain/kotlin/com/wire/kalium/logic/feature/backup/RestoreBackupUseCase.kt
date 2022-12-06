package com.wire.kalium.logic.feature.backup

import com.wire.kalium.cryptography.backup.BackupCoder
import com.wire.kalium.cryptography.backup.BackupCoder.Header.HeaderDecodingErrors
import com.wire.kalium.cryptography.backup.BackupCoder.Header.HeaderDecodingErrors.INVALID_FORMAT
import com.wire.kalium.cryptography.backup.BackupCoder.Header.HeaderDecodingErrors.INVALID_USER_ID
import com.wire.kalium.cryptography.backup.BackupCoder.Header.HeaderDecodingErrors.INVALID_VERSION
import com.wire.kalium.cryptography.utils.ChaCha20Utils
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.asset.KaliumFileSystem
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.feature.backup.BackupConstants.BACKUP_UNENCRYPTED_FILE_NAME
import com.wire.kalium.logic.feature.backup.RestoreBackupResult.BackupRestoreFailure.BackupIOFailure
import com.wire.kalium.logic.feature.backup.RestoreBackupResult.BackupRestoreFailure.DecryptionFailure
import com.wire.kalium.logic.feature.backup.RestoreBackupResult.BackupRestoreFailure.IncompatibleBackup
import com.wire.kalium.logic.feature.backup.RestoreBackupResult.BackupRestoreFailure.InvalidUserId
import com.wire.kalium.logic.feature.backup.RestoreBackupResult.Failure
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.fold
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
     * @param extractedBackupRootPath The absolute file system path to the backup file.
     * @param password the password used to encrypt the original backup file. Null if the file was not encrypted.
     * @return A [RestoreBackupResult] indicating the success or failure of the operation.
     */
    suspend operator fun invoke(extractedBackupRootPath: Path, password: String?): RestoreBackupResult
}

internal class RestoreBackupUseCaseImpl(
    private val databaseImporter: DatabaseImporter,
    private val kaliumFileSystem: KaliumFileSystem,
    private val userId: UserId,
    private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl,
    private val idMapper: IdMapper = MapperProvider.idMapper()
) : RestoreBackupUseCase {

    override suspend operator fun invoke(extractedBackupRootPath: Path, password: String?): RestoreBackupResult =
        withContext(dispatchers.io) {
            runSanityChecks(extractedBackupRootPath, password).fold({ error ->
                return@withContext error
            }, { (encryptedFilePath, isPasswordProtected) ->
                if (isPasswordProtected) {
                    val backupSource = kaliumFileSystem.source(encryptedFilePath!!)
                    val extractedBackupPath = extractedBackupRootPath / BACKUP_UNENCRYPTED_FILE_NAME
                    val backupSink = kaliumFileSystem.sink(extractedBackupPath)
                    val userIdEntity = idMapper.toCryptoModel(userId)
                    val (decodingError, backupSize) = ChaCha20Utils().decryptBackupFile(
                        backupSource,
                        backupSink,
                        BackupCoder.Passphrase(password!!),
                        userIdEntity
                    )

                    if (decodingError != null) {
                        return@withContext mappedDecodingError(decodingError)
                    }

                    if (backupSize > 0) {
                        // On successful decryption, we still need to extract the zip file to do sanity checks and get the database file
                        extractFiles(kaliumFileSystem.source(extractedBackupPath), extractedBackupRootPath).fold({
                            kaliumLogger.e("Failed to extract encrypted backup files")
                            Failure(BackupIOFailure("Failed to extract encrypted backup files"))
                        }, {
                            kaliumFileSystem.delete(extractedBackupPath)
                            getDbPathAndImport(extractedBackupRootPath)
                        })
                    } else {
                        Failure(RestoreBackupResult.BackupRestoreFailure.InvalidPassword)
                    }
                } else {
                    getDbPathAndImport(extractedBackupRootPath)
                }
            })
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
            checkIsValidEncryption(extractedBackupPath, password)
        }

    private suspend fun checkIsValidEncryption(extractedBackupPath: Path, password: String?): Either<Failure, Pair<Path?, Boolean>> {
        val encryptedFilePath = kaliumFileSystem.listDirectories(extractedBackupPath).firstOrNull { it.name.contains(".cc20") }
        val isPasswordProtected = !password.isNullOrEmpty()

        // Check if the backup file is encrypted and if the password is provided
        return when {
            encryptedFilePath != null && !isPasswordProtected -> Either.Left(
                Failure(DecryptionFailure("A non blank password is required to restore a password protected backup"))
            )
            encryptedFilePath == null && isPasswordProtected -> Either.Left(
                Failure(DecryptionFailure("A password was provided but no encrypted backup file was found"))
            )
            else -> Either.Right(encryptedFilePath to isPasswordProtected)
        }
    }

    private suspend fun checkIsValidAuthor(extractedBackupRootPath: Path): Either<Failure, Unit> {
        val isValidBackupAuthor = isValidBackupAuthor(extractedBackupRootPath)
        return if (!isValidBackupAuthor) Either.Left(Failure(InvalidUserId))
        else Either.Right(Unit)
    }

    private fun extractFiles(inputSource: Source, extractedBackupRootPath: Path) =
        extractCompressedFile(inputSource, extractedBackupRootPath, kaliumFileSystem)

    private suspend fun getDbPathAndImport(extractedBackupRootPath: Path): RestoreBackupResult {
        return getBackupDBPath(extractedBackupRootPath)?.let { dbPath ->
            importDBFile(dbPath)
        } ?: Failure(BackupIOFailure("No valid db file found in the backup"))
    }

    private suspend fun importDBFile(userDBPath: Path) = wrapStorageRequest {
        databaseImporter.importFromFile(userDBPath.toString())
    }.fold({ Failure(BackupIOFailure("There was an error when importing the DB")) }, { RestoreBackupResult.Success })

    private suspend fun getBackupDBPath(extractedBackupRootFilesPath: Path): Path? =
        kaliumFileSystem.listDirectories(extractedBackupRootFilesPath).firstOrNull { it.name.contains(".db") }

    private suspend fun isValidBackupAuthor(extractedBackupPath: Path): Boolean =
        kaliumFileSystem.listDirectories(extractedBackupPath).firstOrNull {
            it.name.contains(BackupConstants.BACKUP_METADATA_FILE_NAME)
        }?.let { metadataFile ->
            kaliumFileSystem.source(metadataFile).buffer().use {
                Json.decodeFromString<BackupMetadata>(it.readUtf8()).userId == userId.toString()
            }
        } ?: false
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
