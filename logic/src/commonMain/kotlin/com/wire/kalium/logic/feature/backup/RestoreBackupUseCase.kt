package com.wire.kalium.logic.feature.backup

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.wrapStorageRequest
import com.wire.kalium.persistence.backup.BackupImporter
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.withContext

interface RestoreBackupUseCase {

    /**
     * Restores a valid previously created backup file into the current database, respecting the current data if there is any overlap.
     * @param backUpFilePath The absolute file system path to the backup file
     */
    suspend operator fun invoke(backupFilePath: String): RestoreBackupResult
}

internal class RestoreBackupUseCaseImpl(
    private val backupImporter: BackupImporter,
    private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl
) : RestoreBackupUseCase {

    override suspend operator fun invoke(backupFilePath: String): RestoreBackupResult {
        return withContext(dispatchers.io) {
            wrapStorageRequest {
                backupImporter.importFromFile(backupFilePath)
            }.fold({ RestoreBackupResult.Failure(it) }, { RestoreBackupResult.Success })
        }
    }

}

sealed class RestoreBackupResult {
    data class Failure(val storageFailure: StorageFailure) : RestoreBackupResult()
    object Success : RestoreBackupResult()
}
