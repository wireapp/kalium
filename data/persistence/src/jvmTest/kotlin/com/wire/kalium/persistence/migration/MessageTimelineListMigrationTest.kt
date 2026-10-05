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

import app.cash.sqldelight.SuspendingTransacterImpl
import app.cash.sqldelight.async.coroutines.awaitMigrate
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.wire.kalium.persistence.UserDatabase
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class MessageTimelineListMigrationTest {
    @Test
    fun givenConversationListMigrationApplied_whenUpgrading_thenBothIndexesAndTimelineViewExist() = runTest {
        val dbFile = File("build/user-schema-dumps/message-timeline-upgrade.db")
        dbFile.parentFile.mkdirs()
        File("src/commonMain/db_user/schemas/2.db").copyTo(dbFile, overwrite = true)
        try {
            JdbcSqliteDriver("jdbc:sqlite:${dbFile.absolutePath}").use { driver ->
                object : SuspendingTransacterImpl(driver) {}.transaction {
                    UserDatabase.Schema.awaitMigrate(driver, 2, 145)
                }
                assertEquals(emptyMap(), driver.timelineSchema())
                UserDatabase.Schema.awaitMigrate(driver, 145, 146)
                assertEquals(mapOf("conversation_list_recency" to "index"), driver.timelineSchema())
            }
            // Reopen an installation that has already applied migration 145.
            JdbcSqliteDriver("jdbc:sqlite:${dbFile.absolutePath}").use { driver ->
                UserDatabase.Schema.awaitMigrate(driver, 146, UserDatabase.Schema.version)
                val expected = mapOf(
                    "conversation_list_recency" to "index",
                    "idx_msg_conv_date_id_desc" to "index",
                    "MessageTimelineKey" to "view",
                )
                assertEquals(expected, driver.timelineSchema())
                JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { fresh ->
                    UserDatabase.Schema.create(fresh).await()
                    assertEquals(expected, fresh.timelineSchema())
                }
            }
        } finally {
            dbFile.delete()
        }
    }

    private fun JdbcSqliteDriver.timelineSchema(): Map<String, String> {
        val result = mutableMapOf<String, String>()
        executeQuery(
            null,
            """
                SELECT name, type FROM sqlite_master
                WHERE name IN ('conversation_list_recency', 'idx_msg_conv_date_id_desc', 'MessageTimelineKey')
            """.trimIndent(),
            mapper = { cursor ->
                while (cursor.next().value) result[cursor.getString(0)!!] = cursor.getString(1)!!
                QueryResult.Unit
            },
            0
        )
        return result
    }
}
