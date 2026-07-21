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
import com.wire.kalium.logic.data.id.MeetingId
import com.wire.kalium.logic.data.meeting.MeetingRepository

/**
 * Use case for deleting a meeting by its ID.
 */
public interface DeleteMeetingUseCase {
    public suspend operator fun invoke(meetingId: MeetingId): Result

    public sealed interface Result {
        public data object Success : Result
        public data class Failure(val coreFailure: CoreFailure) : Result
    }
}

internal class DeleteMeetingUseCaseImpl(
    private val meetingRepository: MeetingRepository,
) : DeleteMeetingUseCase {
    override suspend operator fun invoke(meetingId: MeetingId): DeleteMeetingUseCase.Result {
        return meetingRepository.deleteMeeting(meetingId).fold({
            DeleteMeetingUseCase.Result.Failure(it)
        }, {
            DeleteMeetingUseCase.Result.Success
        })
    }
}
