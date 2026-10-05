/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.persistence.migration

import app.cash.sqldelight.SuspendingTransacterImpl
import app.cash.sqldelight.async.coroutines.awaitMigrate
import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.wire.kalium.persistence.UserDatabase
import com.wire.kalium.persistence.db.UserDatabaseSchema
import kotlinx.coroutines.test.runTest
import org.sqlite.SQLiteConfig
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class Migration144Test {
    @Test
    fun upstreamVersions144And145RunAllRemainingLocalMigrationsOnReopen() = runTest {
        for (upstreamVersion in listOf(144L, 145L)) withDatabase { file, driver ->
            object : SuspendingTransacterImpl(driver) {}.transaction {
                UserDatabase.Schema.awaitMigrate(driver, 2, upstreamVersion)
            }
            seedConversation(driver)
            if (upstreamVersion == 145L) driver.execute(null,
                "INSERT INTO AdminlessGroupDelete VALUES ('conversation@wire.test', 1234)", 0)
            driver.execute(null, "PRAGMA user_version = $upstreamVersion", 0)
            driver.close()
            reopenAndVerify(file, if (upstreamVersion == 145L) "1234" else null)
        }
    }

    private fun seedConversation(driver: JdbcSqliteDriver) {
        driver.execute(null, "INSERT INTO User(qualified_id) VALUES ('creator@wire.test')", 0)
        driver.execute(null, """
            INSERT INTO Conversation(qualified_id, type, mls_group_state, protocol, creator_id,
                last_modified_date, access_list, access_role_list, mls_cipher_suite)
            VALUES ('conversation@wire.test', 'GROUP', 'ESTABLISHED', 'PROTEUS', 'creator', 0, '', '', 'DEFAULT')
        """.trimIndent(), 0)
        driver.execute(null, """
            INSERT INTO Message(id, conversation_id, content_type, creation_date, sender_user_id, status, visibility)
            VALUES ('existing', 'conversation@wire.test', 'TEXT', 1, 'creator@wire.test', 'SENT', 'VISIBLE')
        """.trimIndent(), 0)
    }

    private fun reopenAndVerify(file: File, timestamp: String?) {
        JdbcSqliteDriver("jdbc:sqlite:${file.absolutePath}", properties, UserDatabaseSchema.synchronous()).use { driver ->
            assertEquals(listOf("154"), driver.values("PRAGMA user_version"))
            assertEquals(listOf(timestamp), driver.values("SELECT adminlessGroupDeletionTimestamp FROM ConversationDetails"))
            assertEquals(listOf("existing"), driver.values("SELECT id FROM MessageTimelineKey"))
            assertEquals(listOf("0"), driver.values("SELECT count(*) FROM PendingDeliveryConfirmations"))
            assertEquals(listOf("MessageNotificationIdentity", "Subconversation", "conversation_list_recency"), driver.values("""
                SELECT name FROM sqlite_master
                WHERE name IN ('MessageNotificationIdentity', 'Subconversation', 'conversation_list_recency', 'NotificationReceipts')
                ORDER BY name
            """.trimIndent()))
            assertEquals(emptyList(), driver.values("PRAGMA foreign_key_check"))
        }
    }

    private suspend fun withDatabase(block: suspend (File, JdbcSqliteDriver) -> Unit) {
        val file = File.createTempFile("adminless-migration-", ".db")
        File("src/commonMain/db_user/schemas/2.db").copyTo(file, overwrite = true)
        try {
            JdbcSqliteDriver("jdbc:sqlite:${file.absolutePath}", properties).use { block(file, it) }
        } finally {
            file.delete()
        }
    }

    private fun JdbcSqliteDriver.values(sql: String): List<String?> = executeQuery(null, sql, { cursor ->
        val values = mutableListOf<String?>()
        while (cursor.next().value) values += cursor.getString(0)
        QueryResult.Value(values)
    }, 0).value

    private val properties = SQLiteConfig().apply { enforceForeignKeys(true) }.toProperties()
}
