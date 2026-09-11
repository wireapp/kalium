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

import com.wire.kalium.common.functional.getOrNull
import com.wire.kalium.logic.data.id.MeetingId
import com.wire.kalium.logic.data.meeting.MeetingOccurrence
import com.wire.kalium.logic.data.meeting.MeetingRepository
import com.wire.kalium.util.DateTimeUtil.currentInstant
import com.wire.kalium.util.KaliumDispatcher
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant

/**
 * Use case for finding the next soonest unfinished occurrence of a meeting at the given point in time.
 * Unfinished occurrences are those that have not yet ended at the given point in time.
 * If there is a currently ongoing occurrence, it will be returned, otherwise the next upcoming occurrence will be returned.
 * If there are no unfinished occurrences, null will be returned.
 */
public interface GetNextUnfinishedMeetingOccurrenceUseCase {
    public suspend operator fun invoke(meetingId: MeetingId, from: Instant = currentInstant()): MeetingOccurrence?
}

internal class GetNextUnfinishedMeetingOccurrenceUseCaseImpl(
    private val dispatcher: KaliumDispatcher,
    private val meetingRepository: MeetingRepository,
) : GetNextUnfinishedMeetingOccurrenceUseCase {
    override suspend operator fun invoke(meetingId: MeetingId, from: Instant): MeetingOccurrence? = withContext(dispatcher.io) {
        meetingRepository.getNextUnfinishedMeetingOccurrence(meetingId, from).getOrNull()
    }
}
