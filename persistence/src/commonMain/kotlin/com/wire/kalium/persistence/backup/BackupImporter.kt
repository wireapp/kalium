package com.wire.kalium.persistence.backup

import app.cash.sqldelight.db.SqlDriver

interface BackupImporter {
    suspend fun importFromFile(filePath: String)
}

class BackupImporterImpl(private val sqlDriver: SqlDriver) : BackupImporter {

    override suspend fun importFromFile(filePath: String) {
        sqlDriver.execute( """BEGIN""")
        sqlDriver.execute(null, """ATTACH ? AS $BACKUP_DB_ALIAS""", 1) {
            bindString(1, filePath)
        }

        migrateTable("Conversation")
        migrateTable("User")
        migrateTable("Member")
        migrateTable("Message")
        migrateTable("MessageTextContent")

        sqlDriver.execute("""DETACH $BACKUP_DB_ALIAS""")
        sqlDriver.execute( """COMMIT""")
    }

    private fun migrateTable(tableName: String) {
        sqlDriver.execute("""INSERT INTO $tableName SELECT * FROM $BACKUP_DB_ALIAS.$tableName""")
    }

    private fun SqlDriver.execute(command: String) = execute(null, command, 0)

    companion object {
        const val BACKUP_DB_ALIAS = "backupDb"
    }
}
