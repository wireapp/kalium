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

class Migration164Test {
    @Test
    fun existingMentionsSurviveAndEveryoneMentionsFollowMessageEditsAndDeletion() = runTest {
        val file = File.createTempFile("everyone-mentions-migration-", ".db")
        File("src/commonTest/kotlin/com/wire/kalium/persistence/schemas/124.db").copyTo(file, overwrite = true)
        val driver = JdbcSqliteDriver("jdbc:sqlite:${file.absolutePath}", SQLiteConfig().apply { enforceForeignKeys(true) }.toProperties())
        try {
            object : SuspendingTransacterImpl(driver) {}.transaction {
                UserDatabase.Schema.awaitMigrate(driver, 124, 164)
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
            driver.execute(null, "INSERT INTO MessageMention VALUES ('original','conversation@domain',5,5,'sender@domain')", 0)
            object : SuspendingTransacterImpl(driver) {}.transaction {
                UserDatabase.Schema.awaitMigrate(driver, 164, 165)
            }
            assertEquals(listOf("sender@domain"), driver.strings("SELECT user_id FROM MessageMention"))
            driver.execute(null, "INSERT INTO MessageMention VALUES ('original','conversation@domain',0,4,NULL)", 0)
            val mentionsJson = driver.strings("SELECT mentions FROM MessageDetailsView WHERE id='original'").single()
            assertTrue(mentionsJson.contains("\"userId\":null"))
            assertTrue(mentionsJson.contains("\"value\":\"sender\""))
            driver.execute(null, "UPDATE Message SET id='edited' WHERE id='original'", 0)
            assertEquals(listOf("edited", "edited"), driver.strings("SELECT message_id FROM MessageMention"))
            assertEquals(emptyList(), driver.strings("PRAGMA foreign_key_check"))
            driver.execute(null, "DELETE FROM Message WHERE id='edited'", 0)
            assertEquals(emptyList(), driver.strings("SELECT message_id FROM MessageMention"))
        } finally {
            driver.close()
            file.delete()
        }
    }

    private fun JdbcSqliteDriver.strings(sql: String): List<String> {
        val values = mutableListOf<String>()
        executeQuery(null, sql, { cursor ->
            while (cursor.next().value) values += cursor.getString(0)!!
            QueryResult.Unit
        }, 0)
        return values
    }
}
