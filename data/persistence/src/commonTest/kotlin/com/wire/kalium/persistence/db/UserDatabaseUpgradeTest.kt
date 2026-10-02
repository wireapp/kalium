/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.wire.kalium.persistence.db

import app.cash.sqldelight.async.coroutines.await
import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import com.wire.kalium.persistence.UserDatabase
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.dao.meeting.MeetingOccurrencesGenerator.GenerationLimit
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/** Each platform copies the same parent-created SQLite files and opens its production builder. */
abstract class UserDatabaseUpgradeTest {
    protected val dispatcher = StandardTestDispatcher()
    protected val userId = UserIDEntity("creator", "wire.test")

    protected abstract suspend fun withDatabase(
        fixture: String?,
        explicitMigration: Boolean = false,
        block: suspend (open: () -> UserDatabaseBuilder) -> Unit,
    )

    @Test
    fun legacyForkProductionReopenPreservesState() = runTest(dispatcher) {
        for (version in 143..149) verifyUpgrade("fork$version", version, "Europe/Berlin", false)
    }

    @Test
    fun legacyForkExplicitMigrationPreservesState() = runTest(dispatcher) {
        for (version in 143..149) verifyUpgrade("fork$version", version, "Europe/Berlin", true)
    }

    @Test
    fun upstreamProductionReopenKeepsStoredTimezone() = runTest(dispatcher) {
        verifyUpgrade("upstream144", 144, "America/New_York", false)
    }

    @Test
    fun upstreamExplicitMigrationKeepsStoredTimezone() = runTest(dispatcher) {
        verifyUpgrade("upstream144", 144, "America/New_York", true)
    }

    @Test
    fun freshProductionDatabaseSupportsTimezoneAndReopen() = runTest(dispatcher) {
        withDatabase(null) { open ->
            val builder = open()
            try {
                val driver = builder.sqlDriver
                assertEquals(listOf(listOf("153")), driver.rows("PRAGMA user_version", 1))
                driver.execute(null, "INSERT INTO User(qualified_id) VALUES ('creator@wire.test')", 0).await()
                driver.execute(null, """
                    INSERT INTO Conversation(qualified_id, type, mls_group_state, protocol, creator_id,
                        last_modified_date, access_list, access_role_list, mls_cipher_suite)
                    VALUES ('conversation@wire.test', 'MEETING', 'ESTABLISHED', 'MLS', 'creator', 0, '', '', 'DEFAULT')
                """.trimIndent(), 0).await()
                driver.execute(null, """
                    INSERT INTO Meeting(meeting_id, conversation_id, creator_id, creation_date, title, start_date, end_date, tzid)
                    VALUES ('one@wire.test', 'conversation@wire.test', 'creator@wire.test', 1, 'One', 2, 3, 'Asia/Tokyo')
                """.trimIndent(), 0).await()
                assertLocalSchema(driver)
            } finally {
                builder.close()
            }
            val reopened = open()
            try {
                assertEquals("Asia/Tokyo", checkNotNull(reopened.meetingDao.getMeeting(UserIDEntity("one", "wire.test"))).tzid)
                assertEquals(emptyList(), reopened.sqlDriver.rows("PRAGMA foreign_key_check", 4))
            } finally {
                reopened.close()
            }
        }
    }

