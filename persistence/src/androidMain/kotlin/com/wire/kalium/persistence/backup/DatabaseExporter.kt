package com.wire.kalium.persistence.backup

import app.cash.sqldelight.db.SqlDriver
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.db.PlatformDatabaseData
import java.io.File

actual class DatabaseExporter internal actual constructor(
    private val platformDBData: PlatformDatabaseData,
    private val userId: UserIDEntity?,
    private val sqlDriver: SqlDriver
) {

    private val appContext get() = platformDBData.context

    /**
     * Create a new plain database to be used for
     */
    actual fun backupToPlainText(): String {
        val dbFileName = "kalium_backup.db"
        // Delete the old database if it exists, otherwise the new database will be attached to the old one
        deleteDBIfExists(dbFileName)
        val dbFile: File = appContext.getDatabasePath("kalium_backup.db")

        sqlDriver.execute(null, """ATTACH ? AS plain_text KEY '';""", 1) {
            bindString(0, dbFile.absolutePath)
        }
        sqlDriver.execute(null, """SELECT sqlcipher_export('plain_text');""", 0)
        sqlDriver.execute(null, """DETACH DATABASE plaintext;""", 0)
        return dbFile.absolutePath
    }

    private fun deleteDBIfExists(dbFileName: String): Boolean? =
        if (appContext.getDatabasePath(dbFileName).exists()) {
            appContext.deleteDatabase(dbFileName)
        } else {
            null
        }

    actual fun deleteBackupDB(): Boolean? = deleteDBIfExists("kalium_backup.db")
}
