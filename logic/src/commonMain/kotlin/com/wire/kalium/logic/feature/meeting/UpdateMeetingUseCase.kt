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
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.flatMapLeft
import com.wire.kalium.common.functional.fold
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.common.logger.logStructuredJson
import com.wire.kalium.logger.KaliumLogLevel
import com.wire.kalium.logic.data.client.CryptoTransactionProvider
import com.wire.kalium.logic.data.conversation.ResetMLSConversationUseCase
import com.wire.kalium.logic.data.conversation.mls.MLSAdditionResult
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.MeetingId
import com.wire.kalium.logic.data.meeting.CreateMeeting
import com.wire.kalium.logic.data.meeting.MeetingDataSource.EstablishMLSFailure
import com.wire.kalium.logic.data.meeting.MeetingRepository
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.publicuser.RefreshUsersWithoutMetadataUseCase
import com.wire.kalium.logic.sync.receiver.conversation.message.MLSMessageFailureHandler
import com.wire.kalium.logic.sync.receiver.conversation.message.MLSMessageFailureResolution

/**
 * Use case for updating existing meeting.
 */
public interface UpdateMeetingUseCase {
    public suspend operator fun invoke(meetingId: MeetingId, meeting: CreateMeeting): Result
    public sealed interface Result {
        public data object Success : Result
        public data object Failure : Result // TODO: Add more specific error types in the future
    }
}

internal class UpdateMeetingUseCaseImpl(
    private val meetingRepository: MeetingRepository,
    private val userRepository: UserRepository,
    private val refreshUsersWithoutMetadata: RefreshUsersWithoutMetadataUseCase,
    private val resetMLSConversation: ResetMLSConversationUseCase,
    private val transactionProvider: CryptoTransactionProvider,
) : UpdateMeetingUseCase {
    private val logger = kaliumLogger.withTextTag("UpdateMeetingUseCase")

    override suspend operator fun invoke(meetingId: MeetingId, meeting: CreateMeeting) =
        userRepository.insertOrIgnoreIncompleteUsers(meeting.otherParticipants)
            .flatMap {
                transactionProvider
                    .transaction("UpdateMeeting") { transactionContext ->
                        meetingRepository.updateMeeting(
                            meetingId = meetingId,
                            meeting = meeting,
                            transactionContext = transactionContext
                        ).flatMapLeft { failure ->
                            when (failure) {
                                is EstablishMLSFailure -> {
                                    when (MLSMessageFailureHandler.handleFailure(failure.reason)) {
                                        is MLSMessageFailureResolution.Ignore -> {
                                            logger.logStructuredJson(
                                                level = KaliumLogLevel.WARN,
                                                leadingMessage = "Update Meeting external commit Ignored",
                                                jsonStringKeyValues = logData(meetingId, failure.conversationId, failure)
                                            )
                                        }

                                        is MLSMessageFailureResolution.ResetConversation -> {
                                            logger.logStructuredJson(
                                                level = KaliumLogLevel.WARN,
                                                leadingMessage = "Reset Conversation after update Meeting failure",
                                                jsonStringKeyValues = logData(meetingId, failure.conversationId, failure)
                                            )
                                            resetMLSConversation(
                                                conversationId = failure.conversationId,
                                                transactionContext = transactionContext,
                                            )
                                        }

                                        else -> {
                                            logger.logStructuredJson(
                                                level = KaliumLogLevel.ERROR,
                                                leadingMessage = "Update Meeting external commit Failure",
                                                jsonStringKeyValues = logData(meetingId, failure.conversationId, failure)
                                            )
                                        }
                                    }
                                    // don't propagate the MLS establishment error, edit succeeded, MLS establishment can be retried later
                                    Either.Right(MLSAdditionResult.Empty)
                                }

                                else -> Either.Left(failure)
                            }
                        }
                    }
            }
            .onSuccess {
                refreshUsersWithoutMetadata()
            }
            .fold({ UpdateMeetingUseCase.Result.Failure }, { UpdateMeetingUseCase.Result.Success })

    private fun logData(meetingId: MeetingId, conversationId: ConversationId, failure: CoreFailure? = null): Map<String, Any> = buildMap {
        put("meetingId", meetingId.toLogString())
        put("conversationId", conversationId.toLogString())
        failure?.let { put("errorInfo", "$it") }
    }
}
