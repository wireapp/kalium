package com.wire.kalium.logic.feature.backup

import com.wire.kalium.cryptography.backup.BackupCoder
import com.wire.kalium.cryptography.utils.ChaCha20Utils
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.asset.KaliumFileSystem
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.feature.backup.BackupConstants.BACKUP_UNENCRYPTED_FILE_NAME
import com.wire.kalium.logic.feature.backup.RestoreBackupResult.BackupRestoreFailure.BackupIOFailure
import com.wire.kalium.logic.feature.backup.RestoreBackupResult.BackupRestoreFailure.DecryptionFailure
import com.wire.kalium.logic.feature.backup.RestoreBackupResult.BackupRestoreFailure.InvalidUserId
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
) : RestoreBackupUseCase {

    override suspend operator fun invoke(extractedBackupRootPath: Path, password: String?): RestoreBackupResult =
        withContext(dispatchers.io) {
            val idMapper = MapperProvider.idMapper()
            val encryptedFilePath = kaliumFileSystem.list(extractedBackupRootPath).firstOrNull { it.name.contains(".cc20") }
            val isPasswordProtected = !password.isNullOrEmpty()

            // Check if the backup file is encrypted and if the password is provided
            if (encryptedFilePath != null && !isPasswordProtected) return@withContext RestoreBackupResult.Failure(
                DecryptionFailure("A non blank password is required to restore a password protected backup")
            )
            if (encryptedFilePath == null && isPasswordProtected) return@withContext RestoreBackupResult.Failure(
                DecryptionFailure("A password was provided but no encrypted backup file was found")
            )

            if (isPasswordProtected) {
                val backupSource = kaliumFileSystem.source(encryptedFilePath!!)
                val extractedBackupPath = extractedBackupRootPath / BACKUP_UNENCRYPTED_FILE_NAME
                val backupSink = kaliumFileSystem.sink(extractedBackupPath)
                val userIdEntity = idMapper.toCryptoModel(userId)
                val size = ChaCha20Utils().decryptBackupFile(backupSource, backupSink, BackupCoder.Passphrase(password!!), userIdEntity)
                if (size > 0) {
                    // With successful decryption, we still need to extract the zip file to perform sanity checks and get the database file
                    extractFiles(kaliumFileSystem.source(extractedBackupPath), extractedBackupRootPath).fold({
                        kaliumLogger.e("Failed to extract encrypted backup files")
                        RestoreBackupResult.Failure(BackupIOFailure("Failed to extract encrypted backup files"))
                    }, {
                        kaliumFileSystem.delete(extractedBackupPath)
                        doSanityChecksAndImport(extractedBackupRootPath)
                    })
                } else {
                    RestoreBackupResult.Failure(RestoreBackupResult.BackupRestoreFailure.InvalidPassword)
                }
            } else {
                doSanityChecksAndImport(extractedBackupRootPath)
            }
        }

    private fun extractFiles(inputSource: Source, extractedBackupRootPath: Path) =
        extractCompressedFile(inputSource, extractedBackupRootPath, kaliumFileSystem)

    private suspend fun doSanityChecksAndImport(extractedBackupRootPath: Path): RestoreBackupResult {
        val isBackupAuthorValid = validateBackupAuthor(extractedBackupRootPath)
        if (!isBackupAuthorValid) return RestoreBackupResult.Failure(InvalidUserId)
        return getBackupDBPath(extractedBackupRootPath)?.let { dbPath ->
            importDBFile(dbPath)
        } ?: RestoreBackupResult.Failure(BackupIOFailure("No valid db file found in the backup"))
    }

    private suspend fun importDBFile(userDBPath: Path) = wrapStorageRequest {
        databaseImporter.importFromFile(userDBPath.toString())
    }.fold({ RestoreBackupResult.Failure(BackupIOFailure("There was an error when importing the DB")) }, { RestoreBackupResult.Success })

    private suspend fun getBackupDBPath(extractedBackupRootFilesPath: Path): Path? =
        kaliumFileSystem.list(extractedBackupRootFilesPath).firstOrNull { it.name.contains(".db") }

    private suspend fun validateBackupAuthor(extractedBackupPath: Path): Boolean = kaliumFileSystem.list(extractedBackupPath).firstOrNull {
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

    sealed class BackupRestoreFailure(open val cause: String) : CoreFailure.FeatureFailure() {
        object InvalidPassword : BackupRestoreFailure("The provided password is invalid")
        object InvalidUserId : BackupRestoreFailure("User id in the backup file does not match the current user id")
        data class BackupIOFailure(override val cause: String) : BackupRestoreFailure(cause)
        data class DecryptionFailure(override val cause: String) : BackupRestoreFailure(cause)

    }
}
