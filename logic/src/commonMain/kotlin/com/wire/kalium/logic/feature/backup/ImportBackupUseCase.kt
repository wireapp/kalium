package com.wire.kalium.logic.feature.backup

import com.wire.kalium.persistence.backup.BackupImporter

// TODO: User IO Dispatcher
class ImportBackupUseCase(
    private val backupImporter: BackupImporter
) {

    suspend operator fun invoke(backupFilePath: String) {
        backupImporter.importFromFile(backupFilePath)
    }
}
