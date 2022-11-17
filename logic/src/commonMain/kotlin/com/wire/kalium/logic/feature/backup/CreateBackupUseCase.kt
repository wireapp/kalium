package com.wire.kalium.logic.feature.backup

import com.wire.kalium.cryptography.backup.BackupCoder
import com.wire.kalium.cryptography.utils.ChaCha20Utils
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.asset.KaliumFileSystem
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.feature.client.ObserveCurrentClientIdUseCase
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.util.clientPlatform
import com.wire.kalium.logic.util.createCompressedFile
import com.wire.kalium.persistence.util.FileNameUtil
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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
    private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl,
) : CreateBackupUseCase {

    val idMapper = MapperProvider.idMapper()

    override suspend operator fun invoke(password: String): CreateBackupResult = withContext(dispatchers.io) {
        createBackupFile(userId).fold(
            { error -> CreateBackupResult.Failure(error) },
            { backupFilePath ->
                val isBackupEncrypted = password.isNotEmpty()

                if (isBackupEncrypted) {
                    val encryptedBackupFilePath = kaliumFileSystem.tempFilePath(ENCRYPTED_BACKUP_FILE_NAME)
                    val encryptedDataSize = encryptBackup(
                        kaliumFileSystem.source(backupFilePath),
                        kaliumFileSystem.sink(encryptedBackupFilePath),
                        BackupCoder.Passphrase(password)
                    )
                    val finalEncryptedFileName = "$encryptedBackupFilePath.zip".toPath()

                    createCompressedFile(
                        listOf(kaliumFileSystem.source(encryptedBackupFilePath) to finalEncryptedFileName.name),
                        kaliumFileSystem.sink(finalEncryptedFileName)
                    )

                    if (encryptedDataSize > 0) {
                        CreateBackupResult.Success(encryptedBackupFilePath)
                    } else {
                        CreateBackupResult.Failure(StorageFailure.Generic(RuntimeException("Failed to encrypt backup file")))
                    }
                } else CreateBackupResult.Success(backupFilePath)
            })
    }

    private suspend fun encryptBackup(backupFileSource: Source, encryptedBackupSink: Sink, passphrase: BackupCoder.Passphrase) =
        ChaCha20Utils().encryptBackupFile(backupFileSource, encryptedBackupSink, idMapper.toCryptoModel(userId), passphrase)


    private suspend fun createMetadataFile(userId: UserId): Path {
        val clientId = getCurrentClientId.invoke().first()
        val metadataJson = JsonObject(
            mapOf(
                "platform" to JsonPrimitive(clientPlatform),
                "version" to JsonPrimitive(BackupCoder.version),
                "user_id" to JsonPrimitive(userId.toString()),
                "creation_time" to JsonPrimitive(Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).toString()),
                "client_id" to JsonPrimitive(clientId?.value)
            )
        ).toString()
        val metadataFilePath = kaliumFileSystem.tempFilePath(METADATA_FILE_NAME)
        kaliumFileSystem.sink(metadataFilePath).buffer().use {
            it.write(metadataJson.encodeToByteArray())
        }
        return metadataFilePath
    }

    private suspend fun createBackupFile(userId: UserId): Either<CoreFailure, Path> {
        val backupFilePath = kaliumFileSystem.tempFilePath(UNENCRYPTED_BACKUP_FILE_NAME)
        val backupSink = kaliumFileSystem.sink(backupFilePath)
        val backupMetadataPath = createMetadataFile(userId)
        val userDBData = getUserDbDataPath()

        return createCompressedFile(
            listOf(
                kaliumFileSystem.source(backupMetadataPath) to METADATA_FILE_NAME,
                kaliumFileSystem.source(userDBData) to USER_BACKUP_DB_NAME
            ), backupSink
        ).flatMap {
            Either.Right(backupFilePath)
        }
    }

    private fun getUserDbDataPath(): Path = kaliumFileSystem.rootDBPath


    private companion object {
        private const val UNENCRYPTED_BACKUP_FILE_NAME = "user-backup.zip"
        private const val ENCRYPTED_BACKUP_FILE_NAME = "encrypted-user-backup.cc20"
        private const val METADATA_FILE_NAME = "export.json"
        private const val USER_BACKUP_DB_NAME = "user-backup-database.db"
    }
}

sealed class CreateBackupResult {
    data class Failure(val coreFailure: CoreFailure) : CreateBackupResult()
    data class Success(val backupFilePath: Path) : CreateBackupResult()
}
