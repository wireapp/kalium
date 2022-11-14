package com.wire.kalium.logic.feature.backup

import com.wire.kalium.persistence.backup.BackupImporter

// TODO: User IO Dispatcher
/**
 * Imports a valid previously created backup file into the current database, respecting the current data if there is any overlap.
 * @param backUpFilePath The absolute file system path to the backup file
 */
interface ImportBackupUseCase {
    suspend operator fun invoke(backupFilePath: String)
}

internal class ImportBackupUseCaseImpl(
    private val backupImporter: BackupImporter
) : ImportBackupUseCase {

    override suspend operator fun invoke(backupFilePath: String) {
        backupImporter.importFromFile(backupFilePath)
    }
}
