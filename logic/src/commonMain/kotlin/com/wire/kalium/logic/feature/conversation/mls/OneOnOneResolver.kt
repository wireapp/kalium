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
package com.wire.kalium.logic.feature.conversation.mls

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.MLSFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.flatMapLeft
import com.wire.kalium.common.functional.fold
import com.wire.kalium.common.functional.foldToEitherWhileRight
import com.wire.kalium.common.functional.left
import com.wire.kalium.common.functional.map
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.cryptography.CryptoTransactionContext
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.sync.IncrementalSyncRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncStatus
import com.wire.kalium.logic.data.user.OtherUser
import com.wire.kalium.logic.data.user.SupportedProtocol
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.protocol.OneOnOneProtocolSelector
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.exceptions.isTooManyRequests
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import io.mockative.Mockable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlin.time.Duration

@Mockable
internal interface OneOnOneResolver {
    suspend fun resolveAllOneOnOneConversations(
        transactionContext: CryptoTransactionContext,
        synchronizeUsers: Boolean = false
    ): Either<CoreFailure, Unit>

    suspend fun scheduleResolveOneOnOneConversationWithUserId(
        transactionContext: CryptoTransactionContext,
        userId: UserId,
        delay: Duration = Duration.ZERO
    ): Job

    /**
     * Resolves a one-on-one conversation with a user based on their userId.
     *
     * @param userId The userId of the other user in the conversation.
     * @param invalidateCurrentKnownProtocols Flag indicating whether to whether it should attempt refreshing the other user's list of
     * supported protocols by fetching from remote. In case of failure, the local result will be used as a fallback.
     * @return Either a [CoreFailure] if there is an error or a [ConversationId] if the resolution is successful.
     */
    suspend fun resolveOneOnOneConversationWithUserId(
        transactionContext: CryptoTransactionContext,
        userId: UserId,
        invalidateCurrentKnownProtocols: Boolean,
    ): Either<CoreFailure, ConversationId>

    /**
     * Resolves a one-on-one conversation with a user.
     *
     * @param user The other user in the conversation.
     * @param invalidateCurrentKnownProtocols Flag indicating whether to whether it should attempt refreshing the other user's list of
     * supported protocols by fetching from remote. In case of failure, the local result will be used as a fallback.
     * @return Either a [CoreFailure] if there is an error or a [ConversationId] if the resolution is successful.
     */
    suspend fun resolveOneOnOneConversationWithUser(
        transactionContext: CryptoTransactionContext,
        user: OtherUser,
        invalidateCurrentKnownProtocols: Boolean,
    ): Either<CoreFailure, ConversationId>
}

