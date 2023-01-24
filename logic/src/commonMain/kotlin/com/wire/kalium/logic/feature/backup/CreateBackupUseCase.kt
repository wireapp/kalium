package com.wire.kalium.logic.feature.backup

import com.wire.kalium.cryptography.backup.BackupCoder
import com.wire.kalium.cryptography.backup.Passphrase
import com.wire.kalium.cryptography.utils.ChaCha20Encryptor.encryptBackupFile
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.clientPlatform
import com.wire.kalium.logic.data.asset.KaliumFileSystem
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.feature.backup.BackupConstants.BACKUP_ENCRYPTED_FILE_NAME
import com.wire.kalium.logic.feature.backup.BackupConstants.BACKUP_FILE_NAME
import com.wire.kalium.logic.feature.backup.BackupConstants.BACKUP_METADATA_FILE_NAME
import com.wire.kalium.logic.feature.backup.BackupConstants.BACKUP_USER_DB_NAME
import com.wire.kalium.logic.feature.client.ObserveCurrentClientIdUseCase
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.util.createCompressedFile
import com.wire.kalium.persistence.backup.DatabaseExporter
import com.wire.kalium.persistence.db.UserDBSecret
import com.wire.kalium.util.DateTimeUtil
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import io.ktor.util.encodeBase64
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okio.FileNotFoundException
import okio.Path
import okio.Path.Companion.toPath
import okio.Sink
import okio.Source
import okio.buffer
import okio.use

interface CreateBackupUseCase {
    /**
     * Creates a compressed backup file, containing a metadata json file and the current state of the database. This file can be encrypted
     * with the provided password if it is not empty. Otherwise, the file will be unencrypted.
     * @param password The password to encrypt the backup file with. If empty, the file will be unencrypted.
     */
    suspend operator fun invoke(password: String): CreateBackupResult
}

@Suppress("LongParameterList")
internal class CreateBackupUseCaseImpl(
    private val userId: UserId,
    private val getCurrentClientId: ObserveCurrentClientIdUseCase,
    private val kaliumFileSystem: KaliumFileSystem,
    private val userDBSecret: UserDBSecret,
    private val isUserDBSQLCipher: Boolean,
    private val databaseExporter: DatabaseExporter,
    private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl,
    private val idMapper: IdMapper = MapperProvider.idMapper(),
) : CreateBackupUseCase {

    override suspend operator fun invoke(password: String): CreateBackupResult = withContext(dispatchers.default) {
        databaseExporter.beforeBackup()
        val backupFilePath = kaliumFileSystem.tempFilePath(BACKUP_FILE_NAME)
        deletePreviousBackupFiles(backupFilePath)

        createBackupFile(userId, backupFilePath).fold(
            { error -> CreateBackupResult.Failure(error) },
            { (backupFilePath, backupSize) ->
                val isBackupEncrypted = password.isNotEmpty()
                if (isBackupEncrypted) {
                    encryptAndCompressFile(backupFilePath, password)
                } else CreateBackupResult.Success(backupFilePath, backupSize, backupFilePath.name)
            })
    }

    private suspend fun encryptAndCompressFile(backupFilePath: Path, password: String): CreateBackupResult {
        val encryptedBackupFilePath = kaliumFileSystem.tempFilePath(BACKUP_ENCRYPTED_FILE_NAME)
        val backupEncryptedDataSize = encryptBackup(
            kaliumFileSystem.source(backupFilePath),
            kaliumFileSystem.sink(encryptedBackupFilePath),
            Passphrase(password)
        )
        if (backupEncryptedDataSize == 0L)
            return CreateBackupResult.Failure(StorageFailure.Generic(RuntimeException("Failed to encrypt backup file")))

        val finalBackupFilePath = "$encryptedBackupFilePath.zip".toPath()

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
        kaliumFileSystem.delete(kaliumFileSystem.tempFilePath(BACKUP_METADATA_FILE_NAME))
    }

    private suspend fun encryptBackup(backupFileSource: Source, encryptedBackupSink: Sink, passphrase: Passphrase) =
        encryptBackupFile(backupFileSource, encryptedBackupSink, idMapper.toCryptoModel(userId), passphrase)

    private suspend fun createMetadataFile(userId: UserId, userDBSecret: UserDBSecret): Path {
        val clientId = getCurrentClientId().first()
        val creationTime = DateTimeUtil.currentIsoDateTimeString()
        val metadataJson = BackupMetadata(
            clientPlatform,
            BackupCoder.version,
            userId.toString(),
            creationTime,
            clientId.toString(),
            userDBSecret.value.encodeBase64(),
            isUserDBSQLCipher,
        ).toString()

        val metadataFilePath = kaliumFileSystem.tempFilePath(BACKUP_METADATA_FILE_NAME)
        kaliumFileSystem.sink(metadataFilePath).buffer().use {
            it.write(metadataJson.encodeToByteArray())
        }
        return metadataFilePath
    }

    private suspend fun createBackupFile(userId: UserId, backupFilePath: Path): Either<CoreFailure, Pair<Path, Long>> {
        return try {
            val backupSink = kaliumFileSystem.sink(backupFilePath)
            val backupMetadataPath = createMetadataFile(userId, userDBSecret)
            val userDBData = getUserDbDataPath()
            val filesList = listOf(
                kaliumFileSystem.source(backupMetadataPath) to BACKUP_METADATA_FILE_NAME,
                kaliumFileSystem.source(userDBData) to BACKUP_USER_DB_NAME
            )

            createCompressedFile(filesList, backupSink).flatMap { compressedFileSize ->
                Either.Right(backupFilePath to compressedFileSize)
            }
        } catch (e: FileNotFoundException) {
            kaliumLogger.e("There was an error when fetching the user db data path", e)
            Either.Left(StorageFailure.DataNotFound)
        }
    }

    private fun getUserDbDataPath(): Path = kaliumFileSystem.rootDBPath
}

sealed class CreateBackupResult {
    data class Failure(val coreFailure: CoreFailure) : CreateBackupResult()
    data class Success(val backupFilePath: Path, val backupFileSize: Long, val backupFileName: String) : CreateBackupResult()
}
