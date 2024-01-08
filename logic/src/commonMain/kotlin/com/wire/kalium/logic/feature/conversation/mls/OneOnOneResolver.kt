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
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.flatMapLeft
import com.wire.kalium.logic.functional.foldToEitherWhileRight
import com.wire.kalium.logic.functional.map
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
    suspend fun resolveOneOnOneConversationWithUserId(userId: UserId): Either<CoreFailure, ConversationId>
    suspend fun resolveOneOnOneConversationWithUser(user: OtherUser): Either<CoreFailure, ConversationId>
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
    private val resolveActiveOneOnOneScope = CoroutineScope(dispatcher)

    override suspend fun resolveAllOneOnOneConversations(synchronizeUsers: Boolean): Either<CoreFailure, Unit> =
        if (synchronizeUsers) {
            userRepository.fetchAllOtherUsers()
        } else {
            Either.Right(Unit)
        }.flatMap {
            val usersWithOneOnOne = userRepository.getUsersWithOneOnOneConversation()
            kaliumLogger.i("Resolving one-on-one protocol for ${usersWithOneOnOne.size} user(s)")
            usersWithOneOnOne.foldToEitherWhileRight(Unit) { item, _ ->
                resolveOneOnOneConversationWithUser(item).flatMapLeft {
                    when (it) {
                        is CoreFailure.NoKeyPackagesAvailable,
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
                }.map { }
            }
        }

    override suspend fun scheduleResolveOneOnOneConversationWithUserId(userId: UserId, delay: Duration) =
        resolveActiveOneOnOneScope.launch {
            kaliumLogger.d("Schedule resolving active one-on-one")
            incrementalSyncRepository.incrementalSyncState.first { it is IncrementalSyncStatus.Live }
            delay(delay)
            resolveOneOnOneConversationWithUserId(userId)
        }

    override suspend fun resolveOneOnOneConversationWithUserId(userId: UserId): Either<CoreFailure, ConversationId> =
        userRepository.getKnownUser(userId).firstOrNull()?.let {
            resolveOneOnOneConversationWithUser(it)
        } ?: Either.Left(StorageFailure.DataNotFound)

    override suspend fun resolveOneOnOneConversationWithUser(user: OtherUser): Either<CoreFailure, ConversationId> {
        kaliumLogger.i("Resolving one-on-one protocol for ${user.id.toLogString()}")
        return oneOnOneProtocolSelector.getProtocolForUser(user.id).flatMap { supportedProtocol ->
            when (supportedProtocol) {
                SupportedProtocol.PROTEUS -> oneOnOneMigrator.migrateToProteus(user)
                SupportedProtocol.MLS -> oneOnOneMigrator.migrateToMLS(user)
            }
        }
    }
}
