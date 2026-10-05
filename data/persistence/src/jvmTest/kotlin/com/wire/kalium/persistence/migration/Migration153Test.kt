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
import kotlin.test.assertTrue

class Migration153Test {
    @Test
    fun existingMessagesSurviveMigrationAndIdentityFollowsEditsUsingIndexedForeignKey() = runTest {
        val file = File.createTempFile("notification-identity-migration-", ".db")
        File("src/commonTest/kotlin/com/wire/kalium/persistence/schemas/124.db").copyTo(file, overwrite = true)
        val driver = JdbcSqliteDriver("jdbc:sqlite:${file.absolutePath}", SQLiteConfig().apply { enforceForeignKeys(true) }.toProperties())
        try {
            object : SuspendingTransacterImpl(driver) {}.transaction {
                UserDatabase.Schema.awaitMigrate(driver, 124, 153)
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
            object : SuspendingTransacterImpl(driver) {}.transaction {
                UserDatabase.Schema.awaitMigrate(driver, 153, 154)
            }
            assertEquals(listOf("original"), driver.strings("SELECT id FROM Message"))
            assertEquals(emptyList(), driver.strings("SELECT notification_message_id FROM MessageNotificationIdentity"))
            driver.execute(null, """
                INSERT INTO MessageNotificationIdentity VALUES ('original','conversation@domain','original')
            """.trimIndent(), 0)
            driver.execute(null, "UPDATE Message SET id='edited' WHERE id='original'", 0)
            assertEquals(listOf("edited"), driver.strings("SELECT current_message_id FROM MessageNotificationIdentity"))
            assertEquals(listOf("original"), driver.strings("SELECT notification_message_id FROM MessageNotificationIdentity"))
            val plan = driver.strings("""
                EXPLAIN QUERY PLAN SELECT rowid FROM MessageNotificationIdentity
                WHERE current_message_id='edited' AND conversation_id='conversation@domain'
            """.trimIndent(), column = 3)
            assertTrue(plan.any { it.contains("message_notification_identity_current") && it.contains("SEARCH") })
            driver.execute(null, "DELETE FROM Message WHERE id='edited'", 0)
            assertEquals(emptyList(), driver.strings("SELECT notification_message_id FROM MessageNotificationIdentity"))
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
