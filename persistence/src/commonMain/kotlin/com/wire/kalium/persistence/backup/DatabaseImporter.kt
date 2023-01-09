package com.wire.kalium.persistence.backup

import app.cash.sqldelight.db.SqlDriver
import com.wire.kalium.persistence.db.UserDBSecret

interface DatabaseImporter {
    suspend fun importFromFile(filePath: String, fromOtherClient: Boolean, userDBSecret: UserDBSecret?)
}

class DatabaseImporterImpl(private val sqlDriver: SqlDriver) : DatabaseImporter {

    override suspend fun importFromFile(filePath: String, fromOtherClient: Boolean, userDBSecret: UserDBSecret?) {
        val isDBSQLCiphered = userDBSecret != null && userDBSecret.value.isNotEmpty()
        if (isDBSQLCiphered) {
            sqlDriver.execute(null, """ATTACH ? AS $BACKUP_DB_ALIAS KEY ?""", 2) {
                bindString(0, filePath)
                bindBytes(1, userDBSecret!!.value)
            }
        } else {
            sqlDriver.execute(null, """ATTACH ? AS $BACKUP_DB_ALIAS""", 1) {
                bindString(0, filePath)
            }
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
        if (!fromOtherClient) restoreTable("Call")
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
            """UPDATE $BACKUP_DB_ALIAS.MessageAssetContent 
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
