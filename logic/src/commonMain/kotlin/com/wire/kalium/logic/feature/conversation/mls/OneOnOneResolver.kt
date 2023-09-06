/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.id.ConversationId
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
import kotlinx.coroutines.flow.first

interface OneOnOneResolver {
    suspend fun resolveAllOneOnOneConversations(): Either<CoreFailure, Unit>
    suspend fun resolveOneOnOneConversationWithUserId(userId: UserId): Either<CoreFailure, ConversationId>
    suspend fun resolveOneOnOneConversationWithUser(user: OtherUser): Either<CoreFailure, ConversationId>
}

internal class OneOnOneResolverImpl(
    private val userRepository: UserRepository,
    private val oneOnOneProtocolSelector: OneOnOneProtocolSelector,
    private val oneOnOneMigrator: OneOnOneMigrator,
) : OneOnOneResolver {

    override suspend fun resolveAllOneOnOneConversations(): Either<CoreFailure, Unit> {
        val usersWithOneOnOne = userRepository.getUsersWithOneOnOneConversation()
        kaliumLogger.i("Resolving one-on-one protocol for ${usersWithOneOnOne.size} user(s)")
        return usersWithOneOnOne.foldToEitherWhileRight(Unit) { item, _ ->
            resolveOneOnOneConversationWithUser(item).map { }
        }
    }

    override suspend fun resolveOneOnOneConversationWithUserId(userId: UserId): Either<CoreFailure, ConversationId> =
        userRepository.getKnownUser(userId).first()?.let {
            resolveOneOnOneConversationWithUser(it)
        } ?: Either.Left(StorageFailure.DataNotFound)

    override suspend fun resolveOneOnOneConversationWithUser(user: OtherUser): Either<CoreFailure, ConversationId> =
        oneOnOneProtocolSelector.getProtocolForUser(user.id).flatMap { supportedProtocol ->
            when (supportedProtocol) {
                SupportedProtocol.PROTEUS -> oneOnOneMigrator.migrateToProteus(user)
                SupportedProtocol.MLS -> oneOnOneMigrator.migrateToMLS(user)
            }
        }.flatMapLeft {
            if (it is CoreFailure.NoCommonProtocolFound) {
                // TODO mark conversation as read only
                Either.Right(Unit)
            }
            Either.Left(it)
        }
}
