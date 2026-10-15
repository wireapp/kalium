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

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.fold
import com.wire.kalium.logic.data.meeting.MeetingRepository
import com.wire.kalium.util.KaliumDispatcher
import kotlinx.coroutines.withContext

/**
 * Locally prunes ended history and renews the SDK's current recurrence window (through local today + 91 days).
 * Ongoing occurrences are retained regardless of their start day. No network or platform scheduler is required.
 * Callers gate availability, and can run this before observing/refreshing on open or local date/timezone changes.
 */
public interface MaintainMeetingOccurrencesUseCase {
    public suspend operator fun invoke(): Result

    public sealed interface Result {
        public data object Success : Result
        public data class Failure(val coreFailure: CoreFailure) : Result
    }
}

internal class MaintainMeetingOccurrencesUseCaseImpl(
    private val dispatcher: KaliumDispatcher,
    private val meetingRepository: MeetingRepository,
) : MaintainMeetingOccurrencesUseCase {
    override suspend fun invoke(): MaintainMeetingOccurrencesUseCase.Result = withContext(dispatcher.io) {
        meetingRepository.syncMeetingOccurrences().fold(
            { MaintainMeetingOccurrencesUseCase.Result.Failure(it) },
            { MaintainMeetingOccurrencesUseCase.Result.Success },
        )
    }
}
