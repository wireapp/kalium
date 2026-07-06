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
package com.wire.kalium.persistence.dao.meeting

import com.wire.kalium.persistence.BaseDatabaseTest
import com.wire.kalium.persistence.MeetingsQueries
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.db.UserDatabaseBuilder
import com.wire.kalium.persistence.utils.stubs.newConversationEntity
import com.wire.kalium.persistence.utils.stubs.newUserEntity
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimePeriod
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

class MeetingDaoTest : BaseDatabaseTest() {

    private lateinit var databaseBuilder: UserDatabaseBuilder
    private lateinit var meetingDao: MeetingDao
    private lateinit var meetingsQueries: MeetingsQueries

    @BeforeTest
    fun setUp() {
        deleteDatabase(SELF_USER_ID)
        databaseBuilder = createDatabase(SELF_USER_ID, encryptedDBSecret, enableWAL = true)
        meetingDao = databaseBuilder.meetingDao
        meetingsQueries = databaseBuilder.database.meetingsQueries
    }

    @Test
    fun givenRecurringMeeting_whenUpserted_thenFutureOccurrencesAreGeneratedForThreeMonthsAhead() =
        runTest(dispatcher) {
            val now = nowAtStoredPrecision()
            val meeting = newMeeting(
                startTime = now + 1.days,
                recurrence = MeetingEntity.RecurrenceEntity(
                    frequency = MeetingEntity.RecurrenceEntity.Frequency.DAILY,
                    interval = 1,
                    until = now + 120.days
                )
            )
            insertMeetingDependencies(meeting)

            meetingDao.upsertMeetings(listOf(meeting))

            val upperBound = Clock.System.now() + OCCURRENCE_GENERATION_WINDOW_DAYS.days
            val occurrences = occurrencesFor(meeting)
            assertTrue(occurrences.isNotEmpty())
            assertTrue(occurrences.all { it.occurrence_start > now })
            assertTrue(occurrences.all { it.occurrence_start <= upperBound })
        }

    @Test
    fun givenGeneratedOccurrences_whenSameMeetingIsUpserted_thenOccurrencesAreNotDuplicated() = runTest(dispatcher) {
        val now = nowAtStoredPrecision()
        val meeting = newMeeting(
            startTime = now + 1.days,
            recurrence = MeetingEntity.RecurrenceEntity(
                frequency = MeetingEntity.RecurrenceEntity.Frequency.DAILY,
                interval = 1,
                until = (now + 1.days).plusCalendarDays(10)
            )
        )
        insertMeetingDependencies(meeting)
        meetingDao.upsertMeetings(listOf(meeting))
        val firstOccurrences = occurrencesFor(meeting)

        meetingDao.upsertMeetings(listOf(meeting))

        val secondOccurrences = occurrencesFor(meeting)
        assertEquals(firstOccurrences.size, secondOccurrences.size)
        assertContentEquals(
            firstOccurrences.map { it.occurrence_id },
            secondOccurrences.map { it.occurrence_id }
        )
        assertContentEquals(
            firstOccurrences.map { it.occurrence_start },
            secondOccurrences.map { it.occurrence_start }
        )
    }

    @Test
    fun givenStoredMeeting_whenRecurrenceChanges_thenNotStartedOccurrencesAreReplacedAndStartedOnesKept() =
        runTest(dispatcher) {
            val now = nowAtStoredPrecision()
            val oldStart = now - 3.days + 3.hours
            val oldMeeting = newMeeting(
                startTime = oldStart,
                endTime = oldStart + 1.hours,
                recurrence = MeetingEntity.RecurrenceEntity(
                    frequency = MeetingEntity.RecurrenceEntity.Frequency.DAILY,
                    interval = 1,
                    until = oldStart.plusCalendarDays(14)
                )
            )
            insertMeetingDependencies(oldMeeting)
            meetingDao.upsertMeetings(listOf(oldMeeting))
            val startedOccurrenceId = "started-occurrence"
            val startedOccurrenceStart = now - 1.days
            meetingsQueries.insertMeetingOccurrence(
                occurrence_id = startedOccurrenceId,
                meeting_id = oldMeeting.meetingId,
                occurrence_start = startedOccurrenceStart,
                occurrence_end = startedOccurrenceStart + 1.hours
            )
            val oldFutureOccurrenceIds = occurrencesFor(oldMeeting)
                .filter { it.occurrence_start > now }
                .map { it.occurrence_id }
                .toSet()
            assertTrue(oldFutureOccurrenceIds.isNotEmpty())

            val changedStart = now - 6.days
            val changedMeeting = oldMeeting.copy(
                startTime = changedStart,
                endTime = changedStart + 2.hours,
                recurrence = MeetingEntity.RecurrenceEntity(
                    frequency = MeetingEntity.RecurrenceEntity.Frequency.WEEKLY,
                    interval = 1,
                    until = changedStart.plusCalendarDays(28)
                )
            )
            val expectedFirstChangedStart = changedStart.plusCalendarDays(7)

            meetingDao.upsertMeetings(listOf(changedMeeting))

            val updatedOccurrences = occurrencesFor(changedMeeting)
            val updatedFutureOccurrences = updatedOccurrences.filter { it.occurrence_start > now }
            assertTrue(updatedOccurrences.any { it.occurrence_id == startedOccurrenceId })
            assertTrue(updatedOccurrences.none { it.occurrence_id in oldFutureOccurrenceIds })
            assertTrue(updatedFutureOccurrences.any { it.occurrence_start == expectedFirstChangedStart })
            assertTrue(
                updatedFutureOccurrences.all {
                    it.occurrence_end == it.occurrence_start + 2.hours
                }
            )
        }

