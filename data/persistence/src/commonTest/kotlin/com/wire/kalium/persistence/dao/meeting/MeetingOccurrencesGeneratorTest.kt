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

import com.wire.kalium.persistence.dao.QualifiedIDEntity
import kotlinx.datetime.plus
import kotlin.time.Instant
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

class MeetingOccurrencesGeneratorTest {

    @Test
    fun countFromDateKeepsCadenceAcrossDstAndCanResume() {
        val daily = MeetingEntity.RecurrenceEntity.Frequency.DAILY
        val weekly = MeetingEntity.RecurrenceEntity.Frequency.WEEKLY
        val cases = listOf(
            Triple(MeetingEntity.RecurrenceEntity(daily, 1, null), "2026-10-20T08:00:00Z",
                listOf("2026-10-25T09:00:00Z", "2026-10-26T09:00:00Z")),
            Triple(MeetingEntity.RecurrenceEntity(weekly, 1, null), "2026-10-22T08:00:00Z",
                listOf("2026-10-29T09:00:00Z", "2026-11-05T09:00:00Z")),
            Triple(MeetingEntity.RecurrenceEntity(weekly, 2, null), "2026-10-22T08:00:00Z",
                listOf("2026-11-05T09:00:00Z", "2026-11-19T09:00:00Z")),
            Triple(MeetingEntity.RecurrenceEntity(weekly, 4, null), "2026-10-08T08:00:00Z",
                listOf("2026-11-05T09:00:00Z", "2026-12-03T09:00:00Z")),
        )
        val limit = MeetingOccurrencesGenerator.GenerationLimit.Count(2, from = Instant.parse("2026-10-24T08:30:00Z"))
        for ((recurrence, lastStart, starts) in cases) {
            val meeting = newMeeting(startTime = Instant.parse("2026-01-01T09:00:00Z"), tzid = "Europe/Berlin", recurrence = recurrence)
            val expected = starts.map(Instant::parse)
            assertContentEquals(expected, generateOccurrences(limit, meeting = meeting).map { it.occurrenceStart })
            assertContentEquals(
                expected,
                generateOccurrences(limit, meeting = meeting, lastGeneratedStarts = mapOf(meeting.meetingId to Instant.parse(lastStart)))
                    .map { it.occurrenceStart },
            )
            val includingOngoing = if (recurrence.frequency == daily) {
                listOf("2026-10-24T08:00:00Z", "2026-10-25T09:00:00Z").map(Instant::parse)
            } else expected
            assertContentEquals(
                includingOngoing,
                generateOccurrences(limit.copy(includeOngoing = true), meeting = meeting).map { it.occurrenceStart },
            )
            assertContentEquals(
                expected,
                generateOccurrences(limit.copy(from = Instant.parse("2026-10-24T09:00:00Z"), includeOngoing = true), meeting = meeting)
                    .map { it.occurrenceStart },
            )
        }
    }

    @Test
    fun givenCountLimit_whenGeneratingOccurrences_thenReturnsRequestedNumberOfOccurrences() {
        val occurrences = generateOccurrences(MeetingOccurrencesGenerator.GenerationLimit.Count(totalCount = 2))

        assertEquals(2, occurrences.size)
        assertContentEquals(
            listOf(MEETING.startTime, MEETING.startTime.plus(1.days)),
            occurrences.map { it.occurrenceStart }
        )
    }

    @Test
    fun givenRecurringMeetingWithoutInterval_whenGeneratingOccurrences_thenUsesDefaultInterval() {
        val meeting = newMeeting(recurrence = MeetingEntity.RecurrenceEntity(MeetingEntity.RecurrenceEntity.Frequency.DAILY, null, null))

        val occurrences = generateOccurrences(MeetingOccurrencesGenerator.GenerationLimit.Count(totalCount = 2), meeting = meeting)

        assertContentEquals(
            listOf(meeting.startTime, meeting.startTime.plus(1.days)),
            occurrences.map { it.occurrenceStart }
        )
    }

    @Test
    fun givenZeroCountLimit_whenGeneratingOccurrences_thenReturnsNoOccurrences() {
        val occurrences = generateOccurrences(MeetingOccurrencesGenerator.GenerationLimit.Count(totalCount = 0))

        assertEquals(0, occurrences.size)
    }

