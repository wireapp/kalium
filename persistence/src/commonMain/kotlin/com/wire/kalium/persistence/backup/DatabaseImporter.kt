/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

package com.wire.kalium.persistence.backup

import app.cash.sqldelight.db.SqlDriver
import com.wire.kalium.persistence.db.UserDBSecret

interface DatabaseImporter {
    suspend fun importFromFile(filePath: String, fromOtherClient: Boolean, userDBSecret: UserDBSecret?)
}

class DatabaseImporterImpl(private val sqlDriver: SqlDriver) : DatabaseImporter {

    override suspend fun importFromFile(filePath: String, fromOtherClient: Boolean, userDBSecret: UserDBSecret?) {
        val isDBSQLCiphered = userDBSecret != null && userDBSecret.value.isNotEmpty()

        sqlDriver.execute("""BEGIN""")

        // BackupDB will be detached automatically when committing the transaction
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

        restoreTable("User")
        restoreConversations()
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
        // Before restoring any conversations, we need to set the last_read_date of the backup conversations to the last_read_date of the
        // current conversations if it is more recent that what the backup states

        /*
        Parsing Ambiguity
        When the INSERT statement to which the UPSERT is attached takes its values from a SELECT statement,
        there is a potential parsing ambiguity. The parser might not be able to tell if the "ON" keyword is
        introducing the UPSERT or if it is the ON clause of a join. To work around this, the SELECT statement
        should always include a WHERE clause, even if that WHERE clause is just "WHERE true".
        Ambiguous use of ON:
        INSERT INTO t1 SELECT * FROM t2
        ON CONFLICT(x) DO UPDATE SET y=excluded.y;

        Ambiguity resolved using a WHERE clause:
        INSERT INTO t1 SELECT * FROM t2 WHERE true
        ON CONFLICT(x) DO UPDATE SET y=excluded.y;

        https://www.sqlite.org/lang_UPSERT.html
         */

        sqlDriver.execute(
            """INSERT OR IGNORE INTO Conversation
                |SELECT * FROM $BACKUP_DB_ALIAS.Conversation WHERE true
                |ON CONFLICT(qualified_id) DO UPDATE SET
                |last_read_date = 
                |CASE WHEN last_read_date > excluded.last_read_date THEN last_read_date ELSE excluded.last_read_date END,
                |last_modified_date = 
                |CASE WHEN last_modified_date > excluded.last_modified_date THEN last_modified_date ELSE excluded.last_modified_date END,
                |last_notified_date = 
                |CASE WHEN last_notified_date > excluded.last_notified_date THEN last_notified_date ELSE excluded.last_notified_date END;
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
