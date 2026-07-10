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
        val occurrences = generateOccurrences(GenerationBounds.count(totalCount = 2))

        assertEquals(2, occurrences.size)
        assertContentEquals(
            listOf(MEETING.startTime, MEETING.startTime.plus(1.days)),
            occurrences.map { it.occurrenceStart }
        )
    }

    @Test
    fun givenUntilLimit_whenGeneratingOccurrences_thenReturnsOccurrencesUntilLimitExclusively() {
        val until = MEETING.startTime.plus(2.days)
        val occurrences = generateOccurrences(GenerationBounds.until(until))

        assertContentEquals(
            listOf(MEETING.startTime, MEETING.startTime.plus(1.days)),
            occurrences.map { it.occurrenceStart }
        )
        assertContentEquals(
            occurrences.map { it.occurrenceStart + 1.hours },
            occurrences.map { it.occurrenceEnd }
        )
    }

    @Test
    fun givenCountUntilLimit_whenGeneratingOccurrences_thenStopsAfterCount() {
        val until = MEETING.startTime.plus(10.days)
        val occurrences = generateOccurrences(GenerationBounds.countUntil(totalCount = 2, until = until))

        assertContentEquals(
            listOf(MEETING.startTime, MEETING.startTime.plus(1.days)),
            occurrences.map { it.occurrenceStart }
        )
    }

    @Test
    fun givenCountUntilLimit_whenGeneratingOccurrences_thenStopsBeforeUntil() {
        val until = MEETING.startTime.plus(2.days)
        val occurrences = generateOccurrences(GenerationBounds.countUntil(totalCount = 10, until = until))

        assertContentEquals(
            listOf(MEETING.startTime, MEETING.startTime.plus(1.days)),
            occurrences.map { it.occurrenceStart }
        )
    }

    @Test
    fun givenLastGeneratedStart_whenGeneratingOccurrences_thenStartsAfterLastGeneratedOccurrence() {
        val occurrences = generateOccurrences(
            bounds = GenerationBounds.count(totalCount = 2),
            lastGeneratedStarts = mapOf(MEETING.meetingId to MEETING.startTime.plus(1.days))
        )

        assertContentEquals(
            listOf(MEETING.startTime.plus(2.days), MEETING.startTime.plus(3.days)),
            occurrences.map { it.occurrenceStart }
        )
    }

    private fun generateOccurrences(
        bounds: GenerationBounds,
        lastGeneratedStarts: Map<QualifiedIDEntity, Instant> = emptyMap(),
    ) = MeetingOccurrencesGenerator.generate(
        meetings = listOf(MEETING),
        lastGeneratedStarts = lastGeneratedStarts,
        bounds = bounds
    )
}

private val MEETING = newMeeting(recurrence = MeetingEntity.RecurrenceEntity(MeetingEntity.RecurrenceEntity.Frequency.DAILY, 1, null))
