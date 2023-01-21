package com.wire.kalium.persistence.backup

import app.cash.sqldelight.db.SqlDriver
import com.wire.kalium.persistence.db.UserDBSecret
import kotlinx.datetime.Clock

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
        restoreConversations()
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

    private fun restoreConversations() {
        // For some strange reason, when restoring the backup db, all last_read_date fields from backup db are hardcoded to 0. Therefore,
        // before restoring the conversations, we need to set the lastReadTimestamp of the backup conversations to the latest timestamp of
        // the current conversations so that they don't show up as unread.
        sqlDriver.execute(
            """UPDATE $BACKUP_DB_ALIAS.Conversation
            |SET last_read_date = (
            |SELECT Conversation.last_read_date
            |FROM Conversation
            |WHERE Conversation.qualified_id = $BACKUP_DB_ALIAS.Conversation.qualified_id
            |AND Conversation.last_read_date > $BACKUP_DB_ALIAS.Conversation.last_read_date
            |)
            |WHERE EXISTS (
            |SELECT 1
            |FROM Conversation
            |WHERE Conversation.qualified_id = $BACKUP_DB_ALIAS.Conversation.qualified_id
            |AND Conversation.last_read_date > $BACKUP_DB_ALIAS.Conversation.last_read_date
            |);
            |INSERT OR IGNORE INTO Conversation SELECT * FROM $BACKUP_DB_ALIAS.Conversation;
            """.trimMargin()
        )
    }

    private fun restoreTable(tableName: String) {
        sqlDriver.execute("""INSERT OR IGNORE INTO $tableName SELECT * FROM $BACKUP_DB_ALIAS.$tableName""")
    }

    private fun SqlDriver.execute(command: String) = execute(null, command, 0)

    companion object {
        const val BACKUP_DB_ALIAS = "backupDb"
    }
}
