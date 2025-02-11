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

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.sync.IncrementalSyncRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncStatus
import com.wire.kalium.logic.data.user.OtherUser
import com.wire.kalium.logic.data.user.SupportedProtocol
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.protocol.OneOnOneProtocolSelector
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.flatMapLeft
import com.wire.kalium.common.functional.fold
import com.wire.kalium.common.functional.foldToEitherWhileRight
import com.wire.kalium.common.functional.left
import com.wire.kalium.common.functional.map
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlin.time.Duration

interface OneOnOneResolver {
    suspend fun resolveAllOneOnOneConversations(synchronizeUsers: Boolean = false): Either<CoreFailure, Unit>
    suspend fun scheduleResolveOneOnOneConversationWithUserId(userId: UserId, delay: Duration = Duration.ZERO): Job

    /**
     * Resolves a one-on-one conversation with a user based on their userId.
     *
     * @param userId The userId of the other user in the conversation.
     * @param invalidateCurrentKnownProtocols Flag indicating whether to whether it should attempt refreshing the other user's list of
     * supported protocols by fetching from remote. In case of failure, the local result will be used as a fallback.
     * @return Either a [CoreFailure] if there is an error or a [ConversationId] if the resolution is successful.
     */
    suspend fun resolveOneOnOneConversationWithUserId(
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

    @OptIn(ExperimentalCoroutinesApi::class)
    private val dispatcher = kaliumDispatcher.default.limitedParallelism(1)

    // TODO: inherit the scope of UserSessionScope so it's cancelled if user logs out, etc.
    private val resolveActiveOneOnOneScope = CoroutineScope(dispatcher)

    override suspend fun resolveAllOneOnOneConversations(synchronizeUsers: Boolean): Either<CoreFailure, Unit> =
        fetchAllOtherUsersIfNeeded(synchronizeUsers).flatMap {
            val usersWithOneOnOne = userRepository.getUsersWithOneOnOneConversation()
            kaliumLogger.i("Resolving one-on-one protocol for ${usersWithOneOnOne.size} user(s)")
            usersWithOneOnOne.foldToEitherWhileRight(Unit) { item, _ ->
                resolveOneOnOneConversationWithUser(
                    user = item,
                    // Either it fetched all users on the previous step, or it's not needed
                    invalidateCurrentKnownProtocols = false
                ).flatMapLeft {
                    handleBatchEntryFailure(it)
                }.map { }
            }
        }

    private fun handleBatchEntryFailure(it: CoreFailure) = when (it) {
        is CoreFailure.MissingKeyPackages,
        is NetworkFailure.ServerMiscommunication,
        is NetworkFailure.FederatedBackendFailure,
        is CoreFailure.NoCommonProtocolFound
        -> {
            kaliumLogger.e("Resolving one-on-one failed $it, skipping")
            Either.Right(Unit)
        }

        else -> {
            kaliumLogger.e("Resolving one-on-one failed $it, retrying")
            Either.Left(it)
        }
    }

    private suspend fun fetchAllOtherUsersIfNeeded(synchronizeUsers: Boolean) = if (synchronizeUsers) {
        userRepository.fetchAllOtherUsers()
    } else {
        Either.Right(Unit)
    }

    override suspend fun scheduleResolveOneOnOneConversationWithUserId(userId: UserId, delay: Duration) =
        resolveActiveOneOnOneScope.launch {
            kaliumLogger.d("Schedule resolving active one-on-one")
            incrementalSyncRepository.incrementalSyncState.first { it is IncrementalSyncStatus.Live }
            delay(delay)
            resolveOneOnOneConversationWithUserId(
                userId = userId,
                invalidateCurrentKnownProtocols = true
            )
        }

    override suspend fun resolveOneOnOneConversationWithUserId(
        userId: UserId,
        invalidateCurrentKnownProtocols: Boolean
    ): Either<CoreFailure, ConversationId> =
        userRepository.getKnownUser(userId).firstOrNull()?.let {
            resolveOneOnOneConversationWithUser(it, invalidateCurrentKnownProtocols)
        } ?: Either.Left(StorageFailure.DataNotFound)

    override suspend fun resolveOneOnOneConversationWithUser(
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
                SupportedProtocol.MLS -> oneOnOneMigrator.migrateToMLS(user)
            }
        })
    }
}
