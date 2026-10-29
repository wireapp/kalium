/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.persistence.migration

import app.cash.sqldelight.SuspendingTransacterImpl
import app.cash.sqldelight.async.coroutines.awaitMigrate
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.wire.kalium.persistence.UserDatabase
import kotlinx.coroutines.test.runTest
import java.io.File
import org.sqlite.SQLiteConfig
import kotlin.test.Test
import kotlin.test.assertEquals

class Migration163Test {
    @Test
    fun cellsAttachmentsBecomeLastMessagesForExistingAndNewConversations() = runTest {
        val file = File.createTempFile("cells-last-message-migration-", ".db")
        File("src/commonTest/kotlin/com/wire/kalium/persistence/schemas/124.db").copyTo(file, overwrite = true)
        val driver = JdbcSqliteDriver("jdbc:sqlite:${file.absolutePath}", SQLiteConfig().apply { enforceForeignKeys(true) }.toProperties())
        try {
            object : SuspendingTransacterImpl(driver) {}.transaction {
                UserDatabase.Schema.awaitMigrate(driver, 124, 163)
            }
            driver.execute(null, "INSERT INTO User(qualified_id) VALUES ('sender@domain')", 0)
            driver.execute(null, """
                INSERT INTO Conversation(qualified_id,type,mls_group_state,protocol,creator_id,last_modified_date,
                    access_list,access_role_list,mls_cipher_suite)
                VALUES ('conversation@domain','GROUP','ESTABLISHED','PROTEUS','sender@domain',100,'[]','[]','UNKNOWN')
            """.trimIndent(), 0)
            driver.execute(null, """
                INSERT INTO Message(id,conversation_id,content_type,creation_date,sender_user_id,status,visibility)
                VALUES ('original','conversation@domain','TEXT',100,'sender@domain','SENT','VISIBLE')
            """.trimIndent(), 0)
            driver.execute(null, """
                INSERT INTO Message(id,conversation_id,content_type,creation_date,sender_user_id,status,visibility)
                VALUES ('cells','conversation@domain','MULTIPART',200,'sender@domain','SENT','VISIBLE')
            """.trimIndent(), 0)
            assertEquals(listOf("original"), driver.strings("SELECT message_id FROM LastMessage"))
            object : SuspendingTransacterImpl(driver) {}.transaction {
                UserDatabase.Schema.awaitMigrate(driver, 163, 164)
            }
            assertEquals(listOf("cells"), driver.strings("SELECT message_id FROM LastMessage"))
            driver.execute(null, """
                INSERT INTO Message(id,conversation_id,content_type,creation_date,sender_user_id,status,visibility)
                VALUES ('new-cells','conversation@domain','MULTIPART',300,'sender@domain','SENT','VISIBLE')
            """.trimIndent(), 0)
            assertEquals(listOf("new-cells"), driver.strings("SELECT message_id FROM LastMessage"))
            driver.execute(null, "DELETE FROM Message WHERE id='new-cells'", 0)
            assertEquals(listOf("cells"), driver.strings("SELECT message_id FROM LastMessage"))
            driver.execute(null, "UPDATE Message SET id='edited-cells' WHERE id='cells'", 0)
            assertEquals(listOf("edited-cells"), driver.strings("SELECT message_id FROM LastMessage"))
            assertEquals(emptyList(), driver.strings("PRAGMA foreign_key_check"))
        } finally {
            driver.close()
            file.delete()
        }
    }

    private fun JdbcSqliteDriver.strings(sql: String, column: Int = 0): List<String> {
        val values = mutableListOf<String>()
        executeQuery(null, sql, { cursor ->
            while (cursor.next().value) values += cursor.getString(column)!!
            QueryResult.Unit
        }, 0)
        return values
    }
}
