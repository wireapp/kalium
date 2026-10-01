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

import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitMigrate
import com.wire.kalium.persistence.BaseDatabaseTest
import com.wire.kalium.persistence.MeetingsQueries
import com.wire.kalium.persistence.UserDatabase
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.dao.meeting.MeetingEntity.RecurrenceEntity.Frequency
import com.wire.kalium.persistence.dao.meeting.MeetingOccurrencesGenerator.GenerationLimit
import com.wire.kalium.persistence.db.UserDatabaseBuilder
import com.wire.kalium.persistence.utils.stubs.newConversationEntity
import com.wire.kalium.persistence.utils.stubs.newUserEntity
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.first
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

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
    fun changingOnlyTzidRegeneratesOccurrencesAndSurvivesReopen() = runTest(dispatcher) {
        val meeting = newMeeting(
            startTime = Instant.parse("2026-10-24T08:00:00Z"),
            tzid = "Europe/Berlin",
            recurrence = MeetingEntity.RecurrenceEntity(Frequency.DAILY, 1, null)
        )
        val window = GenerationLimit.Window(meeting.startTime, Instant.parse("2026-10-26T10:00:00Z"))
        insertMeetingDependencies(meeting)
        meetingDao.upsertMeetings(listOf(meeting), window)
        assertContentEquals(
            listOf("2026-10-24T08:00:00Z", "2026-10-25T09:00:00Z", "2026-10-26T09:00:00Z").map(Instant::parse),
            occurrencesFor(meeting).map { it.occurrence_start }
        )
        val edited = meeting.copy(tzid = "Asia/Kolkata")
        meetingDao.upsertMeetings(listOf(edited), window)
        val starts = listOf("2026-10-24T08:00:00Z", "2026-10-25T08:00:00Z", "2026-10-26T08:00:00Z").map(Instant::parse)
        assertContentEquals(starts, occurrencesFor(meeting).map { it.occurrence_start })
        databaseBuilder.close()
        databaseBuilder = createDatabase(SELF_USER_ID, encryptedDBSecret, enableWAL = true)
        meetingDao = databaseBuilder.meetingDao
        meetingsQueries = databaseBuilder.database.meetingsQueries
        assertEquals(edited.copy(updatedAt = edited.createdAt), meetingDao.getMeeting(meeting.meetingId))
        assertContentEquals(starts, occurrencesFor(meeting).map { it.occurrence_start })
    }

    @Test
    fun legacyRecurringCacheUpgradeRegeneratesAfterUnchangedSyncAndPreservesOneTimeOccurrence() = runTest(dispatcher) {
        val series = newMeeting(
            startTime = Instant.parse("2026-10-24T08:00:00Z"),
            endTime = Instant.parse("2026-10-24T09:00:00Z"),
            tzid = "Europe/Berlin",
            recurrence = MeetingEntity.RecurrenceEntity(Frequency.DAILY, 1, null),
        )
        val oneTime = newMeeting(meetingId = QualifiedIDEntity("one-time", "wire.com"), startTime = series.startTime)
        val window = GenerationLimit.Window(series.startTime, Instant.parse("2026-10-26T10:00:00Z"))
        insertMeetingDependencies(series)
        meetingDao.upsertMeetings(listOf(series, oneTime), window)
        val oneTimeOccurrences = occurrencesFor(oneTime)
        meetingsQueries.deleteMeetingOccurrences(series.meetingId)
        repeat(3) { day ->
            meetingsQueries.insertMeetingOccurrence(
                "legacy-$day", series.meetingId, series.startTime + day.days, series.endTime + day.days,
            )
        }

        // This local migration must also repair databases that already applied upstream's TZID migration.
        UserDatabase.Schema.awaitMigrate(databaseBuilder.sqlDriver, 150, UserDatabase.Schema.version)
        assertEquals(emptyList(), occurrencesFor(series))
        assertEquals(oneTimeOccurrences, occurrencesFor(oneTime))
        meetingDao.upsertMeetings(listOf(series, oneTime), window)
        meetingDao.insertMissingOccurrences(GenerationLimit.Window(series.startTime, Instant.parse("2026-10-28T10:00:00Z")))
        val regenerated = occurrencesFor(series)
        assertContentEquals(
            listOf("2026-10-24T08:00:00Z", "2026-10-25T09:00:00Z", "2026-10-26T09:00:00Z",
                "2026-10-27T09:00:00Z", "2026-10-28T09:00:00Z").map(Instant::parse),
            regenerated.map { it.occurrence_start },
        )
        assertEquals(series.copy(updatedAt = series.createdAt), meetingDao.getMeeting(series.meetingId))
        databaseBuilder.close()
        databaseBuilder = createDatabase(SELF_USER_ID, encryptedDBSecret, enableWAL = true)
        meetingDao = databaseBuilder.meetingDao
        meetingsQueries = databaseBuilder.database.meetingsQueries
        assertEquals(regenerated, occurrencesFor(series))
        assertEquals(oneTimeOccurrences, occurrencesFor(oneTime))
    }

    @Test
    fun firstOfflinePageAfterCacheUpgradeGeneratesUpcomingStartsAndCanAppend() = assertOfflinePagingAfterUpgrade(interval = 1)

    @Test
    fun firstOfflinePageAfterCacheUpgradeSupportsDefaultRecurrenceInterval() = assertOfflinePagingAfterUpgrade(interval = null)

    private fun assertOfflinePagingAfterUpgrade(interval: Long?) = runTest(dispatcher) {
        val series = newMeeting(
            startTime = Instant.parse("2026-01-01T09:00:00Z"), tzid = "Europe/Berlin",
            recurrence = MeetingEntity.RecurrenceEntity(Frequency.DAILY, interval, null),
        )
        insertMeetingDependencies(series)
        insertMeetingWithoutOccurrences(series)
        meetingsQueries.insertMeetingOccurrence(
            "legacy-upcoming", series.meetingId, Instant.parse("2026-10-25T08:00:00Z"), Instant.parse("2026-10-25T09:00:00Z"),
        )
        UserDatabase.Schema.awaitMigrate(databaseBuilder.sqlDriver, 150, UserDatabase.Schema.version)

        // Load locally before any meeting sync. Starts overlapping the list boundary must not consume its count.
        val pagingSource = meetingDao.getPaginatedMeetingOccurrenceDetails(
            PagingConfig(pageSize = 3, prefetchDistance = 1), startingOffset = 0, from = Instant.parse("2026-10-24T08:30:00Z"),
        ).pagingSource
        val first = assertIs<PagingSource.LoadResult.Page<Int, MeetingOccurrenceDetailsEntity>>(
            pagingSource.load(PagingSource.LoadParams.Refresh(key = null, loadSize = 3, placeholdersEnabled = false)),
        )
        assertContentEquals(
            listOf("2026-10-25T09:00:00Z", "2026-10-26T09:00:00Z", "2026-10-27T09:00:00Z").map(Instant::parse),
            first.data.map { it.occurrence.occurrenceStart },
        )
        assertEquals(3, first.nextKey)
        assertEquals(1, first.itemsAfter)
        val next = assertIs<PagingSource.LoadResult.Page<Int, MeetingOccurrenceDetailsEntity>>(
            pagingSource.load(PagingSource.LoadParams.Append(key = checkNotNull(first.nextKey), loadSize = 3, placeholdersEnabled = false)),
        )
        assertContentEquals(
            listOf("2026-10-28T09:00:00Z", "2026-10-29T09:00:00Z", "2026-10-30T09:00:00Z").map(Instant::parse),
            next.data.map { it.occurrence.occurrenceStart },
        )
    }

    @Test
    fun directOfflineLookupAfterCacheUpgradeFindsOngoingAndFutureOccurrencesWithoutPagingOrSync() = runTest(dispatcher) {
        val dates = listOf(
            Triple(Frequency.DAILY, "2026-10-25T09:00:00Z", "2026-10-26T09:00:00Z"),
            Triple(Frequency.WEEKLY, "2026-10-29T09:00:00Z", "2026-11-05T09:00:00Z"),
        )
        insertMeetingDependencies(newMeeting())
        for ((frequency, ongoingStart, futureStart) in dates) {
            for (interval in listOf(1L, null)) {
                val ongoing = Instant.parse(ongoingStart)
                val requests = listOf(ongoing + 30.minutes to ongoing, ongoing + 1.hours to Instant.parse(futureStart))
                for ((index, request) in requests.withIndex()) {
                    val series = newMeeting(
                        meetingId = QualifiedIDEntity("lookup-$frequency-$interval-$index", "wire.com"),
                        startTime = Instant.parse("2026-01-01T09:00:00Z"), tzid = "Europe/Berlin",
                        recurrence = MeetingEntity.RecurrenceEntity(frequency, interval, null),
                    )
                    insertMeetingWithoutOccurrences(series)
                    meetingsQueries.insertMeetingOccurrence("legacy-${series.meetingId.value}", series.meetingId, ongoing, ongoing + 1.hours)
                    UserDatabase.Schema.awaitMigrate(databaseBuilder.sqlDriver, 150, UserDatabase.Schema.version)

                    val id = checkNotNull(meetingDao.getNextUnfinishedMeetingOccurrenceDetailsId(series.meetingId, request.first))
                    val occurrence = checkNotNull(meetingDao.getMeetingOccurrenceDetailsFlow(id).first()).occurrence
                    assertEquals(request.second, occurrence.occurrenceStart)
                    assertEquals(request.second + 1.hours, occurrence.occurrenceEnd)
                    assertEquals(id, meetingDao.getNextUnfinishedMeetingOccurrenceDetailsId(series.meetingId, request.first))
                    assertEquals(1, occurrencesFor(series).size)
                }
            }
        }
    }

    @Test
    fun retainedOneTimeRowsDoNotHideEarlierRecurringOccurrencesAfterUpgrade() = runTest(dispatcher) {
        val series = newMeeting(
            startTime = Instant.parse("2026-10-24T08:00:00Z"),
            recurrence = MeetingEntity.RecurrenceEntity(Frequency.DAILY, 1, null),
        )
        insertMeetingDependencies(series)
        insertMeetingWithoutOccurrences(series)
        val oneTimeMeetings = (0..3).map {
            newMeeting(meetingId = QualifiedIDEntity("one-time-$it", "wire.com"), startTime = Instant.parse("2026-11-01T09:00:00Z") + it.days)
        }
        meetingDao.upsertMeetings(
            oneTimeMeetings, GenerationLimit.Window(Instant.parse("2026-10-25T00:00:00Z"), Instant.parse("2026-11-10T00:00:00Z")),
        )
        val preserved = oneTimeMeetings.associate { it.meetingId to occurrencesFor(it) }
        UserDatabase.Schema.awaitMigrate(databaseBuilder.sqlDriver, 150, UserDatabase.Schema.version)

        val page = firstLocalPage(Instant.parse("2026-10-25T00:00:00Z"))
        assertContentEquals(
            listOf("2026-10-25T09:00:00Z", "2026-10-26T09:00:00Z", "2026-10-27T09:00:00Z").map(Instant::parse),
            page.data.map { it.occurrence.occurrenceStart },
        )
        for (meeting in oneTimeMeetings) assertEquals(preserved[meeting.meetingId], occurrencesFor(meeting))
    }

    @Test
    fun lookupThenPagingAndUnchangedSyncFillEarlierDatesWithoutReplacingIds() = runTest(dispatcher) {
        val series = newMeeting(
            startTime = Instant.parse("2026-10-24T08:00:00Z"),
            recurrence = MeetingEntity.RecurrenceEntity(Frequency.DAILY, 1, null),
        )
        insertMeetingDependencies(series)
        insertMeetingWithoutOccurrences(series)
        UserDatabase.Schema.awaitMigrate(databaseBuilder.sqlDriver, 150, UserDatabase.Schema.version)
        val nextId = checkNotNull(meetingDao.getNextUnfinishedMeetingOccurrenceDetailsId(series.meetingId, Instant.parse("2026-10-26T12:00:00Z")))
        assertEquals(Instant.parse("2026-10-27T09:00:00Z"), occurrencesFor(series).single().occurrence_start)

        val page = firstLocalPage(Instant.parse("2026-10-26T00:00:00Z"))
        assertContentEquals(
            listOf("2026-10-26T09:00:00Z", "2026-10-27T09:00:00Z", "2026-10-28T09:00:00Z").map(Instant::parse),
            page.data.map { it.occurrence.occurrenceStart },
        )
        assertEquals(nextId, page.data[1].occurrence.occurrenceId)
        val cached = occurrencesFor(series)
        val window = GenerationLimit.Window(Instant.parse("2026-10-25T00:00:00Z"), Instant.parse("2026-10-28T00:00:00Z"))
        meetingDao.upsertMeetings(listOf(series), window)
        meetingDao.insertMissingOccurrences(window)
        val completed = occurrencesFor(series)
        assertEquals(Instant.parse("2026-10-25T09:00:00Z"), completed.first().occurrence_start)
        assertEquals(cached, completed.drop(1))
        val ongoingId = meetingDao.getNextUnfinishedMeetingOccurrenceDetailsId(series.meetingId, Instant.parse("2026-10-26T09:30:00Z"))
        assertEquals(cached.first().occurrence_id, ongoingId)
        assertEquals(completed, occurrencesFor(series))
    }

    @Test
    fun pagingThenLookupFindsAnOvernightOngoingOccurrenceBeforeThePageBoundary() = runTest(dispatcher) {
        val series = newMeeting(
            startTime = Instant.parse("2026-01-01T23:00:00Z"), endTime = Instant.parse("2026-01-02T03:00:00Z"),
            recurrence = MeetingEntity.RecurrenceEntity(Frequency.DAILY, null, null),
        )
        insertMeetingDependencies(series)
        insertMeetingWithoutOccurrences(series)
        UserDatabase.Schema.awaitMigrate(databaseBuilder.sqlDriver, 150, UserDatabase.Schema.version)
        val from = Instant.parse("2026-10-25T00:00:00Z")
        val page = firstLocalPage(from)
        assertEquals(Instant.parse("2026-10-25T23:00:00Z"), page.data.first().occurrence.occurrenceStart)
        val cached = occurrencesFor(series)

        val ongoingId = checkNotNull(meetingDao.getNextUnfinishedMeetingOccurrenceDetailsId(series.meetingId, from))
        val ongoing = checkNotNull(meetingDao.getMeetingOccurrenceDetailsFlow(ongoingId).first()).occurrence
        assertEquals(Instant.parse("2026-10-24T22:00:00Z"), ongoing.occurrenceStart)
        assertEquals(Instant.parse("2026-10-25T02:00:00Z"), ongoing.occurrenceEnd)
        assertEquals(cached, occurrencesFor(series).drop(1))
        meetingDao.upsertMeetings(listOf(series), GenerationLimit.Window(from, Instant.parse("2026-10-27T04:00:00Z")))
        assertEquals(ongoingId, meetingDao.getNextUnfinishedMeetingOccurrenceDetailsId(series.meetingId, from))
        assertEquals(cached, occurrencesFor(series).drop(1))
    }

    private suspend fun firstLocalPage(from: Instant): PagingSource.LoadResult.Page<Int, MeetingOccurrenceDetailsEntity> {
        val source = meetingDao.getPaginatedMeetingOccurrenceDetails(PagingConfig(pageSize = 3, prefetchDistance = 1), 0, from).pagingSource
        return assertIs(source.load(PagingSource.LoadParams.Refresh(key = null, loadSize = 3, placeholdersEnabled = false)))
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

            meetingDao.upsertMeetings(listOf(meeting), GenerationLimit.Window(now - OUTDATED_DAYS.days, now + GENERATION_DAYS.days))

            val upperBound = Clock.System.now() + GENERATION_DAYS.days
            val occurrences = occurrencesFor(meeting)
            assertEquals(true, occurrences.isNotEmpty())
            assertEquals(true, occurrences.all { it.occurrence_start > now })
            assertEquals(true, occurrences.all { it.occurrence_start < upperBound })
        }

    @Test
    fun givenOneTimeMeeting_whenUpserted_thenOccurrenceIsGeneratedAndStored() = runTest(dispatcher) {
        val now = Clock.System.now()
        val meeting = newMeeting(startTime = now + 1.days)
        insertMeetingDependencies(meeting)

        meetingDao.upsertMeetings(listOf(meeting), GenerationLimit.Window(now - OUTDATED_DAYS.days, now + GENERATION_DAYS.days))

        val occurrence = occurrencesFor(meeting).single()
        assertEquals(meeting.meetingId, occurrence.meeting_id)
        assertEquals(meeting.startTime.toEpochMilliseconds(), occurrence.occurrence_start.toEpochMilliseconds())
        assertEquals(meeting.endTime.toEpochMilliseconds(), occurrence.occurrence_end.toEpochMilliseconds())
    }

    @Test
    fun givenRecurringMeetingStartedBeforeWindow_whenUpserted_thenOccurrencesAreGeneratedFromWindow() =
        runTest(dispatcher) {
            val now = Clock.System.now()
            val from = now - 1.days
            val until = now + 2.days
            val meeting = newMeeting(
                startTime = now - 5.days,
                recurrence = MeetingEntity.RecurrenceEntity(frequency = Frequency.DAILY, interval = 1, until = now + 5.days)
            )
            insertMeetingDependencies(meeting)

            meetingDao.upsertMeetings(listOf(meeting), GenerationLimit.Window(from, until))

            val occurrences = occurrencesFor(meeting)
            assertEquals(true, occurrences.isNotEmpty())
            assertEquals(true, occurrences.all { it.occurrence_end > from })
            assertEquals(true, occurrences.all { it.occurrence_start <= until })
        }

    @Test
    fun givenRecurringMeeting_whenGettingNextUnfinishedOccurrence_thenReturnsFirstOccurrenceThatHasNotFinished() =
        runTest(dispatcher) {
            val meetingStart = Instant.parse("2026-01-02T10:00:00Z")
            val meeting = newMeeting(
                startTime = meetingStart,
                recurrence = MeetingEntity.RecurrenceEntity(frequency = Frequency.DAILY, interval = 1, until = meetingStart + 5.days)
            )
            val otherMeeting = newMeeting(
                meetingId = QualifiedIDEntity("other-meeting", "wire.com"),
                conversationId = QualifiedIDEntity("other-conversation", "wire.com"),
                startTime = Instant.parse("2026-01-02T09:45:00Z"),
                recurrence = MeetingEntity.RecurrenceEntity(frequency = Frequency.DAILY, interval = 1, until = meetingStart + 5.days)
            )
            insertMeetingDependencies(meeting)
            insertMeetingDependencies(otherMeeting)
            meetingDao.upsertMeetings(listOf(meeting, otherMeeting), GenerationLimit.Window(meetingStart - 1.days, meetingStart + 5.days))
            val occurrenceIdsByStartTime = occurrencesFor(meeting).associate { it.occurrence_start to it.occurrence_id }

            val beforeTodaysOccurrenceStartId = meetingDao.getNextUnfinishedMeetingOccurrenceDetailsId(
                meetingId = meeting.meetingId,
                from = Instant.parse("2026-01-02T09:30:00Z")
            )
            val atTodaysOccurrenceStartId = meetingDao.getNextUnfinishedMeetingOccurrenceDetailsId(
                meetingId = meeting.meetingId,
                from = meetingStart
            )
            val whileTodaysOccurrenceIsOngoingId = meetingDao.getNextUnfinishedMeetingOccurrenceDetailsId(
                meetingId = meeting.meetingId,
                from = Instant.parse("2026-01-02T10:30:00Z")
            )
            val atTodaysOccurrenceEndId = meetingDao.getNextUnfinishedMeetingOccurrenceDetailsId(
                meetingId = meeting.meetingId,
                from = meetingStart + 1.hours
            )

            assertEquals(occurrenceIdsByStartTime.getValue(meetingStart), beforeTodaysOccurrenceStartId)
            assertEquals(occurrenceIdsByStartTime.getValue(meetingStart), atTodaysOccurrenceStartId)
            assertEquals(occurrenceIdsByStartTime.getValue(meetingStart), whileTodaysOccurrenceIsOngoingId)
            assertEquals(occurrenceIdsByStartTime.getValue(meetingStart + 1.days), atTodaysOccurrenceEndId)
        }

    @Test
    fun givenStoredMeetings_whenGettingMeeting_thenReturnsMeeting() = runTest(dispatcher) {
        val meetingStart = Instant.parse("2026-01-03T10:00:00Z")
        val meeting = newMeeting(
            meetingId = QualifiedIDEntity("meeting-to-fetch", "wire.com"),
            conversationId = QualifiedIDEntity("conversation-to-fetch", "wire.com"),
            startTime = meetingStart,
            recurrence = MeetingEntity.RecurrenceEntity(frequency = Frequency.WEEKLY, interval = 2, until = meetingStart + 28.days)
        ).copy(
            updatedAt = Instant.parse("2026-01-02T12:00:00Z"),
            title = "Fetched meeting",
        )
        val otherMeeting = newMeeting(
            meetingId = QualifiedIDEntity("other-meeting", "wire.com"),
            conversationId = QualifiedIDEntity("other-conversation", "wire.com"),
            startTime = meetingStart + 1.days
        )
        insertMeetingDependencies(meeting)
        insertMeetingDependencies(otherMeeting)
        meetingDao.upsertMeetings(
            listOf(meeting, otherMeeting),
            GenerationLimit.Window(meetingStart - 1.days, meetingStart + GENERATION_DAYS.days)
        )

        assertEquals(meeting, meetingDao.getMeeting(meeting.meetingId))
    }

    @Test
    fun givenNoStoredMeetingForId_whenGettingMeeting_thenReturnsNull() = runTest(dispatcher) {
        val meetingStart = Instant.parse("2026-01-03T10:00:00Z")
        val meeting = newMeeting(startTime = meetingStart)
        insertMeetingDependencies(meeting)
        meetingDao.upsertMeetings(
            listOf(meeting),
            GenerationLimit.Window(meetingStart - 1.days, meetingStart + GENERATION_DAYS.days)
        )

        assertEquals(null, meetingDao.getMeeting(QualifiedIDEntity("missing-meeting", "wire.com")))
    }

    @Test
    fun givenGeneratedOccurrences_whenSameMeetingIsUpserted_thenOccurrencesAreNotDuplicated() = runTest(dispatcher) {
        val now = Clock.System.now()
        val meeting = newMeeting(
            startTime = now + 1.days,
            recurrence = MeetingEntity.RecurrenceEntity(frequency = Frequency.DAILY, interval = 1, until = (now + 10.days))
        )
        insertMeetingDependencies(meeting)
        meetingDao.upsertMeetings(listOf(meeting), GenerationLimit.Window(now - OUTDATED_DAYS.days, now + GENERATION_DAYS.days))
        val firstOccurrences = occurrencesFor(meeting)

        meetingDao.upsertMeetings(listOf(meeting), GenerationLimit.Window(now - OUTDATED_DAYS.days, now + GENERATION_DAYS.days))

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
            meetingDao.upsertMeetings(listOf(oldMeeting), GenerationLimit.Window(now - OUTDATED_DAYS.days, now + GENERATION_DAYS.days))
            val oldOccurrenceIds = occurrencesFor(oldMeeting).toSet()
            assertEquals(true, oldOccurrenceIds.isNotEmpty())

            val changedStart = now + 1.days
            val changedMeeting = oldMeeting.copy(
                startTime = changedStart,
                endTime = changedStart + 2.hours,
                recurrence = MeetingEntity.RecurrenceEntity(frequency = Frequency.WEEKLY, interval = 1, until = changedStart.plus(28.days))
            )
            meetingDao.upsertMeetings(listOf(changedMeeting), GenerationLimit.Window(now - OUTDATED_DAYS.days, now + GENERATION_DAYS.days))

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
            meetingDao.upsertMeetings(
                listOf(unchangedMeeting, changedMeeting),
                GenerationLimit.Window(now - OUTDATED_DAYS.days, now + GENERATION_DAYS.days)
            )
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
                GenerationLimit.Window(now - OUTDATED_DAYS.days, now + GENERATION_DAYS.days)
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
            meetingDao.upsertMeetings(listOf(meeting), GenerationLimit.Window(Instant.DISTANT_PAST, now + GENERATION_DAYS.days))
            assertEquals(true, occurrencesFor(meeting).isNotEmpty())

            meetingDao.removeOutdatedMeetings(now - OUTDATED_DAYS.days)

            assertEquals(false, isMeetingStored(meeting))
            assertEquals(true, occurrencesFor(meeting).isEmpty())
        }

    @Test
    fun givenOneTimeMeetingWithRecentEndDate_whenRemovingOutdatedMeetings_thenMeetingIsKept() =
        runTest(dispatcher) {
            val now = Clock.System.now()
            val meeting = newMeeting(startTime = now - OUTDATED_DAYS.days, endTime = now - OUTDATED_DAYS.days + 1.hours)
            insertMeetingDependencies(meeting)
            meetingDao.upsertMeetings(listOf(meeting), GenerationLimit.Window(now - OUTDATED_DAYS.days, now + GENERATION_DAYS.days))

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
            meetingDao.upsertMeetings(listOf(meeting),GenerationLimit.Window(Instant.DISTANT_PAST, now + GENERATION_DAYS.days))
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
            meetingDao.upsertMeetings(listOf(meeting), GenerationLimit.Window(now - OUTDATED_DAYS.days, now + GENERATION_DAYS.days))

            meetingDao.removeOutdatedMeetings(now - OUTDATED_DAYS.days)
            assertEquals(true, isMeetingStored(meeting))
        }

    @Test
    fun givenRecurringMeetingWithOldOccurrences_whenRemovingOutdatedMeetings_thenOutdatedOccurrencesAreDeleted() =
        runTest(dispatcher) {
            val now = Clock.System.now()
            val outdatedThreshold = now - 1.days
            val meeting = newMeeting(
                startTime = now - 3.days,
                recurrence = MeetingEntity.RecurrenceEntity(frequency = Frequency.DAILY, interval = 1, until = now + 3.days)
            )
            insertMeetingDependencies(meeting)
            meetingDao.upsertMeetings(listOf(meeting), GenerationLimit.Window(Instant.DISTANT_PAST, now + GENERATION_DAYS.days))
            val initialOccurrences = occurrencesFor(meeting)
            assertEquals(true, initialOccurrences.any { it.occurrence_end <= outdatedThreshold })

            meetingDao.removeOutdatedMeetings(outdatedThreshold)

            val remainingOccurrences = occurrencesFor(meeting)
            assertEquals(true, isMeetingStored(meeting))
            assertEquals(true, remainingOccurrences.isNotEmpty())
            assertEquals(true, remainingOccurrences.all { it.occurrence_end > outdatedThreshold })
        }

    @Test
    fun givenRecurringMeetingWithMissingOccurrences_whenInsertingMissing_thenWindowIsToppedUpWithoutDuplicates() =
        runTest(dispatcher) {
            val now = Clock.System.now()
            val from = now - OUTDATED_DAYS.days
            val meeting = newMeeting(
                startTime = now + 1.days,
                recurrence = MeetingEntity.RecurrenceEntity(frequency = Frequency.DAILY, interval = 1, until = now + 120.days)
            )
            insertMeetingDependencies(meeting)
            insertMeetingWithoutOccurrences(meeting)

            meetingDao.insertMissingOccurrences(GenerationLimit.Window(from = from, until = now + GENERATION_DAYS.days))

            val initialOccurrences = occurrencesFor(meeting)
            assertEquals(true, initialOccurrences.isNotEmpty())
            assertEquals(true, initialOccurrences.all { it.occurrence_start <= now + GENERATION_DAYS.days })

            meetingDao.insertMissingOccurrences(GenerationLimit.Window(from = from, until = now + GENERATION_DAYS.days))
            assertContentEquals(initialOccurrences.map { it.occurrence_id }, occurrencesFor(meeting).map { it.occurrence_id })

            meetingDao.insertMissingOccurrences(GenerationLimit.Window(from = from, until = now + GENERATION_DAYS.days + 1.days))
            assertEquals(initialOccurrences.size + 1, occurrencesFor(meeting).size)
        }

    @Test
    fun givenRecurringMeetingWithoutOccurrences_whenInsertingMissing_thenStartsFromWindow() =
        runTest(dispatcher) {
            val now = Clock.System.now()
            val from = now - 1.days
            val until = now + 2.days
            val meeting = newMeeting(
                startTime = now - 5.days,
                recurrence = MeetingEntity.RecurrenceEntity(frequency = Frequency.DAILY, interval = 1, until = now + 5.days)
            )
            insertMeetingDependencies(meeting)
            insertMeetingWithoutOccurrences(meeting)

            meetingDao.insertMissingOccurrences(GenerationLimit.Window(from = from, until = until))

            val occurrences = occurrencesFor(meeting)
            assertEquals(true, occurrences.isNotEmpty())
            assertEquals(true, occurrences.all { it.occurrence_end > from })
            assertEquals(true, occurrences.all { it.occurrence_start <= until })
        }

    @Test
    fun givenStoredMeeting_whenDeletingMeeting_thenMeetingAndOccurrencesAreDeleted() = runTest(dispatcher) {
        val now = Clock.System.now()
        val meeting = newMeeting(
            startTime = now + 1.days,
            recurrence = MeetingEntity.RecurrenceEntity(frequency = Frequency.DAILY, interval = 1, until = now + 5.days)
        )
        val otherMeeting = newMeeting(
            meetingId = QualifiedIDEntity("other-meeting", "wire.com"),
            conversationId = QualifiedIDEntity("other-conversation", "wire.com"),
            startTime = now + 1.days
        )
        insertMeetingDependencies(meeting)
        insertMeetingDependencies(otherMeeting)
        meetingDao.upsertMeetings(listOf(meeting, otherMeeting), GenerationLimit.Window(now, now + GENERATION_DAYS.days))
        assertEquals(true, isMeetingStored(meeting))
        assertEquals(true, occurrencesFor(meeting).isNotEmpty())

        meetingDao.deleteMeeting(meeting.meetingId)

        assertEquals(false, isMeetingStored(meeting))
        assertEquals(true, occurrencesFor(meeting).isEmpty())
        assertEquals(true, isMeetingStored(otherMeeting))
        assertEquals(true, occurrencesFor(otherMeeting).isNotEmpty())
    }

    @Test
    fun givenStoredMeetings_whenUpsertingWithRemoveAbsentEnabled_thenMeetingsAbsentFromUpsertListAreDeleted() = runTest(dispatcher) {
        val now = Clock.System.now()
        val meetingToKeep = newMeeting(
            meetingId = QualifiedIDEntity("meeting-to-keep", "wire.com"),
            conversationId = QualifiedIDEntity("conversation-to-keep", "wire.com"),
            startTime = now + 1.days
        )
        val meetingToRemove = newMeeting(
            meetingId = QualifiedIDEntity("meeting-to-remove", "wire.com"),
            conversationId = QualifiedIDEntity("conversation-to-remove", "wire.com"),
            startTime = now + 2.days
        )
        insertMeetingDependencies(meetingToKeep)
        insertMeetingDependencies(meetingToRemove)
        meetingDao.upsertMeetings(
            listOf(meetingToKeep, meetingToRemove),
            GenerationLimit.Window(now, now + GENERATION_DAYS.days)
        )
        assertEquals(true, occurrencesFor(meetingToKeep).isNotEmpty())
        assertEquals(true, occurrencesFor(meetingToRemove).isNotEmpty())

        val updatedMeetingToKeep = meetingToKeep.copy(title = "Updated meeting")
        meetingDao.upsertMeetings(
            meetings = listOf(updatedMeetingToKeep),
            generateOccurrencesWindow = GenerationLimit.Window(now, now + GENERATION_DAYS.days),
            removeMeetingsAbsentFromUpsertList = true
        )

        val storedMeetingToKeep = meetingDao.getMeeting(meetingToKeep.meetingId)
        assertEquals(meetingToKeep.meetingId, storedMeetingToKeep?.meetingId)
        assertEquals("Updated meeting", storedMeetingToKeep?.title)
        assertEquals(true, occurrencesFor(updatedMeetingToKeep).isNotEmpty())
        assertEquals(false, isMeetingStored(meetingToRemove))
        assertEquals(true, occurrencesFor(meetingToRemove).isEmpty())
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
            tzid = meeting.tzid,
            recurrence_frequency = meeting.recurrence?.frequency,
            recurrence_interval = meeting.recurrence?.interval,
            recurrence_end_date = meeting.recurrence?.until
        )
    }

    private suspend fun isMeetingStored(meeting: MeetingEntity): Boolean =
        meetingsQueries.selectMeetingByIds(listOf(meeting.meetingId)).awaitAsList().isNotEmpty()

    private suspend fun occurrencesFor(meeting: MeetingEntity) =
        meetingsQueries.selectMeetingOccurrencesByMeetingId(meeting.meetingId).awaitAsList()
}

fun newMeeting(
    startTime: Instant = Instant.parse("2026-01-01T10:00:00Z"),
    endTime: Instant = startTime + 1.hours,
    tzid: String = "Europe/Berlin",
    recurrence: MeetingEntity.RecurrenceEntity? = null,
    meetingId: QualifiedIDEntity = MEETING_ID,
    conversationId: QualifiedIDEntity = CONVERSATION_ID,
    creatorId: QualifiedIDEntity = CREATOR_ID,
) = MeetingEntity(
    meetingId = meetingId,
    conversationId = conversationId,
    creatorId = creatorId,
    createdAt = startTime,
    updatedAt = null,
    title = "Meeting",
    startTime = startTime,
    endTime = endTime,
    tzid = tzid,
    recurrence = recurrence
)
private val SELF_USER_ID = UserIDEntity("self", "wire.com")
private val MEETING_ID = QualifiedIDEntity("meeting", "wire.com")
private val CONVERSATION_ID = QualifiedIDEntity("conversation", "wire.com")
private val CREATOR_ID = QualifiedIDEntity("creator", "wire.com")
private const val GENERATION_DAYS = 90
private const val OUTDATED_DAYS = 30
