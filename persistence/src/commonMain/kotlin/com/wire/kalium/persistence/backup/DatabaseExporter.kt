package com.wire.kalium.persistence.backup

import app.cash.sqldelight.db.SqlDriver

interface DatabaseExporter {
    fun beforeBackup()
}

internal class DatabaseExporterImpl internal constructor(
    private val sqlDriver: SqlDriver
) : DatabaseExporter {

    /*
    https://www.sqlite.org/c3ref/c_checkpoint_full.html

    #define SQLITE_CHECKPOINT_PASSIVE  0  /* Do as much as possible w/o blocking */
    #define SQLITE_CHECKPOINT_FULL     1  /* Wait for writers, then checkpoint */
    #define SQLITE_CHECKPOINT_RESTART  2  /* Like FULL but wait for readers */
    #define SQLITE_CHECKPOINT_TRUNCATE 3  /* Like RESTART but also truncate WAL */
     */
    override fun beforeBackup() {
        sqlDriver.executeQuery(null, "PRAGMA wal_checkpoint(3)", {}, 0)
    }

}
