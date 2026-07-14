/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
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

package com.wire.kalium.logic.data.meeting

import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.wrapApiRequest
import com.wire.kalium.common.error.wrapStorageRequest
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.network.api.base.authenticated.meeting.MeetingApi
import com.wire.kalium.persistence.dao.meeting.MeetingDao
import com.wire.kalium.persistence.dao.meeting.MeetingEntity
import com.wire.kalium.util.DateTimeUtil.asStartOfDay
import com.wire.kalium.util.DateTimeUtil.currentInstant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import com.wire.kalium.persistence.dao.meeting.MeetingOccurrencesGenerator.GenerationLimit
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.days

internal interface MeetingRepository {
    suspend fun fetchAndPersistMeetings(
        generateOccurrencesFrom: Instant = occurrenceOutdatedThreshold(),
        generateOccurrencesUntil: Instant = occurrenceGenerationUntil()
    ): Either<CoreFailure, List<MeetingEntity>>

    suspend fun syncMeetingOccurrences(
        removeOlderThan: Instant = occurrenceOutdatedThreshold(),
        generateOccurrencesUntil: Instant = occurrenceGenerationUntil()
    ): Either<CoreFailure, Unit>

    suspend fun observeMeetingOccurrence(occurrenceId: String): Flow<MeetingOccurrence?>

    suspend fun getPaginatedMeetingOccurrences(
        pagingConfig: PagingConfig,
        startingOffset: Long,
        from: Instant = currentInstant().asStartOfDay(),
    ): Flow<PagingData<MeetingOccurrence>>
}

internal class MeetingDataSource(
    private val meetingDAO: MeetingDao,
    private val meetingApi: MeetingApi,
    private val meetingMapper: MeetingMapper = MapperProvider.meetingMapper()
) : MeetingRepository {
    override suspend fun fetchAndPersistMeetings(
        generateOccurrencesFrom: Instant,
        generateOccurrencesUntil: Instant
    ): Either<CoreFailure, List<MeetingEntity>> =
        wrapApiRequest {
            meetingApi.fetchMeetings()
        }.flatMap { meetings ->
            wrapStorageRequest {
                meetings.mapNotNull { meetingMapper.fromApiToDao(it) }
                    .also { meetingsToPersist ->
                        if (meetingsToPersist.isNotEmpty()) {
                            meetingDAO.upsertMeetings(
                                meetings = meetingsToPersist,
                                generateOccurrencesWindow = GenerationLimit.Window(generateOccurrencesFrom, generateOccurrencesUntil)
                            )
                        }
                    }
            }
        }

    override suspend fun syncMeetingOccurrences(
        removeOlderThan: Instant,
        generateOccurrencesUntil: Instant
    ): Either<CoreFailure, Unit> = wrapStorageRequest {
        meetingDAO.removeOutdatedMeetings(removeOlderThan)
        meetingDAO.insertMissingOccurrences(GenerationLimit.Window(removeOlderThan, generateOccurrencesUntil))
    }

    override suspend fun observeMeetingOccurrence(occurrenceId: String): Flow<MeetingOccurrence?> =
        meetingDAO.getMeetingOccurrenceDetailsFlow(occurrenceId)
            .map { it?.let(meetingMapper::fromDaoToModel) }
            .distinctUntilChanged()

    override suspend fun getPaginatedMeetingOccurrences(
        pagingConfig: PagingConfig,
        startingOffset: Long,
        from: Instant,
    ) = meetingDAO.getPaginatedMeetingOccurrenceDetails(
        pagingConfig = pagingConfig,
        startingOffset = startingOffset,
        from = from,
    ).pagingDataFlow.map { pagingData -> pagingData.map(meetingMapper::fromDaoToModel) }
}

private const val OCCURRENCE_GENERATION_WINDOW_DAYS = 90
private const val OUTDATED_MEETING_RETENTION_DAYS = 30
private fun occurrenceGenerationUntil() = currentInstant().asStartOfDay().plus((OCCURRENCE_GENERATION_WINDOW_DAYS + 1).days)
private fun occurrenceOutdatedThreshold() = currentInstant().asStartOfDay().minus(OUTDATED_MEETING_RETENTION_DAYS.days)
