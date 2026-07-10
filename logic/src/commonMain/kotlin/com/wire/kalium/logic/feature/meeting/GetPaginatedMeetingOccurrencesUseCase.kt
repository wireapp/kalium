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

package com.wire.kalium.logic.feature.meeting

import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.wire.kalium.logic.data.meeting.MeetingOccurrence
import com.wire.kalium.logic.data.meeting.MeetingRepository
import com.wire.kalium.util.DateTimeUtil.asStartOfDay
import com.wire.kalium.util.DateTimeUtil.currentInstant
import com.wire.kalium.util.KaliumDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.datetime.Instant

/**
 * This use case observes and returns a flow of paginated meeting occurrences.
 */
public interface GetPaginatedMeetingOccurrencesUseCase {
    /**
     * @param pagingConfig The configuration for pagination, including page size and prefetch distance.
     * @param startingOffset The initial offset for pagination, indicating where to start fetching data.
     * @param from The starting date for fetching meeting occurrences, defaulting to the start of the current day.
     * @return A flow of paginated meeting occurrences, represented as PagingData<MeetingOccurrence>.
     */
    public suspend operator fun invoke(
        pagingConfig: PagingConfig,
        startingOffset: Long,
        from: Instant = currentInstant().asStartOfDay(),
    ): Flow<PagingData<MeetingOccurrence>>
}

internal class GetPaginatedMeetingOccurrencesUseCaseImpl(
    private val dispatcher: KaliumDispatcher,
    private val meetingRepository: MeetingRepository,
) : GetPaginatedMeetingOccurrencesUseCase {
    override suspend operator fun invoke(
        pagingConfig: PagingConfig,
        startingOffset: Long,
        from: Instant,
    ): Flow<PagingData<MeetingOccurrence>> = meetingRepository.getPaginatedMeetingOccurrences(
        pagingConfig = pagingConfig,
        startingOffset = startingOffset,
        from = from,
    ).flowOn(dispatcher.io)
}
