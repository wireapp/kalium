package com.wire.kalium.logic.feature.backup

object BackupConstants {
    const val BACKUP_FILE_NAME = "user-backup.zip"
    const val BACKUP_ENCRYPTED_FILE_NAME = "user-backup.cc20"
    const val BACKUP_USER_DB_NAME = "user-backup-database.db"
    const val BACKUP_METADATA_FILE_NAME = "export.json"
    const val BACKUP_ENCRYPTED_EXTENSION = "cc20"
    const val BACKUP_DB_EXTENSION = "db"
    const val BACKUP_METADATA_EXTENSION = "json"

    val ACCEPTED_EXTENSIONS = listOf(
        BACKUP_ENCRYPTED_EXTENSION,
        BACKUP_DB_EXTENSION,
        BACKUP_METADATA_EXTENSION
    )
}
