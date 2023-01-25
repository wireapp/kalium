package com.wire.kalium.persistence.backup

import app.cash.sqldelight.db.SqlDriver
import com.wire.kalium.persistence.DumpContentQueries
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.db.PlatformDatabaseData
import com.wire.kalium.persistence.db.UserDatabaseBuilder
import com.wire.kalium.persistence.db.nuke
import com.wire.kalium.persistence.db.userDatabaseBuilder
import com.wire.kalium.persistence.util.FileNameUtil
import com.wire.kalium.util.KaliumDispatcherImpl

interface DatabaseExporter {
    // dump the user DB into a plain DB and return the location of the file
    fun exportToPlainDB(): String?

    // deleted the plain DB file
    fun deleteBackupDBFile(): Boolean
}

internal class DatabaseExporterImpl internal constructor(
    uerId: UserIDEntity,
    private val platformDatabaseData: PlatformDatabaseData,
    private val dumpContentQueries: DumpContentQueries,
    private val sqlDriver: SqlDriver
) : DatabaseExporter {

    private val backupUserId = uerId.copy(value = "backup-${uerId.value}")
    private val backupDBName = FileNameUtil.userDBName(backupUserId)

    /*
    https://www.sqlite.org/c3ref/c_checkpoint_full.html

    #define SQLITE_CHECKPOINT_PASSIVE  0  /* Do as much as possible w/o blocking */
    #define SQLITE_CHECKPOINT_FULL     1  /* Wait for writers, then checkpoint */
    #define SQLITE_CHECKPOINT_RESTART  2  /* Like FULL but wait for readers */
    #define SQLITE_CHECKPOINT_TRUNCATE 3  /* Like RESTART but also truncate WAL */
     */
//     override fun beforeBackup() {
//         sqlDriver.executeQuery(null, "PRAGMA wal_checkpoint(3)", {}, 0)
//     }

    override fun exportToPlainDB(): String? {
        // delete the backup DB file if it exists
        if (deleteBackupDBFile()) {
            return null
        }

        // create a new backup DB file
        val plainDatabase: UserDatabaseBuilder =
            userDatabaseBuilder(platformDatabaseData, backupUserId, null, KaliumDispatcherImpl.io, false)

        // copy the data from the user DB to the backup DB
        sqlDriver.execute(null, "ATTACH DATABASE ? AS $PLAIN_DB_ALIAS", 1) {
            bindString(1, plainDatabase.dbFileLocation())
        }

        try {
            plainDatabase.database.transaction {
                dumpContent()
            }
        } finally {
            sqlDriver.execute(null, "DETACH DATABASE $PLAIN_DB_ALIAS", 0)
        }

        return plainDatabase.dbFileLocation()
    }

    override fun deleteBackupDBFile(): Boolean = nuke(backupUserId, platformDatabaseData)

    private fun dumpContent() {
        // dump the content of the user DB into the plain DB must be done in this order
        dumpContentQueries.dumpUserTable()
        dumpContentQueries.dumpConversationTable()
        dumpContentQueries.dumpMessageTable()
        dumpContentQueries.dumpCallTable()
        dumpContentQueries.dumpMessageAssetContentTable()
        dumpContentQueries.dumpMessageRestrictedAssetContentTable()
        dumpContentQueries.dumpMessageMemberChangeContentTable()
        dumpContentQueries.dumpMessageMentionTable()
        dumpContentQueries.dumpMessageMissedCallContentTable()
        dumpContentQueries.dumpMessageTextContentTable()
        dumpContentQueries.dumpMessageUnknownContentTable()
        dumpContentQueries.dumpReactionTable()
    }

    private companion object {
        // THIS MUST MATCH THE PLAIN DATABASE ALIAS IN DumpContent.sq DO NOT CHANGE
        const val PLAIN_DB_ALIAS = "plain_db"
    }
}
