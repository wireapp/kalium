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

import androidx.paging.Pager
import androidx.paging.PagingConfig
import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.wire.kalium.persistence.Meeting
import com.wire.kalium.persistence.MeetingsQueries
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.message.KaliumPager
import com.wire.kalium.persistence.db.ReadDispatcher
import com.wire.kalium.persistence.dao.meeting.MeetingOccurrencesGenerator.GenerationLimit
import com.wire.kalium.persistence.db.WriteDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant

interface MeetingDao {
    suspend fun upsertMeetings(meetings: List<MeetingEntity>, generateOccurrencesWindow: GenerationLimit.Window)
    suspend fun removeOutdatedMeetings(olderThan: Instant)
    suspend fun insertMissingOccurrences(generateOccurrencesWindow: GenerationLimit.Window)
    fun getMeetingOccurrenceDetailsFlow(occurrenceId: String): Flow<MeetingOccurrenceDetailsEntity?>
    fun getPaginatedMeetingOccurrenceDetails(
        pagingConfig: PagingConfig,
        startingOffset: Long,
        from: Instant,
    ): KaliumPager<MeetingOccurrenceDetailsEntity>
}

internal class MeetingDaoImpl(
    private val meetingsQueries: MeetingsQueries,
    private val readDispatcher: ReadDispatcher,
    private val writeDispatcher: WriteDispatcher,
) : MeetingDao {
    override suspend fun upsertMeetings(meetings: List<MeetingEntity>, generateOccurrencesWindow: GenerationLimit.Window) {
        if (meetings.isEmpty()) return

        withContext(writeDispatcher.value) {
            meetingsQueries.transaction {
                val storedMeetingsById = meetingsQueries.selectMeetingByIds(meetings.map { it.meetingId })
                    .awaitAsList()
                    .associateBy { it.meeting_id }
                val meetingIdsRequiringOccurrenceRefresh = meetings
                    .filter { meeting -> storedMeetingsById[meeting.meetingId]?.hasSameScheduleAs(meeting) != true }
                    .mapTo(mutableSetOf()) { it.meetingId }

                meetingIdsRequiringOccurrenceRefresh.forEach { meetingId ->
                    if (storedMeetingsById.containsKey(meetingId)) {
                        meetingsQueries.deleteMeetingOccurrences(meetingId)
                    }
                }

                meetings.forEach { meeting ->
                    meetingsQueries.upsertMeeting(meeting = meeting)
                }
                meetingsQueries.insertGeneratedOccurrences(
                    meetings = meetings,
                    limit = generateOccurrencesWindow,
                    shouldRegenerateOccurrences = meetings.associate {
                        it.meetingId to (it.meetingId in meetingIdsRequiringOccurrenceRefresh)
                    },
                )
            }
        }
    }

    override suspend fun removeOutdatedMeetings(olderThan: Instant) {
        withContext(writeDispatcher.value) {
            meetingsQueries.transaction {
                meetingsQueries.removeOutdatedMeetings(outdatedThreshold = olderThan)
                meetingsQueries.removeOutdatedMeetingOccurrences(outdatedThreshold = olderThan)
            }
        }
    }

    override suspend fun insertMissingOccurrences(generateOccurrencesWindow: GenerationLimit.Window) {
        withContext(writeDispatcher.value) {
            meetingsQueries.transaction {
                meetingsQueries.selectRecurringMeetings(MeetingMapper::fromViewToModel).awaitAsList().let { meetings ->
                    meetingsQueries.insertGeneratedOccurrences(
                        meetings = meetings,
                        limit = generateOccurrencesWindow,
                        shouldRegenerateOccurrences = meetings.associate { it.meetingId to false },
                    )
                }
            }
        }
    }

    override fun getMeetingOccurrenceDetailsFlow(occurrenceId: String): Flow<MeetingOccurrenceDetailsEntity?> =
        meetingsQueries.selectMeetingOccurrenceDetailsById(occurrenceId = occurrenceId, mapper = MeetingMapper::fromViewToDetails)
            .asFlow()
            .mapToOneOrNull(readDispatcher.value)
            .flowOn(readDispatcher.value)

    override fun getPaginatedMeetingOccurrenceDetails(pagingConfig: PagingConfig, startingOffset: Long, from: Instant) =
        KaliumPager(
            pager = Pager(
                config = pagingConfig,
                pagingSourceFactory = {
                    meetingPagingSource(from, startingOffset, pagingConfig.prefetchDistance)
                }
            ),
            pagingSource = meetingPagingSource(
                from = from,
                startingOffset = startingOffset,
                prefetchDistance = pagingConfig.prefetchDistance
            ),
            readDispatcher = readDispatcher,
        )

    private fun meetingPagingSource(from: Instant, startingOffset: Long, prefetchDistance: Int): MeetingPagingSource =
        MeetingPagingSource(
            meetingsQueries = meetingsQueries,
            readContext = readDispatcher.value,
            writeContext = writeDispatcher.value,
            parameters = MeetingPagingParameters(
                from = from,
                initialOffset = startingOffset,
                prefetchDistance = prefetchDistance,
            ),
        )
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

internal suspend fun MeetingsQueries.insertGeneratedOccurrences(
    meetings: List<MeetingEntity>,
    limit: GenerationLimit,
    shouldRegenerateOccurrences: Map<QualifiedIDEntity, Boolean>,
): Int {
    val lastGeneratedStarts = meetings.mapNotNull { meeting ->
        when (shouldRegenerateOccurrences[meeting.meetingId]) {
            true -> null
            else -> selectLastGeneratedOccurrenceStart(meeting.meetingId).awaitAsOneOrNull()?.let { meeting.meetingId to it }
        }
    }.toMap()

    return MeetingOccurrencesGenerator.generate(
        meetings = meetings,
        lastGeneratedStarts = lastGeneratedStarts,
        limit = limit,
    ).onEach { occurrence ->
        insertMeetingOccurrence(
            occurrence_id = occurrence.occurrenceId,
            meeting_id = occurrence.meetingId,
            occurrence_start = occurrence.occurrenceStart,
            occurrence_end = occurrence.occurrenceEnd
        )
    }.size
}

private fun Meeting.hasSameScheduleAs(meeting: MeetingEntity): Boolean =
    start_date.isSameStoredInstantAs(meeting.startTime) &&
            end_date.isSameStoredInstantAs(meeting.endTime) &&
            recurrence_frequency == meeting.recurrence?.frequency &&
            recurrence_interval == meeting.recurrence?.interval &&
            recurrence_end_date.isSameStoredInstantAs(meeting.recurrence?.until)

private fun Instant?.isSameStoredInstantAs(other: Instant?): Boolean =
    this?.toEpochMilliseconds() == other?.toEpochMilliseconds()