@Suppress("LongParameterList")
internal class OneOnOneResolverImpl(
    private val userRepository: UserRepository,
    private val oneOnOneProtocolSelector: OneOnOneProtocolSelector,
    private val oneOnOneMigrator: OneOnOneMigrator,
    private val incrementalSyncRepository: IncrementalSyncRepository,
    private val maxConcurrentResolutions: Int = DEFAULT_MAX_CONCURRENT_RESOLUTIONS,
    private val maxThrottleRetries: Int = DEFAULT_MAX_THROTTLE_RETRIES,
    private val throttleRetryDelayMs: Long = DEFAULT_THROTTLE_RETRY_DELAY_MS,
    kaliumDispatcher: KaliumDispatcher = KaliumDispatcherImpl
) : OneOnOneResolver {

    private val dispatcher = kaliumDispatcher.default.limitedParallelism(1)

    // TODO: inherit the scope of UserSessionScope so it's cancelled if user logs out, etc.
    private val resolveActiveOneOnOneScope = CoroutineScope(dispatcher)

    override suspend fun resolveAllOneOnOneConversations(
        transactionContext: CryptoTransactionContext,
        synchronizeUsers: Boolean
    ): Either<CoreFailure, Unit> = coroutineScope {
        fetchAllOtherUsersIfNeeded(synchronizeUsers).flatMap {
            val usersWithOneOnOne = userRepository.getUsersWithOneOnOneConversation()
            kaliumLogger.i("Resolving one-on-one protocol for ${usersWithOneOnOne.size} user(s)")
            usersWithOneOnOne.chunked(maxConcurrentResolutions).foldToEitherWhileRight(Unit) { batch, _ ->
                resolveBatch(transactionContext, batch)
            }
        }
    }

    private suspend fun resolveBatch(
        transactionContext: CryptoTransactionContext,
        batch: List<OtherUser>,
    ): Either<CoreFailure, Unit> = coroutineScope {
        batch.map { user ->
            async { resolveWithRetry(transactionContext, user) }
        }
    }.foldToEitherWhileRight(Unit) { item, _ ->
        item.await()
    }

    private suspend fun resolveWithRetry(
        transactionContext: CryptoTransactionContext,
        user: OtherUser,
        attempt: Int = 0,
    ): Either<CoreFailure, Unit> {
        return resolveOneOnOneConversationWithUser(
            transactionContext = transactionContext,
            user = user,
            invalidateCurrentKnownProtocols = false
        ).map { }.flatMapLeft { failure ->
            if (failure.isThrottleFailure() && attempt < maxThrottleRetries) {
                val nextAttempt = attempt + 1
                val delayMs = throttleRetryDelayMs * nextAttempt
                kaliumLogger.w(
                    "Resolving one-on-one throttled for ${user.id.toLogString()}; " +
                        "retrying ($nextAttempt/$maxThrottleRetries) in ${delayMs}ms."
                )
                delay(delayMs)
                resolveWithRetry(transactionContext, user, nextAttempt)
            } else {
                handleBatchEntryFailure(failure)
            }
        }
    }

    private fun handleBatchEntryFailure(failure: CoreFailure) = when {
        failure.isThrottleFailure() -> {
            kaliumLogger.w("Resolving one-on-one failed due to throttling, propagating failure.")
            Either.Left(failure)
        }

        failure is CoreFailure.MissingKeyPackages ||
        failure is NetworkFailure.ServerMiscommunication ||
        failure is NetworkFailure.FederatedBackendFailure ||
        failure is CoreFailure.NoCommonProtocolFound ||
        failure is MLSFailure -> {
            kaliumLogger.e("Resolving one-on-one failed $failure, skipping")
            Either.Right(Unit)
        }

        else -> {
            kaliumLogger.e("Resolving one-on-one failed $failure, retrying")
            Either.Left(failure)
        }
    }

    private fun CoreFailure.isThrottleFailure(): Boolean =
        this is NetworkFailure.ServerMiscommunication &&
            (kaliumException as? KaliumException.InvalidRequestError)?.isTooManyRequests() == true

    private suspend fun fetchAllOtherUsersIfNeeded(synchronizeUsers: Boolean) = if (synchronizeUsers) {
        userRepository.fetchAllOtherUsers()
    } else {
        Either.Right(Unit)
    }

    override suspend fun scheduleResolveOneOnOneConversationWithUserId(
        transactionContext: CryptoTransactionContext,
        userId: UserId,
        delay: Duration
    ) =
        resolveActiveOneOnOneScope.launch {
            kaliumLogger.d("Schedule resolving active one-on-one")
            incrementalSyncRepository.incrementalSyncState.first { it is IncrementalSyncStatus.Live }
            delay(delay)
            resolveOneOnOneConversationWithUserId(
                transactionContext = transactionContext,
                userId = userId,
                invalidateCurrentKnownProtocols = true
            )
        }

    override suspend fun resolveOneOnOneConversationWithUserId(
        transactionContext: CryptoTransactionContext,
        userId: UserId,
        invalidateCurrentKnownProtocols: Boolean
    ): Either<CoreFailure, ConversationId> =
        userRepository.getKnownUser(userId).firstOrNull()?.let {
            resolveOneOnOneConversationWithUser(transactionContext, it, invalidateCurrentKnownProtocols)
        } ?: Either.Left(StorageFailure.DataNotFound)

    override suspend fun resolveOneOnOneConversationWithUser(
        transactionContext: CryptoTransactionContext,
        user: OtherUser,
        invalidateCurrentKnownProtocols: Boolean,
    ): Either<CoreFailure, ConversationId> {
        kaliumLogger.i("Resolving one-on-one protocol for ${user.id.toLogString()}")
        if (invalidateCurrentKnownProtocols) {
            userRepository.fetchUsersByIds(setOf(user.id))
        }
        return oneOnOneProtocolSelector.getProtocolForUser(user.id).fold({ coreFailure ->
            if (coreFailure is CoreFailure.NoCommonProtocolFound.OtherNeedToUpdate) {
                kaliumLogger.i("Resolving existing proteus 1:1 as not matching protocol found with: ${user.id.toLogString()}")
                oneOnOneMigrator.migrateExistingProteus(user)
            } else {
                coreFailure.left()
            }
        }, { supportedProtocol ->
            when (supportedProtocol) {
                SupportedProtocol.PROTEUS -> oneOnOneMigrator.migrateToProteus(user)
                SupportedProtocol.MLS -> oneOnOneMigrator.migrateToMLS(transactionContext, user)
            }
        })
    }

    private companion object {
        const val DEFAULT_MAX_CONCURRENT_RESOLUTIONS = 4
        const val DEFAULT_MAX_THROTTLE_RETRIES = 3
        const val DEFAULT_THROTTLE_RETRY_DELAY_MS = 250L
    }
}
