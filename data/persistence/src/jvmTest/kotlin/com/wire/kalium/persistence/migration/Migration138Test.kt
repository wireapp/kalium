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
package com.wire.kalium.persistence.migration

import app.cash.sqldelight.async.coroutines.awaitMigrate
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.wire.kalium.persistence.UserDatabase
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Migration138Test {

    @Test
    fun givenOldMessageIndexes_whenMigrating_thenReplacesThemWithKeysetIndexes() = runTest {
        val dbFile = File("build/user-schema-dumps/migration-138-test.db")
        dbFile.parentFile.mkdirs()
        File("src/commonTest/kotlin/com/wire/kalium/persistence/schemas/124.db").copyTo(dbFile, overwrite = true)
        val driver = JdbcSqliteDriver("jdbc:sqlite:${dbFile.absolutePath}")

        try {
            UserDatabase.Schema.awaitMigrate(driver, 124L, 138L)

            assertTrue("idx_msg_conv_vis_date_desc" in driver.messageIndexNames())

            UserDatabase.Schema.awaitMigrate(driver, 138L, 139L)

            val indexes = driver.messageIndexNames()
            assertFalse("idx_msg_conv_vis_date_desc" in indexes)
            assertTrue("idx_msg_conv_vis_date_id_desc" in indexes)
            assertTrue("idx_msg_pending" in indexes)

            val expectedKeysetColumns = listOf(
                "conversation_id" to 0L,
                "visibility" to 0L,
                "creation_date" to 1L,
                "id" to 1L,
            )
            assertEquals(expectedKeysetColumns, driver.messageIndexColumns("idx_msg_conv_vis_date_id_desc"))
            assertEquals(expectedKeysetColumns, driver.messageIndexColumns("idx_msg_pending"))
            assertTrue(driver.messageIndexSql("idx_msg_pending").contains("WHERE status = 'PENDING'"))
        } finally {
            driver.close()
            dbFile.delete()
        }
    }

    private fun JdbcSqliteDriver.messageIndexNames(): List<String> = executeQuery(
        identifier = null,
        sql = "PRAGMA index_list('Message')",
        mapper = { cursor ->
            val names = buildList {
                while (cursor.next().value) add(cursor.getString(1).orEmpty())
            }
            app.cash.sqldelight.db.QueryResult.Value(names)
        },
        parameters = 0,
    ).value

    private fun JdbcSqliteDriver.messageIndexColumns(indexName: String): List<Pair<String, Long?>> = executeQuery(
        identifier = null,
        sql = "PRAGMA index_xinfo('$indexName')",
        mapper = { cursor ->
            val columns = buildList {
                while (cursor.next().value) {
                    if (cursor.getLong(5) == 1L) add(cursor.getString(2).orEmpty() to cursor.getLong(3))
                }
            }
            app.cash.sqldelight.db.QueryResult.Value(columns)
        },
        parameters = 0,
    ).value

    private fun JdbcSqliteDriver.messageIndexSql(indexName: String): String = executeQuery(
        identifier = null,
        sql = "SELECT sql FROM sqlite_master WHERE type = 'index' AND name = ?",
        mapper = { cursor ->
            val sql = if (cursor.next().value) cursor.getString(0).orEmpty() else ""
            app.cash.sqldelight.db.QueryResult.Value(sql)
        },
        parameters = 1,
    ) {
        bindString(0, indexName)
    }.value
}
