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
import com.wire.kalium.logic.feature.conversation.LeaveConversationUseCase
import com.wire.kalium.logic.feature.conversation.RemoveMemberFromConversationUseCase
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Use case for deleting a meeting only for the self user.
 */
public interface DeleteMeetingForMeUseCase {
    public suspend operator fun invoke(meetingId: MeetingId): Result

    public sealed interface Result {
        public data object Success : Result
        public data class Failure(val coreFailure: CoreFailure) : Result
    }
}

internal class DeleteMeetingForMeUseCaseImpl(
    private val meetingRepository: MeetingRepository,
    private val leaveConversation: LeaveConversationUseCase,
) : DeleteMeetingForMeUseCase {
    override suspend operator fun invoke(meetingId: MeetingId): DeleteMeetingForMeUseCase.Result = withContext(NonCancellable) {
        meetingRepository.getMeeting(meetingId).fold(
            {
                DeleteMeetingForMeUseCase.Result.Failure(it)
            },
            { meeting ->
                when (val result = leaveConversation(meeting.conversationId)) {
                    is RemoveMemberFromConversationUseCase.Result.Failure -> DeleteMeetingForMeUseCase.Result.Failure(result.cause)
                    is RemoveMemberFromConversationUseCase.Result.Success -> meetingRepository.deleteMeetingLocally(meetingId)
                        .fold({ DeleteMeetingForMeUseCase.Result.Failure(it) }, { DeleteMeetingForMeUseCase.Result.Success })
                }
            }
        )
    }
}
