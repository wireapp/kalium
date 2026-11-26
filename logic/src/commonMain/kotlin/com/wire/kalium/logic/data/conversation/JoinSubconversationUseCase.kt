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
import com.wire.kalium.common.error.MLSFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.error.wrapApiRequest
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.flatMapLeft
import com.wire.kalium.common.functional.map
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.cryptography.MlsCoreCryptoContext
import com.wire.kalium.logic.data.client.CryptoTransactionProvider
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.SubconversationId
import com.wire.kalium.logic.data.id.toApi
import com.wire.kalium.logic.sync.receiver.conversation.message.MLSMessageFailureHandler
import com.wire.kalium.logic.sync.receiver.conversation.message.MLSMessageFailureResolution
import com.wire.kalium.network.api.authenticated.conversation.SubconversationDeleteRequest
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationApi
import kotlin.time.Clock
import kotlin.time.Instant
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
    private val transactionProvider: CryptoTransactionProvider
) : JoinSubconversationUseCase {
    override suspend operator fun invoke(
        conversationId: ConversationId,
        subconversationId: SubconversationId
    ): Either<CoreFailure, Unit> =
        joinSubconversation(conversationId, subconversationId).flatMapLeft { failure ->
            if (failure.isStaleMessage()) {
                kaliumLogger.w("Epoch out of date for conversation $conversationId, re-fetching and re-trying")
                joinSubconversation(conversationId, subconversationId)
            } else {
                Either.Left(failure)
            }
        }

    /**
     * Every request runs before the MLS transaction. That transaction cannot be cancelled on Apple, so a request
     * waiting in it on a slow network kept a call that had already ended holding its account.
     */
    private suspend fun joinSubconversation(
        conversationId: ConversationId,
        subconversationId: SubconversationId
    ): Either<CoreFailure, Unit> =
        fetchSubconversation(conversationId, subconversationId).flatMap { subconversation ->
            prepareJoin(subconversation).flatMap { joinPlan ->
                transactionProvider.mlsTransaction("JoinSubconversation") { mlsContext ->
                    executeJoin(mlsContext, subconversation, joinPlan).onSuccess {
                        subconversationRepository.insertSubconversation(conversationId, subconversationId, subconversation.groupId)
                    }
                }
            }
        }

    private suspend fun fetchSubconversation(
        conversationId: ConversationId,
        subconversationId: SubconversationId
    ): Either<CoreFailure, SubConversation> = wrapApiRequest {
        conversationApi.fetchSubconversationDetails(conversationId.toApi(), subconversationId.toApi())
    }.map { it.toModel() }

    /** Decides how to enter the subconversation and runs the request that decision needs. */
    private suspend fun prepareJoin(subconversation: SubConversation): Either<CoreFailure, JoinPlan> = when {
        subconversation.epoch <= INITIAL_EPOCH -> Either.Right(JoinPlan.Establish)
        subconversation.hasStaleEpoch() -> deleteSubconversation(subconversation).map { JoinPlan.Establish }
        else -> fetchGroupInfo(subconversation).map { JoinPlan.JoinByExternalCommit(it) }
    }

    private suspend fun deleteSubconversation(subconversation: SubConversation): Either<CoreFailure, Unit> =
        wrapApiRequest {
            conversationApi.deleteSubconversation(
                subconversation.parentId.toApi(),
                subconversation.id.toApi(),
                SubconversationDeleteRequest(subconversation.epoch, subconversation.groupId.value)
            )
        }

    private suspend fun fetchGroupInfo(subconversation: SubConversation): Either<CoreFailure, ByteArray> =
        wrapApiRequest {
            conversationApi.fetchSubconversationGroupInfo(subconversation.parentId.toApi(), subconversation.id.toApi())
        }

    private suspend fun executeJoin(
        mlsContext: MlsCoreCryptoContext,
        subconversation: SubConversation,
        joinPlan: JoinPlan
    ): Either<CoreFailure, Unit> = when (joinPlan) {
        JoinPlan.Establish -> mlsConversationRepository.establishMLSSubConversationGroup(
            mlsContext,
            subconversation.groupId,
            subconversation.parentId
        )

        is JoinPlan.JoinByExternalCommit ->
            mlsConversationRepository.joinGroupByExternalCommit(mlsContext, subconversation.groupId, joinPlan.groupInfo)
                .flatMapLeft { failure ->
                    if (MLSMessageFailureHandler.handleFailure(failure) is MLSMessageFailureResolution.Ignore) {
                        Either.Right(Unit)
                    } else {
                        Either.Left(failure)
                    }
                }
    }

    companion object {
        const val INITIAL_EPOCH = 0UL
        const val STALE_EPOCH_DURATION_IN_HOURS = 24
    }
}

/** How to enter a subconversation once the requests it needs have completed. */
private sealed interface JoinPlan {
    /** The subconversation has no group yet, or its stale one was just deleted. */
    data object Establish : JoinPlan
    class JoinByExternalCommit(val groupInfo: ByteArray) : JoinPlan
}

private fun CoreFailure.isStaleMessage(): Boolean =
    this is MLSFailure.MessageRejected && cause is NetworkFailure.MlsMessageRejectedFailure.StaleMessage

private fun SubConversation.hasStaleEpoch(): Boolean =
    timeElapsedSinceLastEpochChange().inWholeHours > JoinSubconversationUseCaseImpl.STALE_EPOCH_DURATION_IN_HOURS

private fun Instant.timeElapsedUntilNow(): Duration =
    Clock.System.now().minus(this)

private fun SubConversation.timeElapsedSinceLastEpochChange(): Duration =
    epochTimestamp?.timeElapsedUntilNow() ?: Duration.ZERO
