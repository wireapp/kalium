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

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitCreate
import app.cash.sqldelight.async.coroutines.awaitMigrate
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.wire.kalium.persistence.UserDatabase
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.persistence.dao.message.MessageMapper
import com.wire.kalium.persistence.dao.message.MessageTimelineLoadResultEntity
import com.wire.kalium.persistence.dao.message.MessageTimelineRequestEntity
import com.wire.kalium.persistence.db.DbInvalidationController
import com.wire.kalium.persistence.db.PlatformDatabaseData
import com.wire.kalium.persistence.db.StorageData
import com.wire.kalium.persistence.db.UserDatabaseBuilder
import com.wire.kalium.persistence.utils.stubs.newConversationEntity
import com.wire.kalium.persistence.utils.stubs.newRegularMessageEntity
import com.wire.kalium.persistence.utils.stubs.newUserEntity
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MessageTimelineMigrationTest {
    @Test
    fun freshAndUpgradedSchemasSupportBothTimelineApisAndIndexedSeeks() = runTest {
        val definitions = mutableListOf<List<String>>()
        for (upgrade in listOf(false, true)) {
            val file = Files.createTempFile("timeline-migration", ".db").toFile()
            if (upgrade) {
                File("src/commonTest/kotlin/com/wire/kalium/persistence/schemas/124.db").copyTo(file, overwrite = true)
            }
            val driver = JdbcSqliteDriver("jdbc:sqlite:${file.absolutePath}")
            try {
                if (upgrade) UserDatabase.Schema.awaitMigrate(driver, 124, 144) else UserDatabase.Schema.awaitCreate(driver)
                val self = newUserEntity("timeline-migration-self")
                val conversation = newConversationEntity("timeline-migration")
                val builder = UserDatabaseBuilder(
                    self.id, driver, StandardTestDispatcher(testScheduler), PlatformDatabaseData(StorageData.FileBacked(file)), false,
                    DbInvalidationController(false) { driver.notifyListeners(it) },
                )
                builder.userDAO.upsertUsers(listOf(self))
                builder.conversationDAO.insertConversations(listOf(conversation))
                val messages = (0..59).map {
                    newRegularMessageEntity(
                        id = "message-$it", conversationId = conversation.id, senderUserId = self.id,
                        date = Instant.fromEpochMilliseconds(it.toLong()), status = MessageEntity.Status.SENT,
                    )
                }
                builder.messageDAO.insertOrIgnoreMessages(messages, withUnreadEvents = false)
                if (upgrade) UserDatabase.Schema.awaitMigrate(driver, 144, UserDatabase.Schema.version)

                definitions += driver.strings(
                    "SELECT sql FROM sqlite_master " +
                        "WHERE name IN ('conversation_list_recency', 'idx_msg_conv_date_id_desc', 'MessageTimelineKey') ORDER BY name"
                )
                val offset = builder.database.messagesQueries.selectByConversationIdAndVisibility(
                    conversation.id, MessageEntity.Visibility.entries, 20, 20, MessageMapper::toEntityMessageFromView
                ).awaitAsList()
                assertEquals(messages.asReversed().drop(20).take(20).map { it.id }, offset.map { it.id })
                assertTrue(offset.all { it is MessageEntity.Regular })
                builder.messageTimelineDAO.open(conversation.id).use { session ->
                    val latest = assertIs<MessageTimelineLoadResultEntity.Success>(session.load(MessageTimelineRequestEntity.Latest, 20)).page
                    val older = assertIs<MessageTimelineLoadResultEntity.Success>(
                        session.load(MessageTimelineRequestEntity.Older(requireNotNull(latest.olderCursor)), 20)
                    ).page
                    assertEquals(offset.map { it.id }, older.rows.map { it.id })
                }
                for (direction in listOf("<" to "DESC", ">" to "ASC")) {
                    for (visibilities in listOf("'VISIBLE'", "'VISIBLE', 'DELETED'")) {
                        val plan = driver.strings(
                            """
                            EXPLAIN QUERY PLAN
                            SELECT id, creation_date FROM MessageTimelineKey
                            WHERE conversation_id = '${conversation.id}' AND visibility IN ($visibilities)
                              AND (creation_date, id) ${direction.first} (30, 'message-30')
                            ORDER BY creation_date ${direction.second}, id ${direction.second} LIMIT 21
                            """.trimIndent(), column = 3,
                        ).joinToString("\n")
                        assertTrue(plan.contains("idx_msg_conv_date_id_desc"), plan)
                        assertTrue(plan.contains("SEARCH m"), plan)
                        assertFalse(plan.contains("USE TEMP B-TREE"), plan)
                        assertFalse(plan.contains("SCAN m"), plan)
                    }
                }
            } finally {
                driver.close()
                file.delete()
            }
        }
        assertEquals(definitions.first(), definitions.last())
    }

    private fun SqlDriver.strings(sql: String, column: Int = 0): List<String> = executeQuery(
        null, sql, { cursor ->
            val rows = buildList { while (cursor.next().value) add(requireNotNull(cursor.getString(column))) }
            QueryResult.Value(rows)
        }, 0,
    ).value
}
