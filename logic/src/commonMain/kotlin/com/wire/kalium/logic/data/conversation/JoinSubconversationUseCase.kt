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
package com.wire.kalium.logic.data.conversation

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.id.SubconversationId
import com.wire.kalium.logic.data.id.toApi
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.flatMapLeft
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.sync.receiver.conversation.message.MLSMessageFailureHandler
import com.wire.kalium.logic.sync.receiver.conversation.message.MLSMessageFailureResolution
import com.wire.kalium.logic.sync.receiver.conversation.message.MLSMessageUnpacker
import com.wire.kalium.common.error.wrapApiRequest
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationApi
import com.wire.kalium.network.api.authenticated.conversation.SubconversationDeleteRequest
import com.wire.kalium.network.api.authenticated.conversation.SubconversationResponse
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.exceptions.isMlsStaleMessage
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.toInstant
import kotlin.time.Duration

/**
 * Join a sub-conversation of a MLS conversation
 */
internal interface JoinSubconversationUseCase {
    suspend operator fun invoke(conversationId: ConversationId, subconversationId: SubconversationId): Either<CoreFailure, Unit>
}

// TODO(refactor): usecase should not access API class directly, use SubconversationRepository instead
internal class JoinSubconversationUseCaseImpl(
    private val conversationApi: ConversationApi,
    private val mlsConversationRepository: MLSConversationRepository,
    private val subconversationRepository: SubconversationRepository,
    private val mlsMessageUnpacker: MLSMessageUnpacker,
) : JoinSubconversationUseCase {
    override suspend operator fun invoke(
        conversationId: ConversationId,
        subconversationId: SubconversationId
    ): Either<CoreFailure, Unit> =
        joinOrEstablishSubconversationAndRetry(conversationId, subconversationId)
    private suspend fun joinOrEstablishSubconversation(
        conversationId: ConversationId,
        subconversationId: SubconversationId
    ): Either<CoreFailure, Unit> =
        wrapApiRequest {
            conversationApi.fetchSubconversationDetails(conversationId.toApi(), subconversationId.toApi())
        }.flatMap { subconversationDetails ->
            joinOrEstablishWithSubconversationDetails(subconversationDetails).onSuccess {
                subconversationRepository.insertSubconversation(
                    conversationId,
                    subconversationId,
                    GroupID(subconversationDetails.groupId)
                )
            }
        }

    private suspend fun joinOrEstablishWithSubconversationDetails(
        subconversationDetails: SubconversationResponse
    ): Either<CoreFailure, Unit> =
        if (subconversationDetails.epoch > INITIAL_EPOCH) {
            if (subconversationDetails.timeElapsedSinceLastEpochChange().inWholeHours > STALE_EPOCH_DURATION_IN_HOURS) {
                wrapApiRequest {
                    conversationApi.deleteSubconversation(
                        subconversationDetails.parentId,
                        subconversationDetails.id,
                        SubconversationDeleteRequest(
                            subconversationDetails.epoch,
                            subconversationDetails.groupId
                        )
                    )
                }.flatMap {
                    mlsConversationRepository.establishMLSSubConversationGroup(
                        GroupID(subconversationDetails.groupId),
                        subconversationDetails.parentId.toModel()
                    )
                }
            } else {
                wrapApiRequest {
                    conversationApi.fetchSubconversationGroupInfo(
                        subconversationDetails.parentId,
                        subconversationDetails.id
                    )
                }.flatMap { groupInfo ->
                    mlsConversationRepository.joinGroupByExternalCommit(
                        GroupID(subconversationDetails.groupId),
                        groupInfo

                    ).flatMapLeft {
                        if (MLSMessageFailureHandler.handleFailure(it) is MLSMessageFailureResolution.Ignore) {
                            Either.Right(Unit)
                        } else {
                            Either.Left(it)
                        }
                    }
                }
            }
        } else {
            mlsConversationRepository.establishMLSSubConversationGroup(
                GroupID(subconversationDetails.groupId),
                subconversationDetails.parentId.toModel()
            )
        }

    private suspend fun joinOrEstablishSubconversationAndRetry(
        conversationId: ConversationId,
        subconversationId: SubconversationId
    ): Either<CoreFailure, Unit> =
        joinOrEstablishSubconversation(conversationId, subconversationId)
            .flatMapLeft { failure ->
                if (failure is NetworkFailure.ServerMiscommunication && failure.kaliumException is KaliumException.InvalidRequestError) {
                    if ((failure.kaliumException as KaliumException.InvalidRequestError).isMlsStaleMessage()) {
                        kaliumLogger.w("Epoch out of date for conversation $conversationId, re-fetching and re-trying")
                        joinOrEstablishSubconversation(conversationId, subconversationId)
                    } else {
                        Either.Left(failure)
                    }
                } else {
                    Either.Left(failure)
                }
            }

    companion object {
        const val INITIAL_EPOCH = 0UL
        const val STALE_EPOCH_DURATION_IN_HOURS = 24
    }

}
private fun Instant.timeElapsedUntilNow(): Duration =
    Clock.System.now().minus(this)

private fun SubconversationResponse.timeElapsedSinceLastEpochChange(): Duration =
    epochTimestamp?.toInstant()?.timeElapsedUntilNow() ?: Duration.ZERO
