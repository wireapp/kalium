package com.wire.kalium.persistence.backup

import app.cash.sqldelight.db.SqlDriver
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.db.PlatformDatabaseData
import com.wire.kalium.persistence.util.FileNameUtil

expect class PlatformDatabaseExporter internal constructor(
    platformDBData: PlatformDatabaseData,
    userId: UserIDEntity?,
    sqlDriver: SqlDriver,
    isLocalDatabaseEncrypted: Boolean
): DatabaseExporter {
    override fun backupToPlainText(): String?
    override fun deleteBackupDB(): Boolean?
}

internal interface ExporterExtensions {
    fun backupDatabaseName(userId: UserIDEntity?): String
}

internal class ExporterExtensionsImpl: ExporterExtensions {
    override fun backupDatabaseName(userId: UserIDEntity?): String = (userId?.let {
        FileNameUtil.userDBName(it)
    } ?: FileNameUtil.globalDBName()).let {
        "backup-$it"
    }
}

interface DatabaseExporter {
    fun backupToPlainText(): String?
    fun deleteBackupDB(): Boolean?
}
