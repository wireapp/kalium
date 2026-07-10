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
import kotlinx.datetime.DateTimePeriod
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlin.uuid.Uuid

internal object MeetingOccurrencesGenerator {

    fun generate(
        meetings: List<MeetingEntity>,
        lastGeneratedStarts: Map<QualifiedIDEntity, Instant>,
        bounds: GenerationBounds,
    ): List<MeetingOccurrenceEntity> {
        if (meetings.isEmpty()) return emptyList()
        val statesList = meetings.initialGeneratorStates(lastGeneratedStarts)
        return generateOccurrences(statesList, bounds)
    }

    private fun generateOccurrences(
        statesList: MutableList<MeetingGeneratorState>,
        bounds: GenerationBounds,
    ): List<MeetingOccurrenceEntity> {
        val allOccurrences = mutableListOf<MeetingOccurrenceEntity>()
        while (statesList.isNotEmpty()) {
            val currentState = statesList.minBy { it.nextCandidateStart }
            if (shouldStopGenerating(currentState, allOccurrences.size, bounds)) break
            allOccurrences.add(currentState.toOccurrence())
            statesList.advanceOrRemove(currentState)
        }
        return allOccurrences
    }

    private fun List<MeetingEntity>.initialGeneratorStates(
        lastGeneratedStarts: Map<QualifiedIDEntity, Instant>
    ): MutableList<MeetingGeneratorState> =
        this.mapNotNull { meeting ->
            meeting.toGeneratorState(lastGeneratedStarts[meeting.meetingId])
        }.toMutableList()

    private fun MeetingEntity.toGeneratorState(lastStart: Instant?): MeetingGeneratorState? {
        val interval = recurrence?.interval?.toInt() ?: 1
        val firstCandidateStart = firstCandidateStart(lastStart, interval)
        return if (firstCandidateStart == null || isBeyondSeriesEnd(firstCandidateStart)) {
            null
        } else {
            MeetingGeneratorState(meeting = this, nextCandidateStart = firstCandidateStart)
        }
    }

    private fun shouldStopGenerating(
        currentState: MeetingGeneratorState,
        currentCount: Int,
        bounds: GenerationBounds,
    ): Boolean {
        val hasEnoughItems = bounds.totalCount?.let { currentCount >= it } ?: false
        val isBeyondWindow = bounds.until?.let { currentState.nextCandidateStart >= it } ?: false
        return hasEnoughItems || isBeyondWindow
    }

    private fun MeetingGeneratorState.toOccurrence(): MeetingOccurrenceEntity {
        val duration = meeting.endTime?.let { it - meeting.startTime }
        return MeetingOccurrenceEntity(
            occurrenceId = Uuid.random().toString(),
            meetingId = meeting.meetingId,
            occurrenceStart = nextCandidateStart,
            occurrenceEnd = duration?.let { nextCandidateStart + it } ?: nextCandidateStart
        )
    }

    private fun MutableList<MeetingGeneratorState>.advanceOrRemove(currentState: MeetingGeneratorState) {
        val nextState = currentState.nextState()
        if (nextState == null) {
            remove(currentState)
        } else {
            set(indexOf(currentState), nextState)
        }
    }

    private fun MeetingGeneratorState.nextState(): MeetingGeneratorState? {
        val recurrence = meeting.recurrence ?: return null
        val interval = recurrence.interval.toInt()
        val nextStart = nextCandidateStart.plusPeriod(recurrence.frequency, interval)
        return if (nextStart > nextCandidateStart && recurrence.isBeforeSeriesEnd(nextStart)) {
            copy(nextCandidateStart = nextStart)
        } else {
            null
        }
    }

    private fun MeetingEntity.RecurrenceEntity.isBeforeSeriesEnd(candidateStart: Instant): Boolean =
        until == null || candidateStart <= until

    private fun MeetingEntity.firstCandidateStart(lastStart: Instant?, interval: Int): Instant? {
        val recurrence = this.recurrence
        return when {
            lastStart != null && recurrence != null -> lastStart.plusPeriod(recurrence.frequency, interval)
            lastStart != null -> null
            else -> startTime
        }
    }

    private fun MeetingEntity.isBeyondSeriesEnd(candidateStart: Instant): Boolean =
        recurrence?.until?.let { candidateStart > it } ?: false

    private fun Instant.plusPeriod(frequency: MeetingEntity.RecurrenceEntity.Frequency, interval: Int): Instant {
        val timeZone = TimeZone.currentSystemDefault()
        val period = when (frequency) {
            MeetingEntity.RecurrenceEntity.Frequency.DAILY -> DateTimePeriod(days = interval)
            MeetingEntity.RecurrenceEntity.Frequency.WEEKLY -> DateTimePeriod(days = interval * 7)
            MeetingEntity.RecurrenceEntity.Frequency.MONTHLY -> DateTimePeriod(months = interval)
            MeetingEntity.RecurrenceEntity.Frequency.YEARLY -> DateTimePeriod(years = interval)
        }
        return this.plus(period, timeZone)
    }

    private data class MeetingGeneratorState(
        val meeting: MeetingEntity,
        val nextCandidateStart: Instant,
    )

}

internal class GenerationBounds private constructor(val totalCount: Int?, val until: Instant?) {
    companion object {
        /**
         * Generates at most [totalCount] occurrences without applying a date boundary.
         */
        fun count(totalCount: Int): GenerationBounds = GenerationBounds(totalCount = totalCount, until = null)

        /**
         * Generates occurrences with start dates strictly before [until], without applying a count boundary.
         */
        fun until(until: Instant): GenerationBounds = GenerationBounds(totalCount = null, until = until)
    }
}
