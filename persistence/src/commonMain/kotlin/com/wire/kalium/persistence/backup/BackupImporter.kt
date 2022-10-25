package com.wire.kalium.persistence.backup

import app.cash.sqldelight.db.SqlDriver

interface BackupImporter {
    suspend fun importFromFile(filePath: String)
}

class BackupImporterImpl(private val sqlDriver: SqlDriver) : BackupImporter {

    // TODO: Emit steps to display progress as backup is imported
    override suspend fun importFromFile(filePath: String) {
        sqlDriver.execute("""BEGIN""")
        sqlDriver.execute("""ATTACH '/Users/Mateusz/AndroidStudioProjects/kalium/persistence/src/commonTest/kotlin/com/wire/kalium/persistence/main.db' AS backupDb""")
        sqlDriver.execute("""INSERT OR IGNORE INTO Conversation SELECT * FROM backupDb.conversation""")
//         migrateTable("Team")
//         migrateTable("User")
//         migrateTable("SelfUser")
//         migrateTable("Metadata")
     //   migrateTable("Conversation")
//         migrateTable("Connection")
//         migrateTable("Member")
//         migrateTable("Client")
//         migrateTable("Message")
//         migrateTable("Asset")
//         migrateTable("Call")
//         migrateTable("MessageAssetContent")
//         migrateTable("MessageConversationChangedContent")
//         migrateTable("MessageFailedToDecryptContent")
//         migrateTable("MessageMemberChangeContent")
//         migrateTable("MessageMention")
//         migrateTable("MessageMissedCallContent")
//         migrateTable("MessageRestrictedAssetContent")
//         migrateTable("MessageTextContent")
//         migrateTable("MessageUnknownContent")
//         migrateTable("Reaction")

        sqlDriver.execute("""DETACH $BACKUP_DB_ALIAS""")
    }

    private fun migrateTable(tableName: String) {
        sqlDriver.execute("""INSERT OR IGNORE INTO $tableName SELECT * FROM backupDb.conversation""")
    }

    private fun SqlDriver.execute(command: String) = execute(null, command, 0)

    companion object {
        const val BACKUP_DB_ALIAS = "backupDb"
    }
}
