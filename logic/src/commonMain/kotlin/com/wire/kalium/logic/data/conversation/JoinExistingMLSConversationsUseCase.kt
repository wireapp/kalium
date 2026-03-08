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
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.flatMapLeft
import com.wire.kalium.common.functional.foldToEitherWhileRight
import com.wire.kalium.common.functional.getOrElse
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.cryptography.CryptoTransactionContext
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.client.CryptoTransactionProvider
import com.wire.kalium.logic.data.conversation.Conversation.ProtocolInfo.MLSCapable.GroupState
import com.wire.kalium.logic.featureFlags.FeatureSupport
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.exceptions.isTooManyRequests
import io.mockative.Mockable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay

/**
 * Send an external commit to join all MLS conversations for which the user is a member,
 * but has not yet joined the corresponding MLS group.
 */
@Mockable
internal interface JoinExistingMLSConversationsUseCase {
    suspend operator fun invoke(keepRetryingOnFailure: Boolean = true): Either<CoreFailure, Unit>
}

@Suppress("LongParameterList")
internal class JoinExistingMLSConversationsUseCaseImpl(
    private val featureSupport: FeatureSupport,
    private val clientRepository: ClientRepository,
    private val conversationRepository: ConversationRepository,
    private val joinExistingMLSConversationUseCase: JoinExistingMLSConversationUseCase,
    private val transactionProvider: CryptoTransactionProvider,
    private val maxConcurrentJoins: Int = DEFAULT_MAX_CONCURRENT_JOINS,
    private val maxThrottleRetries: Int = DEFAULT_MAX_THROTTLE_RETRIES,
    private val throttleRetryDelayMs: Long = DEFAULT_THROTTLE_RETRY_DELAY_MS,
) : JoinExistingMLSConversationsUseCase {

    init {
        require(maxConcurrentJoins > 0) { "maxConcurrentJoins must be greater than zero" }
        require(maxThrottleRetries >= 0) { "maxThrottleRetries must not be negative" }
        require(throttleRetryDelayMs >= 0) { "throttleRetryDelayMs must not be negative" }
    }

    override suspend operator fun invoke(keepRetryingOnFailure: Boolean): Either<CoreFailure, Unit> =
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
                        joinBatch(transactionContext, batch, keepRetryingOnFailure)
                    }
                }
            }
        }

    private suspend fun joinBatch(
        transactionContext: CryptoTransactionContext,
        batch: List<Conversation>,
        keepRetryingOnFailure: Boolean,
    ): Either<CoreFailure, Unit> = coroutineScope {
        batch.map { conversation ->
            async {
                joinRetrying(transactionContext, conversation, keepRetryingOnFailure)
            }
        }
    }.foldToEitherWhileRight(Unit) { value, _ ->
        value.await()
    }

    private suspend fun joinRetrying(
        transactionContext: CryptoTransactionContext,
        conversation: Conversation,
        keepRetryingOnFailure: Boolean,
        attempt: Int = 0,
    ): Either<CoreFailure, Unit> {
        return when (val result = joinExistingMLSConversationUseCase(transactionContext, conversation.id)) {
            is Either.Left -> {
                val failure = result.value
                if (keepRetryingOnFailure && failure.isThrottleFailure() && attempt < maxThrottleRetries) {
                    val nextAttempt = attempt + 1
                    val delayMs = throttleRetryDelayMs * nextAttempt
                    kaliumLogger.w(
                        "Failed to establish mls group for ${conversation.id.toLogString()} due to throttling; " +
                            "retrying (${nextAttempt}/${maxThrottleRetries}) in ${delayMs}ms."
                    )
                    delay(delayMs)
                    joinRetrying(transactionContext, conversation, keepRetryingOnFailure, nextAttempt)
                } else {
                    handleJoinFailure(failure, conversation)
                }
            }

            is Either.Right -> Either.Right(Unit)
        }
    }

    private fun handleJoinFailure(failure: CoreFailure, conversation: Conversation): Either<CoreFailure, Unit> =
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
                } else {
                    kaliumLogger.w(
                        "Failed to establish mls group for ${conversation.id.toLogString()} due to network failure"
                    )
                    Either.Left(failure)
                }
            }

            is CoreFailure.MissingKeyPackages -> {
                kaliumLogger.w(
                    "Failed to establish mls group for ${conversation.id.toLogString()} " +
                        "since some participants are out of key packages, skipping."
                )
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

    private companion object {
        const val DEFAULT_MAX_CONCURRENT_JOINS = 4
        const val DEFAULT_MAX_THROTTLE_RETRIES = 3
        const val DEFAULT_THROTTLE_RETRY_DELAY_MS = 250L
    }
}