    private suspend fun verifyUpgrade(fixture: String, oldVersion: Int, tzid: String, explicitMigration: Boolean) {
        withDatabase(fixture, explicitMigration) { open ->
            val builder = open()
            var generatedRows: List<List<String?>>
            try {
                val driver = builder.sqlDriver
                assertEquals(listOf(listOf("153")), driver.rows("PRAGMA user_version", 1), fixture)
                assertEquals(UserDatabase.Schema.version, UserDatabaseSchema.version)
                assertEquals(listOf(listOf(tzid), listOf(tzid)), driver.rows("SELECT tzid FROM Meeting ORDER BY meeting_id", 1), fixture)
                assertEquals(emptyList(), driver.rows("SELECT name FROM pragma_table_info('Meeting') WHERE name = 'trial'", 1))
                assertEquals(listOf(
                    listOf("one@wire.test", "conversation@wire.test", "creator@wire.test", "1", null, "One", "2", "3", null, null, null),
                    listOf("series@wire.test", "conversation@wire.test", "creator@wire.test", "4", "5", "Series",
                        "1792828800000", "1792832400000", "DAILY", "1", null),
                ), driver.rows("""
                    SELECT meeting_id, conversation_id, creator_id, creation_date, last_edit_date, title, start_date,
                        end_date, recurrence_frequency, recurrence_interval, recurrence_end_date FROM Meeting ORDER BY meeting_id
                """.trimIndent(), 11), fixture)
                assertEquals(listOf(listOf("one-occurrence", "one@wire.test", "2", "3")),
                    driver.rows("SELECT * FROM MeetingOccurrence", 4), fixture)
                assertEquals(listOf(listOf("pending-event", "0", "[]", "0", "1"), listOf("processed-event", "1", "[]", "0", "0")),
                    driver.rows("SELECT event_id, is_processed, payload, transient, is_live FROM Events ORDER BY id", 5), fixture)
                assertEquals(listOf(listOf("preserved-message", "TEXT", "conversation@wire.test", "1", "creator@wire.test", "SENT", "VISIBLE")),
                    driver.rows("SELECT id, content_type, conversation_id, creation_date, sender_user_id, status, visibility FROM Message", 7))
                assertEquals(listOf(listOf("preserved-message", "conversation@wire.test", "creator@wire.test", "READ", "2026-10-24T08:00:00Z")),
                    driver.rows("SELECT * FROM Receipt", 5), fixture)
                assertLocalSchema(driver)
                assertEquals(listOf(listOf("preserved-message")), driver.rows("SELECT id FROM MessageTimelineKey", 1))
                val queue = listOf(listOf("1", "conversation", "preserved-message"))
                assertEquals(if (oldVersion >= 146) queue else emptyList(), driver.rows("SELECT * FROM PendingDeliveryConfirmations", 3))
                if (oldVersion < 146) driver.execute(null,
                    "INSERT INTO PendingDeliveryConfirmations(conversation_id, message_id) VALUES ('conversation', 'preserved-message')", 0).await()

                val series = checkNotNull(builder.meetingDao.getMeeting(UserIDEntity("series", "wire.test")))
                builder.meetingDao.upsertMeetings(
                    listOf(series), GenerationLimit.Window(series.startTime, Instant.parse("2026-10-26T10:00:00Z")),
                )
                val starts = builder.database.meetingsQueries.selectMeetingOccurrencesByMeetingId(series.meetingId)
                    .awaitAsList().map { it.occurrence_start }
                assertEquals(if (tzid == "Europe/Berlin") listOf("2026-10-24T08:00:00Z", "2026-10-25T09:00:00Z", "2026-10-26T09:00:00Z")
                    else listOf("2026-10-24T08:00:00Z", "2026-10-25T08:00:00Z", "2026-10-26T08:00:00Z"), starts.map(Instant::toString))
                generatedRows = driver.rows("SELECT * FROM MeetingOccurrence ORDER BY occurrence_id", 4)
                assertEquals(emptyList(), driver.rows("PRAGMA foreign_key_check", 4))
            } finally {
                builder.close()
            }
            val reopened = open()
            try {
                assertEquals(generatedRows, reopened.sqlDriver.rows("SELECT * FROM MeetingOccurrence ORDER BY occurrence_id", 4), fixture)
                assertEquals(listOf(listOf("1", "conversation", "preserved-message")),
                    reopened.sqlDriver.rows("SELECT * FROM PendingDeliveryConfirmations", 3), fixture)
                assertLocalSchema(reopened.sqlDriver)
            } finally {
                reopened.close()
            }
        }
    }

    private suspend fun assertLocalSchema(driver: SqlDriver) {
        assertEquals(listOf("MessageTimelineKey", "PendingDeliveryConfirmations", "conversation_list_recency", "idx_msg_conv_date_id_desc")
            .map { listOf(it) }, driver.rows("""
                SELECT name FROM sqlite_master WHERE name IN ('conversation_list_recency', 'idx_msg_conv_date_id_desc',
                    'MessageTimelineKey', 'PendingDeliveryConfirmations', 'NotificationReceipts') ORDER BY name
            """.trimIndent(), 1))
        assertEquals(emptyList(), driver.rows("SELECT name FROM sqlite_temp_master WHERE name = 'MeetingOccurrenceMigration'", 1))
        assertEquals(emptyList(), driver.rows("PRAGMA foreign_key_check", 4))
    }

    private suspend fun SqlDriver.rows(sql: String, columns: Int): List<List<String?>> = executeQuery(null, sql, { cursor ->
        val rows = mutableListOf<List<String?>>()
        while (cursor.next().value) rows += (0 until columns).map { cursor.getString(it) }
        QueryResult.Value(rows)
    }, 0).await()
}
