package com.wire.kalium.persistence.backup

import app.cash.sqldelight.db.SqlDriver

interface DatabaseImporter {
    suspend fun importFromFile(filePath: String)
}

class DatabaseImporterImpl(private val sqlDriver: SqlDriver) : DatabaseImporter {

    override suspend fun importFromFile(filePath: String) {
        sqlDriver.execute(null, """ATTACH ? AS $BACKUP_DB_ALIAS""", 1) {
            bindString(1, filePath)
        }
        sqlDriver.execute("""BEGIN""")
        restoreTable("Team")
        restoreTable("User")
        restoreTable("Metadata")
        restoreTable("Conversation")
        restoreTable("Connection")
        restoreTable("Member")
        restoreTable("Client")
        restoreTable("Message")
        restoreTable("Call")
        restoreAssets()
        restoreTable("MessageConversationChangedContent")
        restoreTable("MessageFailedToDecryptContent")
        restoreTable("MessageMemberChangeContent")
        restoreTable("MessageMention")
        restoreTable("MessageMissedCallContent")
        restoreTable("MessageTextContent")
        restoreTable("MessageUnknownContent")
        restoreTable("Reaction")

        sqlDriver.execute("""COMMIT""")
        sqlDriver.execute("""DETACH $BACKUP_DB_ALIAS""")
    }

    private fun restoreAssets() {
        sqlDriver.execute(
            """UPDATE backupDb.MessageAssetContent 
            |SET asset_upload_status = 'NOT_UPLOADED', asset_download_status = 'NOT_DOWNLOADED'""".trimMargin()
        )
        restoreTable("MessageAssetContent")
        restoreTable("MessageRestrictedAssetContent")
    }

    private fun restoreTable(tableName: String) {
        sqlDriver.execute("""INSERT OR IGNORE INTO $tableName SELECT * FROM $BACKUP_DB_ALIAS.$tableName""")
    }

    private fun SqlDriver.execute(command: String) = execute(null, command, 0)

    companion object {
        const val BACKUP_DB_ALIAS = "backupDb"
    }
}
