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
import com.wire.kalium.persistence.dao.meeting.MeetingEntity.RecurrenceEntity.Frequency
import com.wire.kalium.persistence.db.UserDatabaseBuilder
import com.wire.kalium.persistence.utils.stubs.newConversationEntity
import com.wire.kalium.persistence.utils.stubs.newUserEntity
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
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
            val now = Clock.System.now()
            val meeting = newMeeting(
                startTime = now + 1.days,
                recurrence = MeetingEntity.RecurrenceEntity(frequency = Frequency.DAILY, interval = 1, until = now + 120.days)
            )
            insertMeetingDependencies(meeting)

            meetingDao.upsertMeetings(listOf(meeting), now + GENERATION_DAYS.days)

            val upperBound = Clock.System.now() + GENERATION_DAYS.days
            val occurrences = occurrencesFor(meeting)
            assertEquals(true, occurrences.isNotEmpty())
            assertEquals(true, occurrences.all { it.occurrence_start > now })
            assertEquals(true, occurrences.all { it.occurrence_start <= upperBound })
        }

    @Test
    fun givenGeneratedOccurrences_whenSameMeetingIsUpserted_thenOccurrencesAreNotDuplicated() = runTest(dispatcher) {
        val now = Clock.System.now()
        val meeting = newMeeting(
            startTime = now + 1.days,
            recurrence = MeetingEntity.RecurrenceEntity(frequency = Frequency.DAILY, interval = 1, until = (now + 10.days))
        )
        insertMeetingDependencies(meeting)
        meetingDao.upsertMeetings(listOf(meeting), now + GENERATION_DAYS.days)
        val firstOccurrences = occurrencesFor(meeting)

        meetingDao.upsertMeetings(listOf(meeting), now + GENERATION_DAYS.days)

        val secondOccurrences = occurrencesFor(meeting)
        assertEquals(firstOccurrences.size, secondOccurrences.size)
        assertContentEquals(firstOccurrences.map { it.occurrence_id }, secondOccurrences.map { it.occurrence_id })
        assertContentEquals(firstOccurrences.map { it.occurrence_start }, secondOccurrences.map { it.occurrence_start })
    }

    @Test
    fun givenStoredMeeting_whenRecurrenceChanges_thenOccurrencesAreReplaced() =
        runTest(dispatcher) {
            val now = Clock.System.now()
            val oldStart = now - 3.days + 3.hours
            val oldMeeting = newMeeting(
                startTime = oldStart,
                endTime = oldStart + 1.hours,
                recurrence = MeetingEntity.RecurrenceEntity(frequency = Frequency.DAILY, interval = 1, until = oldStart.plus(14.days))
            )
            insertMeetingDependencies(oldMeeting)
            meetingDao.upsertMeetings(listOf(oldMeeting), now + GENERATION_DAYS.days)
            val oldOccurrenceIds = occurrencesFor(oldMeeting).toSet()
            assertEquals(true, oldOccurrenceIds.isNotEmpty())

            val changedStart = now + 1.days
            val changedMeeting = oldMeeting.copy(
                startTime = changedStart,
                endTime = changedStart + 2.hours,
                recurrence = MeetingEntity.RecurrenceEntity(frequency = Frequency.WEEKLY, interval = 1, until = changedStart.plus(28.days))
            )
            meetingDao.upsertMeetings(listOf(changedMeeting), now + GENERATION_DAYS.days)

            val updatedOccurrences = occurrencesFor(changedMeeting)
            assertEquals(true, updatedOccurrences != oldOccurrenceIds)
            assertEquals(changedStart.epochSeconds, updatedOccurrences.first().occurrence_start.epochSeconds)
        }

    @Test
    fun givenMixedBatch_whenOnlyOneMeetingScheduleChanges_thenOnlyChangedMeetingFutureOccurrencesAreReplaced() =
        runTest(dispatcher) {
            val now = Clock.System.now()
            val unchangedMeeting = newMeeting(
                meetingId = QualifiedIDEntity("unchanged-meeting", "wire.com"),
                conversationId = QualifiedIDEntity("unchanged-conversation", "wire.com"),
                startTime = now + 1.days,
                recurrence = MeetingEntity.RecurrenceEntity(frequency = Frequency.DAILY, interval = 1, until = now + 10.days)
            )
            val changedMeeting = newMeeting(
                meetingId = QualifiedIDEntity("changed-meeting", "wire.com"),
                conversationId = QualifiedIDEntity("changed-conversation", "wire.com"),
                startTime = now - 2.days,
                recurrence = MeetingEntity.RecurrenceEntity(frequency = Frequency.DAILY, interval = 1, until = now + 10.days)
            )
            insertMeetingDependencies(unchangedMeeting)
            insertMeetingDependencies(changedMeeting)
            meetingDao.upsertMeetings(listOf(unchangedMeeting, changedMeeting), now + GENERATION_DAYS.days)
            val unchangedOccurrenceIds = occurrencesFor(unchangedMeeting).map { it.occurrence_id }
            val changedFutureOccurrenceIds = occurrencesFor(changedMeeting)
                .filter { it.occurrence_start > now }
                .map { it.occurrence_id }
                .toSet()
            assertEquals(true, changedFutureOccurrenceIds.isNotEmpty())

            val changedMeetingWithWeeklyRecurrence = changedMeeting.copy(
                recurrence = MeetingEntity.RecurrenceEntity(frequency = Frequency.WEEKLY, interval = 1, until = now + 28.days)
            )

            meetingDao.upsertMeetings(
                listOf(unchangedMeeting, changedMeetingWithWeeklyRecurrence),
                now + GENERATION_DAYS.days
            )

            assertContentEquals(unchangedOccurrenceIds, occurrencesFor(unchangedMeeting).map { it.occurrence_id })
            assertEquals(true, occurrencesFor(changedMeetingWithWeeklyRecurrence).none { it.occurrence_id in changedFutureOccurrenceIds })
        }

    @Test
    fun givenOneTimeMeetingWithOldEndDate_whenRemovingOutdatedMeetings_thenMeetingAndOccurrencesAreDeleted() =
        runTest(dispatcher) {
            val now = Clock.System.now()
            val meeting = newMeeting(startTime = now - OUTDATED_DAYS.days - 1.hours, endTime = now - OUTDATED_DAYS.days, recurrence = null)
            insertMeetingDependencies(meeting)
            meetingDao.upsertMeetings(listOf(meeting), now + GENERATION_DAYS.days)
            assertEquals(true, occurrencesFor(meeting).isNotEmpty())

            meetingDao.removeOutdatedMeetings(now - OUTDATED_DAYS.days)

            assertEquals(false, isMeetingStored(meeting))
            assertEquals(true, occurrencesFor(meeting).isEmpty())
        }

    @Test
    fun givenOneTimeMeetingWithoutEndDate_whenRemovingOutdatedMeetings_thenMeetingIsKept() =
        runTest(dispatcher) {
            val now = Clock.System.now()
            val meeting = newMeeting(startTime = now - OUTDATED_DAYS.days - 1.days, endTime = null, recurrence = null)
            insertMeetingDependencies(meeting)
            meetingDao.upsertMeetings(listOf(meeting), now + GENERATION_DAYS.days)

            meetingDao.removeOutdatedMeetings(now - OUTDATED_DAYS.days)

            assertEquals(true, isMeetingStored(meeting))
        }

    @Test
    fun givenRecurringMeetingWithOldUntilDate_whenRemovingOutdatedMeetings_thenMeetingAndOccurrencesAreDeleted() =
        runTest(dispatcher) {
            val now = Clock.System.now()
            val meeting = newMeeting(
                startTime = now - OUTDATED_DAYS.days - 5.days,
                recurrence = MeetingEntity.RecurrenceEntity(frequency = Frequency.DAILY, interval = 1, until = now - OUTDATED_DAYS.days)
            )
            insertMeetingDependencies(meeting)
            meetingDao.upsertMeetings(listOf(meeting), now + GENERATION_DAYS.days)
            assertEquals(true, occurrencesFor(meeting).isNotEmpty())

            meetingDao.removeOutdatedMeetings(now - OUTDATED_DAYS.days)
            assertEquals(false, isMeetingStored(meeting))
            assertEquals(true, occurrencesFor(meeting).isEmpty())
        }

    @Test
    fun givenRecurringMeetingWithoutUntilDate_whenRemovingOutdatedMeetings_thenMeetingIsKept() =
        runTest(dispatcher) {
            val now = Clock.System.now()
            val meeting = newMeeting(
                startTime = now - 1.days,
                recurrence = MeetingEntity.RecurrenceEntity(frequency = Frequency.DAILY, interval = 1, until = null)
            )
            insertMeetingDependencies(meeting)
            meetingDao.upsertMeetings(listOf(meeting), now + GENERATION_DAYS.days)

            meetingDao.removeOutdatedMeetings(now - OUTDATED_DAYS.days)
            assertEquals(true, isMeetingStored(meeting))
        }

    @Test
    fun givenRecurringMeetingWithMissingOccurrences_whenInsertingMissing_thenWindowIsToppedUpWithoutDuplicates() =
        runTest(dispatcher) {
            val now = Clock.System.now()
            val meeting = newMeeting(
                startTime = now + 1.days,
                recurrence = MeetingEntity.RecurrenceEntity(frequency = Frequency.DAILY, interval = 1, until = now + 120.days)
            )
            insertMeetingDependencies(meeting)
            insertMeetingWithoutOccurrences(meeting)

            meetingDao.insertMissingOccurrences(now + GENERATION_DAYS.days)

            val initialOccurrences = occurrencesFor(meeting)
            assertEquals(true, initialOccurrences.isNotEmpty())
            assertEquals(true, initialOccurrences.all { it.occurrence_start <= now + GENERATION_DAYS.days })

            meetingDao.insertMissingOccurrences(now + GENERATION_DAYS.days)
            assertContentEquals(initialOccurrences.map { it.occurrence_id }, occurrencesFor(meeting).map { it.occurrence_id })

            meetingDao.insertMissingOccurrences(now + GENERATION_DAYS.days + 1.days)
            assertEquals(initialOccurrences.size + 1, occurrencesFor(meeting).size)
        }

    private suspend fun insertMeetingDependencies(meeting: MeetingEntity) {
        databaseBuilder.userDAO.upsertUser(newUserEntity(meeting.creatorId))
        databaseBuilder.conversationDAO.insertConversation(newConversationEntity(meeting.conversationId))
    }

    private suspend fun insertMeetingWithoutOccurrences(meeting: MeetingEntity) {
        meetingsQueries.upsertMeeting(
            meeting_id = meeting.meetingId,
            conversation_id = meeting.conversationId,
            creator_id = meeting.creatorId,
            creation_date = meeting.createdAt,
            last_edit_date = meeting.updatedAt,
            title = meeting.title,
            start_date = meeting.startTime,
            end_date = meeting.endTime,
            trial = meeting.trial,
            recurrence_frequency = meeting.recurrence?.frequency,
            recurrence_interval = meeting.recurrence?.interval,
            recurrence_end_date = meeting.recurrence?.until
        )
    }

    private fun isMeetingStored(meeting: MeetingEntity): Boolean =
        meetingsQueries.selectMeetingByIds(listOf(meeting.meetingId)).executeAsList().isNotEmpty()

    private fun occurrencesFor(meeting: MeetingEntity) =
        meetingsQueries.selectMeetingOccurrencesByMeetingId(meeting.meetingId).executeAsList()
}

fun newMeeting(
    startTime: Instant = Instant.parse("2026-01-01T10:00:00Z"),
    endTime: Instant? = startTime + 1.hours,
    recurrence: MeetingEntity.RecurrenceEntity? = null,
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
private val SELF_USER_ID = UserIDEntity("self", "wire.com")
private val MEETING_ID = QualifiedIDEntity("meeting", "wire.com")
private val CONVERSATION_ID = QualifiedIDEntity("conversation", "wire.com")
private val CREATOR_ID = QualifiedIDEntity("creator", "wire.com")
private const val GENERATION_DAYS = 90
private const val OUTDATED_DAYS = 30
