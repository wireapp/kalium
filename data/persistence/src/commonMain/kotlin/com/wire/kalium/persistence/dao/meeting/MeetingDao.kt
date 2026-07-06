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

import com.wire.kalium.persistence.Meeting
import com.wire.kalium.persistence.MeetingsQueries
import com.wire.kalium.persistence.db.WriteDispatcher
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.days

interface MeetingDao {
    suspend fun upsertMeetings(meetings: List<MeetingEntity>)
}

internal class MeetingDaoImpl(
    private val meetingsQueries: MeetingsQueries,
    private val writeDispatcher: WriteDispatcher,
) : MeetingDao {
    override suspend fun upsertMeetings(meetings: List<MeetingEntity>) {
        if (meetings.isEmpty()) return

        withContext(writeDispatcher.value) {
            val now = Clock.System.now()
            meetingsQueries.transaction {
                val storedMeetingsById = meetingsQueries.selectMeetingByIds(meetings.map { it.meetingId })
                    .executeAsList()
                    .associateBy { it.meeting_id }
                val meetingIdsRequiringOccurrenceRefresh = meetings
                    .filter { meeting -> storedMeetingsById[meeting.meetingId]?.hasSameScheduleAs(meeting) != true }
                    .mapTo(mutableSetOf()) { it.meetingId }

                meetingIdsRequiringOccurrenceRefresh.forEach { meetingId ->
                    if (storedMeetingsById.containsKey(meetingId)) {
                        meetingsQueries.deleteNotStartedOccurrences(meetingId, now)
                    }
                }

                meetings.forEach { meeting -> meetingsQueries.upsertMeeting(meeting) }
                meetings.forEach { meeting ->
                    meetingsQueries.insertGeneratedOccurrences(
                        meeting = meeting,
                        shouldRegenerateOccurrences = meeting.meetingId in meetingIdsRequiringOccurrenceRefresh,
                        now = now
                    )
                }
            }
        }
    }

    private suspend fun MeetingsQueries.upsertMeeting(meeting: MeetingEntity) {
        upsertMeeting(
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
            recurrence_end_date = meeting.recurrence?.until,
        )
    }

    private suspend fun MeetingsQueries.insertGeneratedOccurrences(
        meeting: MeetingEntity,
        shouldRegenerateOccurrences: Boolean,
        now: Instant
    ) {
        val lastGeneratedStarts = if (shouldRegenerateOccurrences) {
            emptyMap()
        } else {
            selectLastGeneratedOccurrenceStart(meeting.meetingId).executeAsOneOrNull()
                ?.let { mapOf(meeting.meetingId.toString() to it) }
                ?: emptyMap()
        }

        MeetingOccurrencesGenerator.generate(
            meetings = listOf(meeting),
            lastGeneratedStarts = lastGeneratedStarts,
            limit = MeetingOccurrencesGenerator.GenerationLimit.TimeWindow(OCCURRENCE_GENERATION_WINDOW_DAYS.days),
            now = now
        ).forEach { occurrence ->
            insertMeetingOccurrence(
                occurrence_id = occurrence.occurrenceId,
                meeting_id = occurrence.meetingId,
                occurrence_start = occurrence.occurrenceStart,
                occurrence_end = occurrence.occurrenceEnd ?: occurrence.occurrenceStart
            )
        }
    }

    private companion object {
        private const val OCCURRENCE_GENERATION_WINDOW_DAYS = 90
    }
}

private fun Meeting.hasSameScheduleAs(meeting: MeetingEntity): Boolean =
    start_date.isSameStoredInstantAs(meeting.startTime) &&
        end_date.isSameStoredInstantAs(meeting.endTime) &&
        recurrence_frequency == meeting.recurrence?.frequency &&
        recurrence_interval == meeting.recurrence?.interval &&
        recurrence_end_date.isSameStoredInstantAs(meeting.recurrence?.until)

private fun Instant?.isSameStoredInstantAs(other: Instant?): Boolean =
    this?.toEpochMilliseconds() == other?.toEpochMilliseconds()