    @Test
    fun givenNegativeCountLimit_whenGeneratingOccurrences_thenReturnsNoOccurrences() {
        val occurrences = generateOccurrences(MeetingOccurrencesGenerator.GenerationLimit.Count(totalCount = -1))

        assertEquals(0, occurrences.size)
    }

    @Test
    fun givenWindowLimit_whenGeneratingOccurrences_thenReturnsOccurrencesUntilLimitInclusively() {
        val from = MEETING.startTime - 1.days
        val until = MEETING.startTime.plus(2.days)
        val occurrences = generateOccurrences(MeetingOccurrencesGenerator.GenerationLimit.Window(from = from, until = until))

        assertContentEquals(
            listOf(MEETING.startTime, MEETING.startTime.plus(1.days), until),
            occurrences.map { it.occurrenceStart }
        )
        assertContentEquals(
            occurrences.map { it.occurrenceStart + 1.hours },
            occurrences.map { it.occurrenceEnd }
        )
    }

    @Test
    fun givenWindowLimitWithFrom_whenGeneratingOccurrences_thenReturnsOccurrencesAfterFrom() {
        val from = MEETING.startTime.plus(1.days) + 1.hours
        val until = MEETING.startTime.plus(3.days)
        val occurrences = generateOccurrences(MeetingOccurrencesGenerator.GenerationLimit.Window(from = from, until = until))

        assertContentEquals(
            listOf(MEETING.startTime.plus(2.days), MEETING.startTime.plus(3.days)),
            occurrences.map { it.occurrenceStart }
        )
    }

    @Test
    fun givenLastGeneratedStart_whenGeneratingOccurrences_thenStartsAfterLastGeneratedOccurrence() {
        val occurrences = generateOccurrences(
            limit = MeetingOccurrencesGenerator.GenerationLimit.Count(totalCount = 2),
            lastGeneratedStarts = mapOf(MEETING.meetingId to MEETING.startTime.plus(1.days))
        )

        assertContentEquals(
            listOf(MEETING.startTime.plus(2.days), MEETING.startTime.plus(3.days)),
            occurrences.map { it.occurrenceStart }
        )
    }

    @Test
    fun givenMeetingTimezone_whenGeneratingOccurrencesAcrossDaylightSavingChange_thenKeepsTheSameLocalStartAndEndTimes() {
        val meeting = newMeeting(
            tzid = "Europe/Berlin",
            startTime = Instant.parse("2026-03-28T10:00:00+01:00"), // Before DST change: local Berlin's timezone is +1
            endTime = Instant.parse("2026-03-28T11:00:00+01:00"),
            recurrence = MeetingEntity.RecurrenceEntity(MeetingEntity.RecurrenceEntity.Frequency.DAILY, 1, null),
        )

        val occurrences = generateOccurrences(MeetingOccurrencesGenerator.GenerationLimit.Count(totalCount = 2), meeting = meeting)

        // Local times stay at 10:00-11:00 in Europe/Berlin; only the offset changes to +2 after DST starts
        assertContentEquals(
            expected = listOf(
                // Before DST change: timezone is +1, local start-end times are 10:00-11:00
                "2026-03-28T10:00:00+01:00" to "2026-03-28T11:00:00+01:00",
                // After DST change: local timezone is now +2, local start times are still 10:00-11:00
                "2026-03-29T10:00:00+02:00" to "2026-03-29T11:00:00+02:00",
            ).parseToInstants(),
            actual = occurrences.map { it.occurrenceStart to it.occurrenceEnd }
        )
    }

    @Test
    fun givenRecurringMeetingWithUnsupportedTimezone_whenGeneratingOccurrences_thenReturnsNoOccurrences() {
        val meeting = newMeeting(
            tzid = "Unsupported/TZID",
            recurrence = MeetingEntity.RecurrenceEntity(MeetingEntity.RecurrenceEntity.Frequency.DAILY, 1, null),
        )

        val occurrences = generateOccurrences(MeetingOccurrencesGenerator.GenerationLimit.Count(totalCount = 2), meeting = meeting)

        assertEquals(0, occurrences.size)
    }

    @Test
    fun givenNonRecurringMeetingWithUnsupportedTimezone_whenGeneratingOccurrences_thenReturnsOriginalOccurrence() {
        val meeting = newMeeting(tzid = "Unsupported/TZID", recurrence = null)

        val occurrences = generateOccurrences(MeetingOccurrencesGenerator.GenerationLimit.Count(totalCount = 2), meeting = meeting)

        assertContentEquals(listOf(meeting.startTime), occurrences.map { it.occurrenceStart })
    }

