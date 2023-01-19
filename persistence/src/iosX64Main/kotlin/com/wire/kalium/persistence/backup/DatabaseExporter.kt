package com.wire.kalium.persistence.backup

import app.cash.sqldelight.db.SqlDriver
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.db.PlatformDatabaseData

actual class PlatformDatabaseExporter internal actual constructor(
    private val platformDBData: PlatformDatabaseData,
    private val userId: UserIDEntity?,
    private val sqlDriver: SqlDriver,
    isLocalDatabaseEncrypted: Boolean
): DatabaseExporter {
    actual override fun backupToPlainText(): String {
        TODO("Not yet implemented")
    }

    actual override fun deleteBackupDB(): Boolean? {
        TODO("Not yet implemented")
    }
}
