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

import com.wire.kalium.logic.data.meeting.MeetingRepository
import com.wire.kalium.util.KaliumDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlin.time.Instant

/** Observes all list-eligible occurrences in inclusive bounds, independently of loaded pages.
 * Callers supply the list cutoff and grace-adjusted end boundary, and replace the observation as time changes.
 */
public fun interface ObserveHasOngoingMeetingUseCase {
    public operator fun invoke(from: Instant, now: Instant, earliestEnd: Instant): Flow<Boolean>
}

internal class ObserveHasOngoingMeetingUseCaseImpl(
    private val dispatcher: KaliumDispatcher,
    private val meetingRepository: MeetingRepository,
) : ObserveHasOngoingMeetingUseCase {
    override fun invoke(from: Instant, now: Instant, earliestEnd: Instant): Flow<Boolean> =
        meetingRepository.observeHasOngoingMeeting(from, now, earliestEnd).flowOn(dispatcher.io)
}
