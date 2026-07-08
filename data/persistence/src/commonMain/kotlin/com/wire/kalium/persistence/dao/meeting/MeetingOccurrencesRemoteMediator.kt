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
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

@OptIn(ExperimentalPagingApi::class)
internal class MeetingOccurrencesRemoteMediator(
    private val meetingDao: MeetingDao,
    private val fromDate: Instant,
) : RemoteMediator<Int, MeetingDetailsEntity>() {
    override suspend fun load(loadType: LoadType, state: PagingState<Int, MeetingDetailsEntity>): MediatorResult =
        @Suppress("TooGenericExceptionCaught")
        try {
            when (loadType) {
                LoadType.REFRESH -> {
                    val targetCount = state.config.initialLoadSize + state.config.prefetchDistance
                    val result = meetingDao.ensureUpcomingOccurrences(minimumCount = targetCount, now = fromDate)
                    MediatorResult.Success(endOfPaginationReached = result.existingUpcomingCount == 0 && result.generatedCount == 0)
                }
                LoadType.APPEND -> {
                    val targetCount = state.config.pageSize + state.config.prefetchDistance
                    val result = meetingDao.generateMoreOccurrences(count = targetCount, now = Clock.System.now())
                    MediatorResult.Success(endOfPaginationReached = result.generatedCount == 0)
                }
                LoadType.PREPEND -> MediatorResult.Success(endOfPaginationReached = true)
            }
        } catch (exception: Exception) {
            MediatorResult.Error(exception)
        }
}
