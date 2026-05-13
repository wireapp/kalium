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
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.foldToEitherWhileRight
import com.wire.kalium.common.functional.getOrElse
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.cryptography.CryptoTransactionContext
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.client.CryptoTransactionProvider
import com.wire.kalium.logic.data.conversation.Conversation.ProtocolInfo.MLSCapable.GroupState
import com.wire.kalium.logic.data.conversation.mls.PendingActionsRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.featureFlags.FeatureSupport
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.exceptions.isTooManyRequests
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay

/**
 * Send an external commit to join all MLS conversations for which the user is a member,
 * but has not yet joined the corresponding MLS group.
 */
internal interface JoinExistingMLSConversationsUseCase {
    suspend operator fun invoke(
        keepRetryingOnFailure: Boolean = true,
        allowJoinByExternalCommit: Boolean = true,
    ): Either<CoreFailure, Unit>
}

@Suppress("LongParameterList")
internal class JoinExistingMLSConversationsUseCaseImpl(
    private val featureSupport: FeatureSupport,
    private val clientRepository: ClientRepository,
    private val conversationRepository: ConversationRepository,
    private val joinExistingMLSConversationUseCase: JoinExistingMLSConversationUseCase,
    private val transactionProvider: CryptoTransactionProvider,
    private val pendingActionsRepository: PendingActionsRepository,
    private val maxConcurrentJoins: Int = DEFAULT_MAX_CONCURRENT_JOINS,
    private val maxThrottleRetries: Int = DEFAULT_MAX_THROTTLE_RETRIES,
    private val throttleRetryDelayMs: Long = DEFAULT_THROTTLE_RETRY_DELAY_MS,
) : JoinExistingMLSConversationsUseCase {

    init {
        require(maxConcurrentJoins > 0) { "maxConcurrentJoins must be greater than zero" }
        require(maxThrottleRetries >= 0) { "maxThrottleRetries must not be negative" }
        require(throttleRetryDelayMs >= 0) { "throttleRetryDelayMs must not be negative" }
    }

    override suspend operator fun invoke(
        keepRetryingOnFailure: Boolean,
        allowJoinByExternalCommit: Boolean,
    ): Either<CoreFailure, Unit> =
        if (!featureSupport.isMLSSupported ||
            !clientRepository.hasRegisteredMLSClient().getOrElse(false)
        ) {
            kaliumLogger.i("Skip re-join existing MLS conversation(s), since MLS is not supported.")
            Either.Right(Unit)
        } else {
            transactionProvider.transaction("JoinExistingMLSConversations") { transactionContext ->
                conversationRepository.getConversationsByGroupState(GroupState.PENDING_JOIN).flatMap { pendingConversations ->
                    kaliumLogger.d("Requesting to re-join ${pendingConversations.size} existing MLS conversation(s)")
                    pendingConversations.chunked(maxConcurrentJoins).foldToEitherWhileRight(Unit) { batch, _ ->
                        joinBatch(transactionContext, batch, keepRetryingOnFailure, allowJoinByExternalCommit)
                    }
                }
            }
        }

    private suspend fun joinBatch(
        transactionContext: CryptoTransactionContext,
        batch: List<Conversation>,
        keepRetryingOnFailure: Boolean,
        allowJoinByExternalCommit: Boolean,
    ): Either<CoreFailure, Unit> = coroutineScope {
        batch.map { conversation ->
            async {
                joinRetrying(transactionContext, conversation, keepRetryingOnFailure, allowJoinByExternalCommit)
            }
        }
    }.foldToEitherWhileRight(Unit) { value, _ ->
        value.await()
    }

    private suspend fun joinRetrying(
        transactionContext: CryptoTransactionContext,
        conversation: Conversation,
        keepRetryingOnFailure: Boolean,
        allowJoinByExternalCommit: Boolean,
        attempt: Int = 0,
    ): Either<CoreFailure, Unit> {
        return when (
            val result = joinExistingMLSConversationUseCase(
                transactionContext = transactionContext,
                conversationId = conversation.id,
                allowJoinByExternalCommit = allowJoinByExternalCommit
            )
        ) {
            is Either.Left -> {
                val failure = result.value
                if (keepRetryingOnFailure && failure.isThrottleFailure() && attempt < maxThrottleRetries) {
                    val nextAttempt = attempt + 1
                    val delayMs = throttleRetryDelayMs * nextAttempt
                    kaliumLogger.w(
                        "Failed to establish mls group for ${conversation.id.toLogString()} due to throttling; " +
                            "retrying ($nextAttempt/$maxThrottleRetries) in ${delayMs}ms."
                    )
                    delay(delayMs)
                    joinRetrying(
                        transactionContext = transactionContext,
                        conversation = conversation,
                        keepRetryingOnFailure = keepRetryingOnFailure,
                        allowJoinByExternalCommit = allowJoinByExternalCommit,
                        attempt = nextAttempt
                    )
                } else {
                    handleJoinFailure(failure, conversation)
                }
            }

            is Either.Right -> Either.Right(Unit)
        }
    }

    private suspend fun handleJoinFailure(failure: CoreFailure, conversation: Conversation): Either<CoreFailure, Unit> =
        when (failure) {
            is NetworkFailure -> {
                if (failure.isThrottleFailure()) {
                    kaliumLogger.w(
                        "Failed to establish mls group for ${conversation.id.toLogString()} " +
                            "due to throttling, propagating failure."
                    )
                    Either.Left(failure)
                } else if (failure is NetworkFailure.ServerMiscommunication
                    && failure.kaliumException is KaliumException.InvalidRequestError
                ) {
                    kaliumLogger.w(
                        "Failed to establish mls group for ${conversation.id.toLogString()} " +
                            "due to invalid request error, skipping."
                    )
                    Either.Right(Unit)
                } else if (failure is NetworkFailure.FederatedBackendFailure.RetryableFailure) {
                    enqueueForForegroundRecovery(conversation.id, failure)
                    Either.Right(Unit)
                } else if (failure is NetworkFailure.FederatedBackendFailure) {
                    kaliumLogger.w(
                        "Failed to establish mls group for ${conversation.id.toLogString()} " +
                            "due to federated backend failure, skipping."
                    )
                    Either.Right(Unit)
                } else {
                    kaliumLogger.w(
                        "Failed to establish mls group for ${conversation.id.toLogString()} due to network failure"
                    )
                    Either.Left(failure)
                }
            }

            is CoreFailure.MissingKeyPackages -> {
                enqueueForForegroundRecovery(conversation.id, failure)
                Either.Right(Unit)
            }

            is MLSFailure.MessageRejected -> {
                if (failure.cause == NetworkFailure.MlsMessageRejectedFailure.StaleMessage) {
                    enqueueForForegroundRecovery(conversation.id, failure)
                } else {
                    kaliumLogger.w(
                        "Failed to establish mls group for ${conversation.id.toLogString()} due to $failure, skipping.."
                    )
                }
                Either.Right(Unit)
            }

            else -> {
                kaliumLogger.w(
                    "Failed to establish mls group for ${conversation.id.toLogString()} due to $failure, skipping.."
                )
                Either.Right(Unit)
            }
        }

    private fun CoreFailure.isThrottleFailure(): Boolean =
        this is NetworkFailure.ServerMiscommunication &&
            (kaliumException as? KaliumException.InvalidRequestError)?.isTooManyRequests() == true

    private suspend fun enqueueForForegroundRecovery(conversationId: ConversationId, failure: CoreFailure) {
        kaliumLogger.w(
            "Failed to establish mls group for ${conversationId.toLogString()} due to $failure, " +
                "adding retry for next foreground action."
        )
        pendingActionsRepository.enqueuePendingMLSGroupJoin(conversationId)
    }

    private companion object {
        const val DEFAULT_MAX_CONCURRENT_JOINS = 4
        const val DEFAULT_MAX_THROTTLE_RETRIES = 3
        const val DEFAULT_THROTTLE_RETRY_DELAY_MS = 250L
    }
}
