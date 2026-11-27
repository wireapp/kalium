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

package com.wire.kalium.logic.feature.meeting

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMapLeft
import com.wire.kalium.common.functional.fold
import com.wire.kalium.common.functional.map
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.MeetingId
import com.wire.kalium.logic.data.client.CryptoTransactionProvider
import com.wire.kalium.logic.data.meeting.CreateMeeting
import com.wire.kalium.logic.data.meeting.MeetingDataSource.SavedMeetingFailure
import com.wire.kalium.logic.data.meeting.MeetingDataSource.EstablishMLSFailure
import com.wire.kalium.logic.data.meeting.MeetingRepository
import com.wire.kalium.logic.feature.publicuser.RefreshUsersWithoutMetadataUseCase

/**
 * Use case for creating a new meeting.
 */
public interface CreateNewMeetingUseCase {
    public suspend operator fun invoke(createMeeting: CreateMeeting): Result
    public sealed interface Result {
        public sealed interface Success : Result {
            public val conversationId: ConversationId
            public val meetingId: MeetingId

            public data class Complete(
                override val conversationId: ConversationId,
                override val meetingId: MeetingId,
            ) : Success

            public data class Partial(
                override val conversationId: ConversationId,
                override val meetingId: MeetingId,
                val warning: MeetingSaveWarning,
            ) : Success
        }
        public data class Failure(val coreFailure: CoreFailure) : Result
    }
}

internal class CreateNewMeetingUseCaseImpl(
    private val meetingRepository: MeetingRepository,
    private val refreshUsersWithoutMetadata: RefreshUsersWithoutMetadataUseCase,
    private val transactionProvider: CryptoTransactionProvider,
) : CreateNewMeetingUseCase {

    override suspend operator fun invoke(createMeeting: CreateMeeting) = transactionProvider
        .transaction("CreateNewMeeting") { transactionContext ->
            meetingRepository.createNewMeeting(meeting = createMeeting, transactionContext = transactionContext)
        }
        .map {
            when (val warning = it.mlsAdditionResult.toMeetingSaveWarning()) {
                null -> CreateNewMeetingUseCase.Result.Success.Complete(it.conversationId, it.meetingId)
                else -> CreateNewMeetingUseCase.Result.Success.Partial(it.conversationId, it.meetingId, warning)
            }
        }
        .flatMapLeft { failure ->
            when (failure) {
                // The meeting was created; report partial success because MLS establishment can be retried later.
                is EstablishMLSFailure -> Either.Right(
                    CreateNewMeetingUseCase.Result.Success.Partial(
                        conversationId = failure.conversationId,
                        meetingId = failure.meetingId,
                        warning = failure.reason.toSetupWarning(createMeeting.otherParticipants)
                    )
                )
                is SavedMeetingFailure -> Either.Right(
                    CreateNewMeetingUseCase.Result.Success.Partial(
                        conversationId = failure.conversationId,
                        meetingId = failure.meetingId,
                        warning = failure.toMeetingSaveWarning()
                    )
                )
                else -> Either.Left(CreateNewMeetingUseCase.Result.Failure(failure))
            }
        }
        .onSuccess { refreshUsersWithoutMetadata() }
        .fold({ it }, { it })
}
