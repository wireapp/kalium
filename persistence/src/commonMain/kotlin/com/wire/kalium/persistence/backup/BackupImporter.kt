package com.wire.kalium.persistence.backup

import app.cash.sqldelight.db.SqlDriver

interface BackupImporter {
    suspend fun importFromFile(filePath: String)
}

class BackupImporterImpl(private val sqlDriver: SqlDriver) : BackupImporter {

    // TODO: Emit steps to display progress as backup is imported
    override suspend fun importFromFile(filePath: String) {
        sqlDriver.execute(null, """ATTACH ? AS $BACKUP_DB_ALIAS""", 1) {
            bindString(1, filePath)
        }
        sqlDriver.execute("""BEGIN""")

        migrateTable("Team")
        migrateTable("User")
//         migrateTable("SelfUser")
        migrateTable("Metadata")
        migrateTable("Conversation")
        migrateTable("Connection")
        migrateTable("Member")
        migrateTable("Client")
        migrateTable("Message")
        migrateTable("Asset")
        migrateTable("Call")
        migrateTable("MessageAssetContent")
        migrateTable("MessageConversationChangedContent")
        migrateTable("MessageFailedToDecryptContent")
        migrateTable("MessageMemberChangeContent")
        migrateTable("MessageMention")
        migrateTable("MessageMissedCallContent")
        migrateTable("MessageRestrictedAssetContent")
        migrateTable("MessageTextContent")
        migrateTable("MessageUnknownContent")
        migrateTable("Reaction")

        sqlDriver.execute("""COMMIT""")
        sqlDriver.execute("""DETACH $BACKUP_DB_ALIAS""")
    }

    private fun migrateTable(tableName: String) {
        sqlDriver.execute("""INSERT OR IGNORE INTO $tableName SELECT * FROM $BACKUP_DB_ALIAS.$tableName""")
    }

    private fun SqlDriver.execute(command: String) = execute(null, command, 0)

    companion object {
        const val BACKUP_DB_ALIAS = "backupDb"
    }
}
