package com.wire.kalium.logic.feature.backup

import com.wire.kalium.persistence.backup.BackupImporter

class ImportBackupUseCase(
    private val backupImporter: BackupImporter
    // TODO: User IO Dispatcher
    ) {

    suspend operator fun invoke(backupFilePath: String) {
        backupImporter.importFromFile(backupFilePath)
    }
}
