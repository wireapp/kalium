/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.persistence.migration

import app.cash.sqldelight.SuspendingTransacterImpl
import app.cash.sqldelight.async.coroutines.awaitMigrate
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.wire.kalium.persistence.UserDatabase
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class Migration166Test {
    @Test
    fun storedEncryptionNoticesAndTheirMarkersAreDeleted() = runTest {
        val file = File.createTempFile("encryption-notice-migration-", ".db")
        File("src/commonTest/kotlin/com/wire/kalium/persistence/schemas/124.db").copyTo(file, overwrite = true)
        val driver = JdbcSqliteDriver("jdbc:sqlite:${file.absolutePath}")
        try {
            object : SuspendingTransacterImpl(driver) {}.transaction { UserDatabase.Schema.awaitMigrate(driver, 124, 166) }
            driver.execute(
                null,
                """
                INSERT INTO Conversation(qualified_id, type, mls_group_state, protocol, creator_id, last_modified_date,
                    access_list, access_role_list, mls_cipher_suite)
                VALUES ('group@domain', 'GROUP', 'ESTABLISHED', 'PROTEUS', 'self@domain', 0, '[]', '[]', 'UNKNOWN')
                """.trimIndent(),
                0
            )
            listOf("notice" to "CONVERSATION_STARTED_UNVERIFIED_WARNING", "created" to "CONVERSATION_CREATED").forEach { (id, type) ->
                driver.execute(
                    null,
                    """
                    INSERT INTO Message(id, conversation_id, content_type, creation_date, sender_user_id, status, visibility)
                    VALUES ('$id', 'group@domain', '$type', 0, 'self@domain', 'SENT', 'VISIBLE')
                    """.trimIndent(),
                    0
                )
            }
            listOf("connection.accepted-warning/peer@domain/one@domain/0", "other").forEach { key ->
                driver.execute(null, "INSERT INTO Metadata(key, stringValue) VALUES ('$key', 'persisted')", 0)
            }

            object : SuspendingTransacterImpl(driver) {}.transaction { UserDatabase.Schema.awaitMigrate(driver, 166, 167) }

            assertEquals(listOf("created"), driver.strings("SELECT id FROM Message"))
            assertEquals(
                listOf("other"),
                driver.strings("SELECT key FROM Metadata WHERE key = 'other' OR key LIKE 'connection.accepted-warning/%'")
            )
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
