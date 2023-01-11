package com.wire.kalium.persistence.backup

import app.cash.sqldelight.db.SqlDriver
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.db.PlatformDatabaseData

expect class DatabaseExporter internal constructor(
    platformDBData: PlatformDatabaseData,
    userId: UserIDEntity?,
    sqlDriver: SqlDriver
) {
    /**
     * Create a new plain database to be used for
     */
    fun backupToPlainText(): String

    fun deleteBackupDB(): Boolean?
}
