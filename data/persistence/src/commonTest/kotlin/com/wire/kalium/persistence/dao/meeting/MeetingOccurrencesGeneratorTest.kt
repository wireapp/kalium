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
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

class MeetingOccurrencesGeneratorTest {

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
    fun givenUntilLimit_whenGeneratingOccurrences_thenReturnsOccurrencesUntilLimitInclusively() {
        val until = MEETING.startTime.plus(2.days)
        val occurrences = generateOccurrences(MeetingOccurrencesGenerator.GenerationLimit.Until(until))

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

    private fun generateOccurrences(
        limit: MeetingOccurrencesGenerator.GenerationLimit,
        lastGeneratedStarts: Map<QualifiedIDEntity, Instant> = emptyMap(),
    ) = MeetingOccurrencesGenerator.generate(meetings = listOf(MEETING), lastGeneratedStarts = lastGeneratedStarts, limit = limit)
}

private val MEETING = newMeeting(recurrence = MeetingEntity.RecurrenceEntity(MeetingEntity.RecurrenceEntity.Frequency.DAILY, 1, null))
