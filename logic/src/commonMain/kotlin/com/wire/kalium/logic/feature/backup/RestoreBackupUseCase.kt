package com.wire.kalium.logic.feature.backup

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.wrapStorageRequest
import com.wire.kalium.persistence.backup.BackupImporter


interface RestoreBackupUseCase {

    /**
     * Restores a valid previously created backup file into the current database, respecting the current data if there is any overlap.
     * @param backUpFilePath The absolute file system path to the backup file
     */
    suspend operator fun invoke(backupFilePath: String): RestoreBackupResult
}

// TODO: User IO Dispatcher
internal class RestoreBackupUseCaseImpl(
    private val backupImporter: BackupImporter
) : RestoreBackupUseCase {

    override suspend operator fun invoke(backupFilePath: String): RestoreBackupResult {
        return wrapStorageRequest {
            backupImporter.importFromFile(backupFilePath)
        }.fold({ RestoreBackupResult.Failure(it) }, { RestoreBackupResult.Success })
    }

}

sealed class RestoreBackupResult {
    data class Failure(val storageFailure: StorageFailure) : RestoreBackupResult()
    object Success : RestoreBackupResult()
}
