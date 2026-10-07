/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.wire.kalium.persistence.migration

import app.cash.sqldelight.SuspendingTransacterImpl
import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitMigrate
import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.wire.kalium.persistence.UserDatabase
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.dao.meeting.MeetingOccurrencesGenerator.GenerationLimit
import com.wire.kalium.persistence.db.DbInvalidationController
import com.wire.kalium.persistence.db.PlatformDatabaseData
import com.wire.kalium.persistence.db.StorageData
import com.wire.kalium.persistence.db.UserDatabaseBuilder
import com.wire.kalium.persistence.db.UserDatabaseSchema
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.sqlite.SQLiteConfig
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class Migration143Test {
    @Test
    fun upstreamMeetingUpgradePreservesRowsAndRunsEveryLocalMigrationAfterReopen() = runTest {
        val file = Files.createTempFile("meeting-upstream-upgrade", ".db").toFile()
        File("src/commonMain/db_user/schemas/2.db").copyTo(file, overwrite = true)
        val properties = SQLiteConfig().apply { enforceForeignKeys(true) }.toProperties()
        val seriesStart = Instant.parse("2026-10-24T08:00:00Z").toEpochMilliseconds()
        val seriesEnd = Instant.parse("2026-10-24T09:00:00Z").toEpochMilliseconds()
        try {
            JdbcSqliteDriver("jdbc:sqlite:${file.absolutePath}", properties).use { driver ->
                // Schema 2 and migrations 1..142 match upstream, apart from Kotlin Instant imports.
                object : SuspendingTransacterImpl(driver) {}.transaction {
                    UserDatabase.Schema.awaitMigrate(driver, 2, 143)
                }
                driver.execute(null, "INSERT INTO User(qualified_id) VALUES ('creator@wire.test')", 0)
                driver.execute(null, """
                    INSERT INTO Conversation(qualified_id, type, mls_group_state, protocol, creator_id,
                        last_modified_date, access_list, access_role_list, mls_cipher_suite)
                    VALUES ('conversation@wire.test', 'MEETING', 'ESTABLISHED', 'MLS', 'creator', 0, '', '', 'DEFAULT')
                """.trimIndent(), 0)
                driver.execute(null, """
                    INSERT INTO Meeting VALUES
                    ('one@wire.test', 'conversation@wire.test', 'creator@wire.test', 1, NULL, 'One', 2, 3, 0, NULL, NULL, NULL),
                    ('series@wire.test', 'conversation@wire.test', 'creator@wire.test', 4, 5, 'Series', $seriesStart, $seriesEnd, 1, 'DAILY', 1, NULL)
                """.trimIndent(), 0)
                driver.execute(null, """
                    INSERT INTO MeetingOccurrence VALUES
                    ('one-occurrence', 'one@wire.test', 2, 3),
                    ('series-24', 'series@wire.test', $seriesStart, $seriesEnd),
                    ('series-25', 'series@wire.test', ${seriesStart + 86_400_000}, ${seriesEnd + 86_400_000}),
                    ('series-26', 'series@wire.test', ${seriesStart + 172_800_000}, ${seriesEnd + 172_800_000})
                """.trimIndent(), 0)
                val preservedColumns = "meeting_id, conversation_id, creator_id, creation_date, last_edit_date, " +
                    "title, start_date, end_date, recurrence_frequency, recurrence_interval, recurrence_end_date"
                val meetings = driver.rows("SELECT $preservedColumns FROM Meeting ORDER BY meeting_id", 11)
                val occurrences = driver.rows("SELECT * FROM MeetingOccurrence ORDER BY occurrence_id", 4)
                object : SuspendingTransacterImpl(driver) {}.transaction {
                    UserDatabase.Schema.awaitMigrate(driver, 143, 144)
                }
                assertEquals(meetings, driver.rows("SELECT $preservedColumns FROM Meeting ORDER BY meeting_id", 11))
                assertEquals(occurrences, driver.rows("SELECT * FROM MeetingOccurrence ORDER BY occurrence_id", 4))
                assertEquals(listOf(listOf("Europe/Berlin"), listOf("Europe/Berlin")), driver.rows("SELECT tzid FROM Meeting", 1))
                assertEquals(emptyList(), driver.rows("PRAGMA foreign_key_check", 4))
                assertEquals(emptyList(), driver.rows("SELECT name FROM sqlite_master WHERE name = 'PendingDeliveryConfirmations'", 1))
                driver.execute(null, "PRAGMA user_version = 144", 0)
            }
            // Open the upstream version through the production driver's automatic migration path.
            JdbcSqliteDriver("jdbc:sqlite:${file.absolutePath}", properties, UserDatabaseSchema.synchronous()).use { driver ->
                assertEquals(157L, UserDatabase.Schema.version)
                assertEquals(listOf(listOf("157")), driver.rows("PRAGMA user_version", 1))
                assertEquals(listOf(listOf("Europe/Berlin"), listOf("Europe/Berlin")), driver.rows("SELECT tzid FROM Meeting", 1))
                assertEquals(listOf(listOf("one-occurrence", "one@wire.test", "2", "3")), driver.rows("SELECT * FROM MeetingOccurrence", 4))
                assertEquals(emptyList(), driver.rows("PRAGMA foreign_key_check", 4))
                val names = driver.rows("""
                    SELECT name FROM sqlite_master WHERE name IN ('conversation_list_recency', 'idx_msg_conv_date_id_desc',
                        'MessageTimelineKey', 'PendingDeliveryConfirmations', 'NotificationReceipts') ORDER BY name
                """.trimIndent(), 1)
                assertEquals(listOf("MessageTimelineKey", "PendingDeliveryConfirmations", "conversation_list_recency",
                    "idx_msg_conv_date_id_desc").map { listOf(it) }, names)
                driver.execute(null, "INSERT INTO PendingDeliveryConfirmations(conversation_id, message_id) VALUES ('conversation', 'message')", 0)
                assertEquals(listOf(listOf("1")), driver.rows("SELECT count(*) FROM PendingDeliveryConfirmations", 1))
                assertEquals(emptyList(), driver.rows("SELECT name FROM sqlite_temp_master WHERE name = 'MeetingOccurrenceMigration'", 1))

                val builder = UserDatabaseBuilder(
                    UserIDEntity("creator", "wire.test"), driver, StandardTestDispatcher(testScheduler),
                    PlatformDatabaseData(StorageData.FileBacked(file)), false,
                    DbInvalidationController(false) { driver.notifyListeners(it) }, initializeSelfUser = false,
                )
                val series = checkNotNull(builder.meetingDao.getMeeting(UserIDEntity("series", "wire.test")))
                // An unchanged Berlin payload must generate from the original start, not the legacy UTC cache.
                builder.meetingDao.upsertMeetings(
                    listOf(series), GenerationLimit.Window(series.startTime, Instant.parse("2026-10-26T10:00:00Z")),
                )
                builder.meetingDao.insertMissingOccurrences(
                    GenerationLimit.Window(series.startTime, Instant.parse("2026-10-28T10:00:00Z")),
                )
                assertEquals(
                    listOf("2026-10-24T08:00:00Z", "2026-10-25T09:00:00Z", "2026-10-26T09:00:00Z",
                        "2026-10-27T09:00:00Z", "2026-10-28T09:00:00Z").map(Instant::parse),
                    builder.database.meetingsQueries.selectMeetingOccurrencesByMeetingId(series.meetingId)
                        .awaitAsList().map { it.occurrence_start },
                )
            }
            // Reopening at the final version must not reapply any of the local CREATE operations.
            JdbcSqliteDriver("jdbc:sqlite:${file.absolutePath}", properties, UserDatabaseSchema.synchronous()).use { driver ->
                assertEquals(listOf(listOf("1")), driver.rows("SELECT count(*) FROM PendingDeliveryConfirmations", 1))
                assertEquals(listOf(listOf("6")), driver.rows("SELECT count(*) FROM MeetingOccurrence", 1))
            }
        } finally {
            file.delete()
        }
    }

    private fun JdbcSqliteDriver.rows(sql: String, columns: Int): List<List<String?>> = executeQuery(null, sql, { cursor ->
        val rows = mutableListOf<List<String?>>()
        while (cursor.next().value) rows += (0 until columns).map { cursor.getString(it) }
        QueryResult.Value(rows)
    }, 0).value
}