    @Test
    fun givenRecurringMeetingWithUnsupportedTimezoneAndLastGeneratedStart_whenGeneratingOccurrences_thenReturnsNoOccurrences() {
        val meeting = newMeeting(
            tzid = "Unsupported/TZID",
            recurrence = MeetingEntity.RecurrenceEntity(MeetingEntity.RecurrenceEntity.Frequency.DAILY, 1, null),
        )

        val occurrences = generateOccurrences(
            limit = MeetingOccurrencesGenerator.GenerationLimit.Count(totalCount = 2),
            lastGeneratedStarts = mapOf(meeting.meetingId to meeting.startTime),
            meeting = meeting
        )

        assertEquals(0, occurrences.size)
    }

    @Test
    fun givenRecurringMeetingWithUnsupportedTimezoneOutsideWindow_whenGeneratingOccurrences_thenReturnsNoOccurrences() {
        val meeting = newMeeting(
            tzid = "Unsupported/TZID",
            recurrence = MeetingEntity.RecurrenceEntity(MeetingEntity.RecurrenceEntity.Frequency.DAILY, 1, null),
        )

        val occurrences = generateOccurrences(
            limit = MeetingOccurrencesGenerator.GenerationLimit.Window(
                from = meeting.endTime,
                until = meeting.startTime.plus(2.days)
            ),
            meeting = meeting
        )

        assertEquals(0, occurrences.size)
    }


    @Test
    fun allSupportedIntervalsPreserveCreatorTimeAcrossAutumnDst() {
        val recurrences = listOf(
            MeetingEntity.RecurrenceEntity.Frequency.DAILY to 1L,
            MeetingEntity.RecurrenceEntity.Frequency.WEEKLY to 1L,
            MeetingEntity.RecurrenceEntity.Frequency.WEEKLY to 2L,
            MeetingEntity.RecurrenceEntity.Frequency.WEEKLY to 4L,
        )
        for ((frequency, interval) in recurrences) {
            val meeting = newMeeting(
                startTime = Instant.parse("2026-10-24T10:00:00+02:00"),
                tzid = "Europe/Berlin",
                recurrence = MeetingEntity.RecurrenceEntity(frequency, interval, null)
            )
            val days = if (frequency == MeetingEntity.RecurrenceEntity.Frequency.DAILY) 1 else interval.toInt() * 7
            val nextDate = kotlinx.datetime.LocalDate(2026, 10, 24).plus(days, kotlinx.datetime.DateTimeUnit.DAY)
            val expected = Instant.parse("${nextDate}T10:00:00+01:00")
            val generated = generateOccurrences(MeetingOccurrencesGenerator.GenerationLimit.Count(2), meeting = meeting)
            assertContentEquals(listOf(meeting.startTime, expected), generated.map { it.occurrenceStart })
            assertEquals(1.hours, generated.last().occurrenceEnd - generated.last().occurrenceStart)
            val resumed = generateOccurrences(
                MeetingOccurrencesGenerator.GenerationLimit.Count(1), mapOf(meeting.meetingId to meeting.startTime), meeting
            )
            assertEquals(expected, resumed.single().occurrenceStart)
            val windowed = generateOccurrences(
                MeetingOccurrencesGenerator.GenerationLimit.Window(meeting.endTime, expected), meeting = meeting
            )
            assertEquals(expected, windowed.single().occurrenceStart)
        }
    }
    private fun List<Pair<String, String>>.parseToInstants(): List<Pair<Instant, Instant>> = map {
        Instant.parse(it.first) to Instant.parse(it.second)
    }

    private fun generateOccurrences(
        limit: MeetingOccurrencesGenerator.GenerationLimit,
        lastGeneratedStarts: Map<QualifiedIDEntity, Instant> = emptyMap(),
        meeting: MeetingEntity = MEETING,
    ) = MeetingOccurrencesGenerator.generate(meetings = listOf(meeting), lastGeneratedStarts = lastGeneratedStarts, limit = limit)
}

private val MEETING = newMeeting(recurrence = MeetingEntity.RecurrenceEntity(MeetingEntity.RecurrenceEntity.Frequency.DAILY, 1, null))
