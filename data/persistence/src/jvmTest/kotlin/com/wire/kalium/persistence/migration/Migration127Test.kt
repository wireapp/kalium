/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

package com.wire.kalium.persistence.dao.migration

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.sqlite.driver.JdbcSqliteDriver
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Migration127Test {

    @Test
    fun givenMigration127_whenExecuted_thenAllTablesAreCreated() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            executeMigration(driver, migrationSql())

            assertObjectExists(driver, type = "table", name = "MessageMainList")
            assertObjectExists(driver, type = "table", name = "MessageThreadRoot")
            assertObjectExists(driver, type = "table", name = "MessageThreadItem")
        } finally {
            driver.close()
        }
    }

    @Test
    fun givenMigration127_whenExecuted_thenAllIndexesAreCreated() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            executeMigration(driver, migrationSql())

            assertObjectExists(driver, type = "index", name = "message_main_list_conv_vis_date_desc")
            assertObjectExists(driver, type = "index", name = "message_thread_item_conv_thread_vis_date_desc")
            assertObjectExists(driver, type = "index", name = "message_thread_item_conv_thread_message")
        } finally {
            driver.close()
        }
    }

    @Test
    fun givenMigration127_whenExecuted_thenNoLegacyIndexesExist() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            executeMigration(driver, migrationSql())

            assertObjectDoesNotExist(driver, type = "index", name = "message_thread_item_conv_thread_date_desc")
            assertObjectDoesNotExist(driver, type = "index", name = "message_thread_item_conv_root_message")
        } finally {
            driver.close()
        }
    }

    @Test
    fun givenMigration127_whenExecuted_thenThreadRootHasAllColumns() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            executeMigration(driver, migrationSql())

            assertColumnExists(driver, table = "MessageThreadRoot", column = "conversation_id")
            assertColumnExists(driver, table = "MessageThreadRoot", column = "root_message_id")
            assertColumnExists(driver, table = "MessageThreadRoot", column = "thread_id")
            assertColumnExists(driver, table = "MessageThreadRoot", column = "created_at")
            assertColumnExists(driver, table = "MessageThreadRoot", column = "visible_reply_count")
            assertColumnExists(driver, table = "MessageThreadRoot", column = "last_reply_date")
        } finally {
            driver.close()
        }
    }

    @Test
    fun givenMigration127_whenExecuted_thenThreadItemHasVisibilityColumn() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            executeMigration(driver, migrationSql())

            assertColumnExists(driver, table = "MessageThreadItem", column = "visibility")
        } finally {
            driver.close()
        }
    }

    @Test
    fun givenExistingMessages_whenMigration127Executed_thenMessageMainListIsBackfilled() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            createMinimalMessageTable(driver)

            insertMessage(driver, id = "msg-1", conversationId = "conv-a", creationDate = 1000L, visibility = "VISIBLE")
            insertMessage(driver, id = "msg-2", conversationId = "conv-a", creationDate = 2000L, visibility = "DELETED")
            insertMessage(driver, id = "msg-3", conversationId = "conv-b", creationDate = 3000L, visibility = "VISIBLE")

            executeMigration(driver, migrationSql())

            val rows = selectAllMainListRows(driver)
            assertEquals(3, rows.size, "All messages should be backfilled into MessageMainList")

            val msg1 = rows.first { it.messageId == "msg-1" }
            assertEquals("conv-a", msg1.conversationId)
            assertEquals(1000L, msg1.creationDate)
            assertEquals("VISIBLE", msg1.visibility)

            val msg2 = rows.first { it.messageId == "msg-2" }
            assertEquals("conv-a", msg2.conversationId)
            assertEquals(2000L, msg2.creationDate)
            assertEquals("DELETED", msg2.visibility)

            val msg3 = rows.first { it.messageId == "msg-3" }
            assertEquals("conv-b", msg3.conversationId)
            assertEquals(3000L, msg3.creationDate)
            assertEquals("VISIBLE", msg3.visibility)
        } finally {
            driver.close()
        }
    }

    @Test
    fun givenEmptyMessageTable_whenMigration127Executed_thenMessageMainListIsEmpty() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            createMinimalMessageTable(driver)

            executeMigration(driver, migrationSql())

            val count = countMainListRows(driver)
            assertEquals(0, count, "MessageMainList should be empty when no messages exist")
        } finally {
            driver.close()
        }
    }

    // region helpers

    private fun migrationSql(): String {
        val migrationFile = File("src/commonMain/db_user/migrations/127.sqm")
        check(migrationFile.exists()) { "Migration file not found: ${migrationFile.absolutePath}" }
        return migrationFile.readText()
    }

    /**
     * Creates a minimal Message table that satisfies the FK references in the migration.
     * Uses only the columns referenced by the backfill INSERT.
     */
    private fun createMinimalMessageTable(driver: JdbcSqliteDriver) {
        driver.execute(
            null,
            """
            CREATE TABLE Message (
                id TEXT NOT NULL,
                conversation_id TEXT NOT NULL,
                creation_date INTEGER NOT NULL,
                visibility TEXT NOT NULL DEFAULT 'VISIBLE',
                PRIMARY KEY (id, conversation_id)
            )
            """.trimIndent(),
            0
        )
    }

    private fun insertMessage(
        driver: JdbcSqliteDriver,
        id: String,
        conversationId: String,
        creationDate: Long,
        visibility: String
    ) {
        driver.execute(
            null,
            "INSERT INTO Message (id, conversation_id, creation_date, visibility) VALUES (?, ?, ?, ?)",
            4
        ) {
            bindString(0, id)
            bindString(1, conversationId)
            bindLong(2, creationDate)
            bindString(3, visibility)
        }
    }

    private data class MainListRow(
        val conversationId: String,
        val messageId: String,
        val creationDate: Long,
        val visibility: String,
    )

    private fun selectAllMainListRows(driver: JdbcSqliteDriver): List<MainListRow> {
        val rows = mutableListOf<MainListRow>()
        driver.executeQuery(
            null,
            "SELECT conversation_id, message_id, creation_date, visibility FROM MessageMainList ORDER BY creation_date",
            mapper = { cursor ->
                while (cursor.next().value) {
                    rows.add(
                        MainListRow(
                            conversationId = cursor.getString(0)!!,
                            messageId = cursor.getString(1)!!,
                            creationDate = cursor.getLong(2)!!,
                            visibility = cursor.getString(3)!!,
                        )
                    )
                }
                QueryResult.Unit
            },
            parameters = 0
        )
        return rows
    }

    private fun countMainListRows(driver: JdbcSqliteDriver): Long {
        var count = 0L
        driver.executeQuery(
            null,
            "SELECT COUNT(*) FROM MessageMainList",
            mapper = { cursor ->
                cursor.next()
                count = cursor.getLong(0) ?: 0L
                QueryResult.Unit
            },
            parameters = 0
        )
        return count
    }

    private fun executeMigration(driver: JdbcSqliteDriver, migrationSql: String) {
        val statements = migrationSql.split(";")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        statements.forEach { statement ->
            driver.execute(null, statement, 0)
        }
    }

    private fun assertObjectExists(driver: JdbcSqliteDriver, type: String, name: String) {
        var exists = false
        driver.executeQuery(
            null,
            """
                SELECT 1
                FROM sqlite_master
                WHERE type = ? AND name = ?
                LIMIT 1
            """.trimIndent(),
            mapper = { cursor ->
                exists = cursor.next().value
                QueryResult.Unit
            },
            parameters = 2
        ) {
            bindString(0, type)
            bindString(1, name)
        }
        assertTrue(exists, "Expected sqlite object '$name' of type '$type' to exist")
    }

    private fun assertObjectDoesNotExist(driver: JdbcSqliteDriver, type: String, name: String) {
        var exists = false
        driver.executeQuery(
            null,
            """
                SELECT 1
                FROM sqlite_master
                WHERE type = ? AND name = ?
                LIMIT 1
            """.trimIndent(),
            mapper = { cursor ->
                exists = cursor.next().value
                QueryResult.Unit
            },
            parameters = 2
        ) {
            bindString(0, type)
            bindString(1, name)
        }
        assertFalse(exists, "Expected sqlite object '$name' of type '$type' to not exist")
    }

    private fun assertColumnExists(driver: JdbcSqliteDriver, table: String, column: String) {
        var exists = false
        driver.executeQuery(
            null,
            """
                SELECT 1
                FROM pragma_table_info('$table')
                WHERE name = ?
                LIMIT 1
            """.trimIndent(),
            mapper = { cursor ->
                exists = cursor.next().value
                QueryResult.Unit
            },
            parameters = 1
        ) {
            bindString(0, column)
        }
        assertTrue(exists, "Expected column '$column' to exist in table '$table'")
    }

    // endregion
}
