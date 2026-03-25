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
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import io.mockative.Mockable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
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

internal class OneOnOneResolverImpl(
    private val userRepository: UserRepository,
    private val oneOnOneProtocolSelector: OneOnOneProtocolSelector,
    private val oneOnOneMigrator: OneOnOneMigrator,
    private val incrementalSyncRepository: IncrementalSyncRepository,
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
            usersWithOneOnOne.map { item ->
                async {
                    resolveOneOnOneConversationWithUser(
                        transactionContext = transactionContext,
                        user = item,
                        // Either it fetched all users on the previous step, or it's not needed
                        invalidateCurrentKnownProtocols = false
                    ).map { }.flatMapLeft {
                        handleBatchEntryFailure(transactionContext, item, it)
                    }
                }
            }.foldToEitherWhileRight(Unit) { item, _ ->
                item.await()
            }
        }
    }

    private suspend fun handleBatchEntryFailure(
        transactionContext: CryptoTransactionContext,
        user: OtherUser,
        failure: CoreFailure
    ) = when (failure) {
        is CoreFailure.MissingKeyPackages,
        is CoreFailure.NoCommonProtocolFound -> {
            if (failure is CoreFailure.MissingKeyPackages) {
                kaliumLogger.w(
                    "Resolving one-on-one failed $failure for ${user.id.toLogString()}, " +
                        "scheduling retry after incremental sync is live"
                )
                scheduleResolveOneOnOneConversationWithUserId(transactionContext, user.id)
            } else {
                kaliumLogger.e("Resolving one-on-one failed $failure, skipping")
            }
            Either.Right(Unit)
        }

        is NetworkFailure.FederatedBackendFailure.RetryableFailure -> {
            kaliumLogger.w(
                "Resolving one-on-one failed $failure for ${user.id.toLogString()}, " +
                    "scheduling retry after incremental sync is live"
            )
            scheduleResolveOneOnOneConversationWithUserId(transactionContext, user.id)
            Either.Right(Unit)
        }

        is NetworkFailure.FederatedBackendFailure -> {
            kaliumLogger.e("Resolving one-on-one failed $failure, skipping")
            Either.Right(Unit)
        }

        is NetworkFailure.ServerMiscommunication -> {
            if (failure.kaliumException is KaliumException.InvalidRequestError) {
                kaliumLogger.e("Resolving one-on-one failed $failure, skipping")
                Either.Right(Unit)
            } else {
                kaliumLogger.w("Resolving one-on-one failed $failure, retrying")
                Either.Left(failure)
            }
        }

        is MLSFailure.MessageRejected -> {
            if (failure.cause == NetworkFailure.MlsMessageRejectedFailure.StaleMessage) {
                kaliumLogger.w(
                    "Resolving one-on-one failed $failure for ${user.id.toLogString()}, " +
                        "scheduling retry after incremental sync is live"
                )
                scheduleResolveOneOnOneConversationWithUserId(transactionContext, user.id)
            } else {
                // MessageRejected can still leave the local active 1:1 pointer unresolved until
                // another explicit resolve trigger happens. We currently prefer to keep the batch
                // moving instead of retrying the entire one-on-one resolution here.
                kaliumLogger.e("Resolving one-on-one failed $failure, skipping")
            }
            Either.Right(Unit)
        }

        else -> {
            kaliumLogger.w(
                "Resolving one-on-one failed $failure, retrying"
            )
            Either.Left(failure)
        }
    }

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
            val syncReachedLive = incrementalSyncRepository.incrementalSyncState.firstOrNull { it is IncrementalSyncStatus.Live }
            if (syncReachedLive == null) {
                kaliumLogger.w("Skipping scheduled active one-on-one resolve for ${userId.toLogString()} because sync state flow completed")
                return@launch
            }
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
}
