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
import com.wire.kalium.util.TzidUtil
import kotlinx.datetime.Instant
import kotlin.uuid.Uuid

object MeetingOccurrencesGenerator {

    fun generate(
        meetings: List<MeetingEntity>,
        lastGeneratedStarts: Map<QualifiedIDEntity, Instant>,
        limit: GenerationLimit,
    ): List<MeetingOccurrenceEntity> {
        if (meetings.isEmpty()) return emptyList()
        val maxDateLimit = (limit as? GenerationLimit.Window)?.until
        val minDateLimit = (limit as? GenerationLimit.Window)?.from
        val totalCountToGenerate = (limit as? GenerationLimit.Count)?.totalCount
        val statesList = meetings.initialGeneratorStates(lastGeneratedStarts, minDateLimit)
        return generateOccurrences(statesList, maxDateLimit, totalCountToGenerate)
    }

    private fun generateOccurrences(
        statesList: MutableList<MeetingGeneratorState>,
        maxDateLimit: Instant?,
        totalCountToGenerate: Int?
    ): List<MeetingOccurrenceEntity> {
        val allOccurrences = mutableListOf<MeetingOccurrenceEntity>()
        while (statesList.isNotEmpty()) {
            val currentState = statesList.minBy { it.nextCandidateStart }
            if (shouldStopGenerating(currentState, allOccurrences.size, maxDateLimit, totalCountToGenerate)) break
            allOccurrences.add(currentState.toOccurrence())
            statesList.advanceOrRemove(currentState)
        }
        return allOccurrences
    }

    private fun List<MeetingEntity>.initialGeneratorStates(
        lastGeneratedStarts: Map<QualifiedIDEntity, Instant>,
        minDateLimit: Instant?,
    ): MutableList<MeetingGeneratorState> =
        this.mapNotNull { meeting ->
            meeting.toGeneratorState(lastGeneratedStarts[meeting.meetingId], minDateLimit)
        }.toMutableList()

    private fun MeetingEntity.toGeneratorState(lastStart: Instant?, minDateLimit: Instant?): MeetingGeneratorState? {
        // If the meeting has a recurrence, check if the tzid is supported. If not, return null to skip this meeting.
        // This is a safeguard to prevent generating occurrences for meeting with unsupported tzid.
        // After updating the app to support the given tzid, the next generation will correctly generate occurrences for this meeting.
        if (recurrence != null && !TzidUtil.isTimeZoneSupported(tzid)) return null

        val interval = recurrence?.interval?.toInt() ?: 1
        val firstCandidateStart = firstCandidateStart(lastStart, interval)?.let {
            advanceUntilAfter(candidateStart = it, minDateLimit = minDateLimit, interval = interval)
        }
        return if (firstCandidateStart == null || isBeyondSeriesEnd(firstCandidateStart)) {
            null
        } else {
            MeetingGeneratorState(meeting = this, nextCandidateStart = firstCandidateStart)
        }
    }

    private fun shouldStopGenerating(
        currentState: MeetingGeneratorState,
        currentCount: Int,
        maxDateLimit: Instant?,
        totalCountToGenerate: Int?
    ): Boolean {
        val hasEnoughItems = totalCountToGenerate != null && currentCount >= totalCountToGenerate
        val isBeyondWindow = maxDateLimit != null && currentState.nextCandidateStart > maxDateLimit
        return hasEnoughItems || isBeyondWindow
    }

    private fun MeetingGeneratorState.toOccurrence(): MeetingOccurrenceEntity {
        val duration = meeting.endTime - meeting.startTime
        return MeetingOccurrenceEntity(
            occurrenceId = Uuid.random().toString(),
            meetingId = meeting.meetingId,
            occurrenceStart = nextCandidateStart,
            occurrenceEnd = nextCandidateStart + duration
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
        val interval = recurrence.interval?.toInt() ?: 1
        return nextCandidateStart.plusPeriod(recurrence.frequency, interval, meeting.tzid)?.takeIf { nextStart ->
            nextStart > nextCandidateStart && recurrence.isBeforeSeriesEnd(nextStart)
        }?.let {
            copy(nextCandidateStart = it)
        }
    }

    private fun MeetingEntity.RecurrenceEntity.isBeforeSeriesEnd(candidateStart: Instant): Boolean =
        until == null || candidateStart <= until

    private fun MeetingEntity.firstCandidateStart(lastStart: Instant?, interval: Int): Instant? {
        val recurrence = this.recurrence
        return when {
            lastStart != null && recurrence != null -> lastStart.plusPeriod(recurrence.frequency, interval, tzid)
            lastStart != null -> null
            else -> startTime
        }
    }

    private fun MeetingEntity.advanceUntilAfter(candidateStart: Instant, minDateLimit: Instant?, interval: Int): Instant? {
        val duration = endTime - startTime
        val recurrence = recurrence
        var nextCandidateStart: Instant? = candidateStart
        while (minDateLimit != null && nextCandidateStart != null && nextCandidateStart + duration <= minDateLimit) {
            nextCandidateStart = if (recurrence != null) {
                nextCandidateStart.plusPeriod(recurrence.frequency, interval, tzid)?.takeIf { advancedStart ->
                    advancedStart > nextCandidateStart && recurrence.isBeforeSeriesEnd(advancedStart)
                }
            } else {
                null
            }
        }
        return nextCandidateStart
    }

    private fun MeetingEntity.isBeyondSeriesEnd(candidateStart: Instant): Boolean =
        recurrence?.until?.let { candidateStart > it } ?: false

    @Suppress("MagicNumber")
    private fun Instant.plusPeriod(frequency: MeetingEntity.RecurrenceEntity.Frequency, interval: Int, tzid: String): Instant? {
        val daysToAdd = when (frequency) {
            MeetingEntity.RecurrenceEntity.Frequency.DAILY -> interval
            MeetingEntity.RecurrenceEntity.Frequency.WEEKLY -> interval * 7
        }
        return TzidUtil.plusDaysOrNull(this, daysToAdd, tzid)
    }

    private data class MeetingGeneratorState(
        val meeting: MeetingEntity,
        val nextCandidateStart: Instant,
    )

    sealed interface GenerationLimit {
        data class Count(val totalCount: Int) : GenerationLimit
        data class Window(val from: Instant, val until: Instant) : GenerationLimit
    }
}
