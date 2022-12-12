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
import com.wire.kalium.logic.util.SecurityHelper
import com.wire.kalium.logic.util.createCompressedFile
import com.wire.kalium.persistence.dbPassphrase.PassphraseStorage
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
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

internal class CreateBackupUseCaseImpl(
    private val userId: UserId,
    private val getCurrentClientId: ObserveCurrentClientIdUseCase,
    private val kaliumFileSystem: KaliumFileSystem,
    private val passphraseStorage: PassphraseStorage,
    private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl,
    private val idMapper: IdMapper = MapperProvider.idMapper(),
) : CreateBackupUseCase {

    override suspend operator fun invoke(password: String): CreateBackupResult = withContext(dispatchers.io) {
        val backupFilePath = kaliumFileSystem.tempFilePath(BACKUP_FILE_NAME)
        // Delete any previously existing temporary backup file
        if (kaliumFileSystem.exists(backupFilePath))
            kaliumFileSystem.delete(backupFilePath)
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

    private fun deleteTempFiles(backupFilePath: Path, encryptedBackupFilePath: Path) {
        kaliumFileSystem.delete(backupFilePath)
        kaliumFileSystem.delete(encryptedBackupFilePath)
        kaliumFileSystem.delete(kaliumFileSystem.tempFilePath(BACKUP_METADATA_FILE_NAME))
    }

    private suspend fun encryptBackup(backupFileSource: Source, encryptedBackupSink: Sink, passphrase: Passphrase) =
        encryptBackupFile(backupFileSource, encryptedBackupSink, idMapper.toCryptoModel(userId), passphrase)

    private suspend fun createMetadataFile(userId: UserId, passphraseStorage: PassphraseStorage): Path {
        val USER_DB_PASSPHRASE_PREFIX = "user_db_secret_alias"
        val userDBAlias = "${USER_DB_PASSPHRASE_PREFIX}_$userId"
        val userDBPassphrase = passphraseStorage.getPassphrase(userDBAlias) ?: ""
        val clientId = getCurrentClientId().first()
        val creationTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).toString()
        val metadataJson =
            BackupMetadata(clientPlatform, BackupCoder.version, userId.toString(), creationTime, clientId.toString(), userDBPassphrase)
                .toString()
        val metadataFilePath = kaliumFileSystem.tempFilePath(BACKUP_METADATA_FILE_NAME)
        kaliumFileSystem.sink(metadataFilePath).buffer().use {
            it.write(metadataJson.encodeToByteArray())
        }
        return metadataFilePath
    }

    private suspend fun createBackupFile(userId: UserId, backupFilePath: Path): Either<CoreFailure, Pair<Path, Long>> {
        val backupSink = kaliumFileSystem.sink(backupFilePath)
        val backupMetadataPath = createMetadataFile(userId, passphraseStorage)
        val userDBData = getUserDbDataPath()

        return createCompressedFile(
            listOf(
                kaliumFileSystem.source(backupMetadataPath) to BACKUP_METADATA_FILE_NAME,
                kaliumFileSystem.source(userDBData) to BACKUP_USER_DB_NAME
            ), backupSink
        ).flatMap { compressedFileSize ->
            Either.Right(backupFilePath to compressedFileSize)
        }
    }

    private fun getUserDbDataPath(): Path = kaliumFileSystem.rootDBPath
}

sealed class CreateBackupResult {
    data class Failure(val coreFailure: CoreFailure) : CreateBackupResult()
    data class Success(val backupFilePath: Path, val backupFileSize: Long, val backupFileName: String) : CreateBackupResult()
}
