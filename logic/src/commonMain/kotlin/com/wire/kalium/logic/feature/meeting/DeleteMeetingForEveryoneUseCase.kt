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

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.fold
import com.wire.kalium.common.functional.map
import com.wire.kalium.logic.data.client.CryptoTransactionProvider
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.MeetingId
import com.wire.kalium.logic.data.meeting.MeetingDataSource
import com.wire.kalium.logic.data.meeting.MeetingRepository
import io.ktor.http.HttpStatusCode
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.logic.feature.conversation.delete.DeleteConversationUseCase

/**
 * Use case for deleting a meeting for every participant by its ID.
 */
public interface DeleteMeetingForEveryoneUseCase {
    public suspend operator fun invoke(meetingId: MeetingId): Result

    public sealed interface Result {
        public data object Success : Result
        public sealed interface Failure : Result {
            public data class CleanupFailed(val coreFailure: CoreFailure) : Failure
            public data object NotAllowed : Failure
            public data class Other(val coreFailure: CoreFailure) : Failure
        }
    }
}

internal class DeleteMeetingForEveryoneUseCaseImpl(
    private val meetingRepository: MeetingRepository,
    private val conversationRepository: ConversationRepository,
    private val deleteConversation: DeleteConversationUseCase,
    private val transactionProvider: CryptoTransactionProvider,
) : DeleteMeetingForEveryoneUseCase {
    override suspend operator fun invoke(meetingId: MeetingId): DeleteMeetingForEveryoneUseCase.Result =
        meetingRepository.getMeeting(meetingId).flatMap { meeting ->
            meetingRepository.deleteMeeting(meeting.meetingId).map { meeting }
        }.fold({
            it.toDeleteResult()
        }, { meeting ->
            conversationRepository.getConversationById(meeting.conversationId).flatMap { conversation ->
                if (conversation.type == Conversation.Type.Group.Meeting) {
                    transactionProvider.transaction("DeleteMeetingForEveryone") { transactionContext ->
                        deleteConversation(transactionContext, meeting.conversationId)
                    }
                } else {
                    Either.Right(Unit)
                }
            }.fold(
                {
                    if (it == StorageFailure.DataNotFound) DeleteMeetingForEveryoneUseCase.Result.Success
                    else DeleteMeetingForEveryoneUseCase.Result.Failure.CleanupFailed(it)
                },
                { DeleteMeetingForEveryoneUseCase.Result.Success }
            )
        })

    private fun CoreFailure.toDeleteResult(): DeleteMeetingForEveryoneUseCase.Result.Failure = when {
        this is MeetingDataSource.DeleteCleanupFailure -> DeleteMeetingForEveryoneUseCase.Result.Failure.CleanupFailed(reason)
        this is NetworkFailure.ServerMiscommunication &&
            (kaliumException as? KaliumException.InvalidRequestError)?.errorResponse?.code in listOf(
                HttpStatusCode.Forbidden.value, HttpStatusCode.NotFound.value, HttpStatusCode.Gone.value
            ) ->
            DeleteMeetingForEveryoneUseCase.Result.Failure.NotAllowed
        else -> DeleteMeetingForEveryoneUseCase.Result.Failure.Other(this)
    }
}
