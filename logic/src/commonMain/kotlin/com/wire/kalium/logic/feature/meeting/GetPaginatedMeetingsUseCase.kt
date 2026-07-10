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
import com.wire.kalium.util.KaliumDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * This use case observes and returns a flow of paginated meeting occurrences.
 */
public class GetPaginatedMeetingsUseCase internal constructor(
    private val dispatcher: KaliumDispatcher,
    private val meetingRepository: MeetingRepository,
) {
    public suspend operator fun invoke(
        pagingConfig: PagingConfig,
        startingOffset: Long,
        fromDate: Instant = Clock.System.now()
    ): Flow<PagingData<MeetingOccurrence>> = meetingRepository.getPaginatedMeetings(
        pagingConfig = pagingConfig,
        startingOffset = startingOffset,
        fromDate = fromDate
    ).flowOn(dispatcher.io)
}