    @Test
    fun givenMixedBatch_whenOnlyOneMeetingScheduleChanges_thenOnlyChangedMeetingFutureOccurrencesAreReplaced() =
        runTest(dispatcher) {
            val now = nowAtStoredPrecision()
            val unchangedMeeting = newMeeting(
                meetingId = QualifiedIDEntity("unchanged-meeting", "wire.com"),
                conversationId = QualifiedIDEntity("unchanged-conversation", "wire.com"),
                startTime = now + 1.days,
                recurrence = MeetingEntity.RecurrenceEntity(
                    frequency = MeetingEntity.RecurrenceEntity.Frequency.DAILY,
                    interval = 1,
                    until = now + 10.days
                )
            )
            val changedMeeting = newMeeting(
                meetingId = QualifiedIDEntity("changed-meeting", "wire.com"),
                conversationId = QualifiedIDEntity("changed-conversation", "wire.com"),
                startTime = now - 2.days,
                recurrence = MeetingEntity.RecurrenceEntity(
                    frequency = MeetingEntity.RecurrenceEntity.Frequency.DAILY,
                    interval = 1,
                    until = now + 10.days
                )
            )
            insertMeetingDependencies(unchangedMeeting)
            insertMeetingDependencies(changedMeeting)
            meetingDao.upsertMeetings(listOf(unchangedMeeting, changedMeeting))
            val unchangedOccurrenceIds = occurrencesFor(unchangedMeeting).map { it.occurrence_id }
            val changedFutureOccurrenceIds = occurrencesFor(changedMeeting)
                .filter { it.occurrence_start > now }
                .map { it.occurrence_id }
                .toSet()
            assertTrue(changedFutureOccurrenceIds.isNotEmpty())

            val changedMeetingWithWeeklyRecurrence = changedMeeting.copy(
                recurrence = MeetingEntity.RecurrenceEntity(
                    frequency = MeetingEntity.RecurrenceEntity.Frequency.WEEKLY,
                    interval = 1,
                    until = now + 28.days
                )
            )

            meetingDao.upsertMeetings(listOf(unchangedMeeting, changedMeetingWithWeeklyRecurrence))

            assertContentEquals(
                unchangedOccurrenceIds,
                occurrencesFor(unchangedMeeting).map { it.occurrence_id }
            )
            assertTrue(
                occurrencesFor(changedMeetingWithWeeklyRecurrence).none {
                    it.occurrence_id in changedFutureOccurrenceIds
                }
            )
        }

    private suspend fun insertMeetingDependencies(meeting: MeetingEntity) {
        databaseBuilder.userDAO.upsertUser(newUserEntity(meeting.creatorId))
        databaseBuilder.conversationDAO.insertConversation(newConversationEntity(meeting.conversationId))
    }

    private fun occurrencesFor(meeting: MeetingEntity) =
        meetingsQueries.selectMeetingOccurrencesByMeetingId(meeting.meetingId).executeAsList()

    @Suppress("LongParameterList")
    private fun newMeeting(
        startTime: Instant,
        endTime: Instant = startTime + 1.hours,
        recurrence: MeetingEntity.RecurrenceEntity?,
        meetingId: QualifiedIDEntity = MEETING_ID,
        conversationId: QualifiedIDEntity = CONVERSATION_ID,
        creatorId: QualifiedIDEntity = CREATOR_ID
    ) = MeetingEntity(
        meetingId = meetingId,
        conversationId = conversationId,
        creatorId = creatorId,
        createdAt = startTime,
        updatedAt = null,
        title = "Meeting",
        startTime = startTime,
        endTime = endTime,
        trial = false,
        recurrence = recurrence
    )

    private fun Instant.plusCalendarDays(days: Int): Instant =
        plus(DateTimePeriod(days = days), TimeZone.currentSystemDefault())

    private fun nowAtStoredPrecision(): Instant =
        Instant.fromEpochMilliseconds(Clock.System.now().toEpochMilliseconds())

    private companion object {
        private val SELF_USER_ID = UserIDEntity("self", "wire.com")
        private val MEETING_ID = QualifiedIDEntity("meeting", "wire.com")
        private val CONVERSATION_ID = QualifiedIDEntity("conversation", "wire.com")
        private val CREATOR_ID = QualifiedIDEntity("creator", "wire.com")
        private const val OCCURRENCE_GENERATION_WINDOW_DAYS = 90
    }
}
