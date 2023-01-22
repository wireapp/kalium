package com.wire.kalium.persistence.backup

import app.cash.sqldelight.db.SqlDriver
import com.wire.kalium.persistence.UserDatabase
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.db.PlatformDatabaseData
import com.wire.kalium.persistence.kaliumLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import net.sqlcipher.database.SQLiteDatabase as SqlCipherDatabase

actual class PlatformDatabaseExporter internal actual constructor(
    private val platformDBData: PlatformDatabaseData,
    userId: UserIDEntity?,
    private val sqlDriver: SqlDriver,
    private val isLocalDatabaseEncrypted: Boolean
) : DatabaseExporter, ExporterExtensions by ExporterExtensionsImpl() {

    private val appContext get() = platformDBData.context
    private val backupDBName = backupDatabaseName(userId)

    /**
     * Create a new plain database to be used for
     */
    actual override suspend fun backupToPlainText(): String? = if (isLocalDatabaseEncrypted) {
        backupToPlainTextFromEncrypted()
    } else {
        backupToPlainTextFromUnencrypted()
    }

    private suspend fun backupToPlainTextFromEncrypted(): String? {
        // Delete the old database if it exists, otherwise the new database will be attached to the old one
        if (!deleteDBIfExists(backupDBName)) {
            kaliumLogger.e("Failed to delete old backup database")
            return null
        }
        val dbFile: File = appContext.getDatabasePath(backupDBName)

        val db = SqlCipherDatabase.openOrCreateDatabase(dbFile, "123", null)
        if (db == null) {
            kaliumLogger.e("Could not create backup database")
            return null
        }
        db.close()

        withContext(Dispatchers.IO) {
            UserDatabase.Schema.create(sqlDriver)
            sqlDriver.execute(null, """ATTACH ? AS plain_text  KEY '123';""", 1) {
                bindString(0, dbFile.absolutePath)
            }
            sqlDriver.executeQuery(null, """SELECT sqlcipher_export('plain_text');""", { }, 0)
            sqlDriver.execute(null, """DETACH DATABASE plain_text;""", 0)
        }
        return dbFile.absolutePath
    }

    private fun backupToPlainTextFromUnencrypted(): String = appContext.getDatabasePath(backupDBName).absolutePath

    private fun deleteDBIfExists(dbFileName: String): Boolean =
        if (appContext.getDatabasePath(dbFileName).exists()) {
            appContext.deleteDatabase(dbFileName)
        } else {
            true
        }

    // in case of unencrypted db, we don't need to delete the backup db since it is the same file as the local db
    actual override fun deleteBackupDB(): Boolean? = if (isLocalDatabaseEncrypted) {
        deleteDBIfExists(backupDBName)
    } else {
        true
    }
}
