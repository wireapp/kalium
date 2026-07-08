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

import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import com.wire.kalium.persistence.Meeting
import com.wire.kalium.persistence.MeetingsQueries
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.message.KaliumPager
import com.wire.kalium.persistence.db.ReadDispatcher
import com.wire.kalium.persistence.db.WriteDispatcher
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.days

interface MeetingDao {
    suspend fun upsertMeetings(meetings: List<MeetingEntity>, now: Instant = Clock.System.now())
    suspend fun removeOutdatedMeetings(now: Instant = Clock.System.now())
    suspend fun insertMissingOccurrences(now: Instant = Clock.System.now())
    suspend fun ensureUpcomingOccurrences(minimumCount: Int, now: Instant = Clock.System.now()): EnsureResult

    suspend fun generateMoreOccurrences(count: Int, now: Instant = Clock.System.now()): GenerateResult
    fun getPaginatedMeetings(
        pagingConfig: PagingConfig,
        startingOffset: Long,
        fromDate: Instant = Clock.System.now()
    ): KaliumPager<MeetingDetailsEntity>

    data class EnsureResult(val generatedCount: Int, val existingUpcomingCount: Int)
    value class GenerateResult(val generatedCount: Int)
}

internal class MeetingDaoImpl(
    private val meetingsQueries: MeetingsQueries,
    private val readDispatcher: ReadDispatcher,
    private val writeDispatcher: WriteDispatcher,
) : MeetingDao {
    override suspend fun upsertMeetings(meetings: List<MeetingEntity>, now: Instant) {
        if (meetings.isEmpty()) return

        withContext(writeDispatcher.value) {
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

                meetings.forEach { meeting ->
                    meetingsQueries.upsertMeeting(meeting = meeting)
                }
                meetingsQueries.insertGeneratedOccurrences(
                    meetings = meetings,
                    limit = MeetingOccurrencesGenerator.GenerationLimit.TimeWindow(OCCURRENCE_GENERATION_WINDOW_DAYS.days),
                    shouldRegenerateOccurrences = meetings.associate {
                        it.meetingId to (it.meetingId in meetingIdsRequiringOccurrenceRefresh)
                    },
                    now = now
                )
            }
        }
    }

    override suspend fun removeOutdatedMeetings(now: Instant) {
        withContext(writeDispatcher.value) {
            meetingsQueries.removeOutdatedMeetings(
                outdatedThreshold = now - OUTDATED_MEETING_RETENTION_DAYS.days
            )
        }
    }

    override suspend fun insertMissingOccurrences(now: Instant) {
        withContext(writeDispatcher.value) {
            meetingsQueries.transaction {
                meetingsQueries.selectRecurringMeetings(MeetingMapper::fromViewToModel).executeAsList().let { meetings ->
                    meetingsQueries.insertGeneratedOccurrences(
                        meetings = meetings,
                        limit = MeetingOccurrencesGenerator.GenerationLimit.TimeWindow(OCCURRENCE_GENERATION_WINDOW_DAYS.days),
                        shouldRegenerateOccurrences = meetings.associate { it.meetingId to false },
                        now = now
                    )
                }
            }
        }
    }

    override suspend fun ensureUpcomingOccurrences(minimumCount: Int, now: Instant): MeetingDao.EnsureResult {
        if (minimumCount <= 0) {
            val existingCount = withContext(readDispatcher.value) {
                meetingsQueries.countUpcomingMeetingOccurrences(now).executeAsOne().toInt()
            }
            return MeetingDao.EnsureResult(generatedCount = 0, existingUpcomingCount = existingCount)
        }
        return withContext(writeDispatcher.value) {
            meetingsQueries.transactionWithResult {
                val existingCount = meetingsQueries.countUpcomingMeetingOccurrences(now).executeAsOne().toInt()
                val missingCount = (minimumCount - existingCount).coerceAtLeast(0)
                val recurringMeetings = meetingsQueries.selectRecurringMeetings(MeetingMapper::fromViewToModel).executeAsList()
                if (missingCount <= 0) {
                    return@transactionWithResult MeetingDao.EnsureResult(generatedCount = 0, existingUpcomingCount = existingCount)
                }
                val generatedCount = meetingsQueries.insertGeneratedOccurrences(
                    meetings = recurringMeetings,
                    limit = MeetingOccurrencesGenerator.GenerationLimit.Count(missingCount),
                    shouldRegenerateOccurrences = recurringMeetings.associate { it.meetingId to false },
                    now = now
                )
                MeetingDao.EnsureResult(generatedCount = generatedCount, existingUpcomingCount = existingCount)
            }
        }
    }

    override suspend fun generateMoreOccurrences(count: Int, now: Instant): MeetingDao.GenerateResult {
        if (count <= 0) {
            return MeetingDao.GenerateResult(generatedCount = 0)
        }
        return withContext(writeDispatcher.value) {
            meetingsQueries.transactionWithResult {
                val recurringMeetings = meetingsQueries.selectRecurringMeetings(MeetingMapper::fromViewToModel).executeAsList()
                val generatedCount = meetingsQueries.insertGeneratedOccurrences(
                    meetings = recurringMeetings,
                    limit = MeetingOccurrencesGenerator.GenerationLimit.Count(count),
                    shouldRegenerateOccurrences = recurringMeetings.associate { it.meetingId to false },
                    now = now
                )
                MeetingDao.GenerateResult(generatedCount = generatedCount)
            }
        }
    }

    @OptIn(ExperimentalPagingApi::class)
    override fun getPaginatedMeetings(
        pagingConfig: PagingConfig,
        startingOffset: Long,
        fromDate: Instant
    ): KaliumPager<MeetingDetailsEntity> = KaliumPager(
        pager = Pager(
            config = pagingConfig,
            remoteMediator = MeetingOccurrencesRemoteMediator(
                meetingDao = this,
                fromDate = fromDate,
            ),
            pagingSourceFactory = { meetingPagingSource(fromDate, startingOffset) }
        ),
        pagingSource = meetingPagingSource(fromDate, startingOffset),
        readDispatcher = readDispatcher,
    )

    private fun meetingPagingSource(fromDate: Instant, startingOffset: Long): MeetingPagingSource =
        MeetingPagingSource(
            meetingsQueries = meetingsQueries,
            context = readDispatcher.value,
            fromDate = fromDate,
            initialOffset = startingOffset,
        )

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
        meetings: List<MeetingEntity>,
        limit: MeetingOccurrencesGenerator.GenerationLimit,
        now: Instant,
        shouldRegenerateOccurrences: Map<QualifiedIDEntity, Boolean>,
    ): Int {
        val lastGeneratedStarts = meetings.mapNotNull { meeting ->
            when (shouldRegenerateOccurrences[meeting.meetingId]) {
                true -> null
                else -> selectLastGeneratedOccurrenceStart(meeting.meetingId).executeAsOneOrNull()
                    ?.let { meeting.meetingId to it }
            }
        }.toMap()

        return MeetingOccurrencesGenerator.generate(
            meetings = meetings,
            lastGeneratedStarts = lastGeneratedStarts,
            limit = limit,
            now = now
        ).onEach { occurrence ->
            insertMeetingOccurrence(
                occurrence_id = occurrence.occurrenceId,
                meeting_id = occurrence.meetingId,
                occurrence_start = occurrence.occurrenceStart,
                occurrence_end = occurrence.occurrenceEnd ?: occurrence.occurrenceStart
            )
        }.size
    }

    private companion object {
        private const val OCCURRENCE_GENERATION_WINDOW_DAYS = 90
        private const val OUTDATED_MEETING_RETENTION_DAYS = 30
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
