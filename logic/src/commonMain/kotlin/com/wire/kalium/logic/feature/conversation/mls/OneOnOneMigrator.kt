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
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationGroupRepository
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.message.SystemMessageInserter
import com.wire.kalium.logic.data.user.OtherUser
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.data.user.type.isTeammate
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.foldToEitherWhileRight
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.kaliumLogger
import io.mockative.Mockable

@Mockable
interface OneOnOneMigrator {
    suspend fun migrateToProteus(user: OtherUser): Either<CoreFailure, ConversationId>
    suspend fun migrateToMLS(user: OtherUser): Either<CoreFailure, ConversationId>
}

internal class OneOnOneMigratorImpl(
    private val getResolvedMLSOneOnOne: MLSOneOnOneConversationResolver,
    private val conversationGroupRepository: ConversationGroupRepository,
    private val conversationRepository: ConversationRepository,
    private val messageRepository: MessageRepository,
    private val userRepository: UserRepository,
    private val systemMessageInserter: SystemMessageInserter
) : OneOnOneMigrator {

    override suspend fun migrateToProteus(user: OtherUser): Either<CoreFailure, ConversationId> =
        conversationRepository.getOneOnOneConversationsWithOtherUser(user.id, Conversation.Protocol.PROTEUS).flatMap { conversationIds ->
            if (conversationIds.isNotEmpty()) {
                val conversationId = conversationIds.first()
                Either.Right(conversationId)
            } else {
                Either.Left(StorageFailure.DataNotFound)
            }
        }.fold({ failure ->
            if (failure is StorageFailure.DataNotFound && user.userType.isTeammate()) {
                conversationGroupRepository.createGroupConversation(usersList = listOf(user.id)).map { it.id }
            } else {
                Either.Left(failure)
            }
        }, {
            Either.Right(it)
        }).flatMap { conversationId ->
            if (user.activeOneOnOneConversationId != conversationId) {
                kaliumLogger.d("resolved one-on-one to proteus, user = ${user.id.toLogString()}")
                userRepository.updateActiveOneOnOneConversation(user.id, conversationId)
            }
            Either.Right(conversationId)
        }

    override suspend fun migrateToMLS(user: OtherUser): Either<CoreFailure, ConversationId> {
        return getResolvedMLSOneOnOne(user.id)
            .flatMap { mlsConversation ->
                if (user.activeOneOnOneConversationId == mlsConversation) {
                    return@flatMap Either.Right(mlsConversation)
                }

                kaliumLogger.d("resolved one-on-one to MLS, user = ${user.id.toLogString()}")

                migrateOneOnOneHistory(user, mlsConversation)
                    .flatMap {
                        userRepository.updateActiveOneOnOneConversation(
                            conversationId = mlsConversation,
                            userId = user.id
                        ).map {
                            mlsConversation
                        }.also {
                            systemMessageInserter.insertProtocolChangedSystemMessage(
                                conversationId = mlsConversation,
                                senderUserId = user.id,
                                protocol = Conversation.Protocol.MLS
                            )
                        }
                    }
            }
    }

    private suspend fun migrateOneOnOneHistory(user: OtherUser, targetConversation: ConversationId): Either<CoreFailure, Unit> {
            return conversationRepository.getOneOnOneConversationsWithOtherUser(
                otherUserId = user.id,
                protocol = Conversation.Protocol.PROTEUS
            ).flatMap { proteusOneOnOneConversations ->
                // We can theoretically have more than one proteus 1-1 conversation with
                // team members since there was no backend safeguards against this
                proteusOneOnOneConversations.foldToEitherWhileRight(Unit) { proteusOneOnOneConversation, _ ->
                    messageRepository.moveMessagesToAnotherConversation(
                        originalConversation = proteusOneOnOneConversation,
                        targetConversation = targetConversation
                    )
                }
            }
    }
}
