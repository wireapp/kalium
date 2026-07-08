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
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal interface MeetingRepository {
    suspend fun fetchAndPersistMeetings(now: Instant = Clock.System.now()): Either<CoreFailure, Unit>
    suspend fun syncMeetingOccurrences(now: Instant = Clock.System.now()): Either<CoreFailure, Unit>
    suspend fun getPaginatedMeetings(
        pagingConfig: PagingConfig,
        startingOffset: Long,
        fromDate: Instant = Clock.System.now()
    ): Flow<PagingData<Meeting>>
}

internal class MeetingDataSource(
    private val meetingDAO: MeetingDao,
    private val meetingApi: MeetingApi,
    private val meetingMapper: MeetingMapper = MapperProvider.meetingMapper()
) : MeetingRepository {
    override suspend fun fetchAndPersistMeetings(now: Instant): Either<CoreFailure, Unit> = wrapApiRequest {
        meetingApi.fetchMeetings()
    }.flatMap { meetings ->
        wrapStorageRequest {
            meetingDAO.upsertMeetings(meetings.map { meetingMapper.fromApiToDao(it) }, now)
        }
    }

    override suspend fun syncMeetingOccurrences(now: Instant): Either<CoreFailure, Unit> {
        return wrapStorageRequest {
            meetingDAO.removeOutdatedMeetings(now)
            meetingDAO.insertMissingOccurrences(now)
        }
    }

    override suspend fun getPaginatedMeetings(
        pagingConfig: PagingConfig,
        startingOffset: Long,
        fromDate: Instant
    ): Flow<PagingData<Meeting>> =
        meetingDAO.getPaginatedMeetings(
            pagingConfig = pagingConfig,
            startingOffset = startingOffset,
            fromDate = fromDate
        ).pagingDataFlow.map { pagingData ->
            pagingData.map(meetingMapper::fromDaoToModel)
        }
}
