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
package com.wire.kalium.logic.feature.mlsmigration

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.Conversation.Protocol
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.SystemMessageInserter
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.SelfTeamIdProvider
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.flatMapLeft
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.foldToEitherWhileRight
import com.wire.kalium.logic.kaliumLogger

interface MLSMigrator {
    suspend fun migrateProteusConversations(): Either<CoreFailure, Unit>
    suspend fun finaliseProteusConversations(): Either<CoreFailure, Unit>
}
internal class MLSMigratorImpl(
    private val selfUserId: UserId,
    private val selfTeamIdProvider: SelfTeamIdProvider,
    private val userRepository: UserRepository,
    private val conversationRepository: ConversationRepository,
    private val mlsConversationRepository: MLSConversationRepository,
    private val systemMessageInserter: SystemMessageInserter
) : MLSMigrator {

    override suspend fun migrateProteusConversations(): Either<CoreFailure, Unit> =
        selfTeamIdProvider().flatMap {
            it?.let { Either.Right(it) } ?: Either.Left(StorageFailure.DataNotFound)
        }.flatMap { teamId ->
            conversationRepository.getProteusTeamConversations(teamId)
                .flatMap {
                    it.foldToEitherWhileRight(Unit) { conversationId, _ ->
                        migrate(conversationId)
                    }
                }
        }

    override suspend fun finaliseProteusConversations(): Either<CoreFailure, Unit> =
        selfTeamIdProvider().flatMap {
            it?.let { Either.Right(it) } ?: Either.Left(StorageFailure.DataNotFound)
        }.flatMap { teamId ->
            userRepository.fetchAllOtherUsers()
                .flatMap {
                    conversationRepository.getProteusTeamConversationsReadyForFinalisation(teamId)
                        .flatMap {
                            it.foldToEitherWhileRight(Unit) { conversationId, _ ->
                                finalise(conversationId)
                            }
                        }
                }
        }

    private suspend fun migrate(conversationId: ConversationId): Either<CoreFailure, Unit> {
        kaliumLogger.i("migrating ${conversationId.toLogString()} to mixed")
        return conversationRepository.updateProtocol(conversationId, Protocol.MIXED)
            .flatMap { updated ->
                if (updated) {
                    systemMessageInserter.insertProtocolChangedSystemMessage(
                        conversationId, selfUserId, Protocol.MIXED
                    )
                }
                establishConversation(conversationId)
            }.flatMapLeft {
                kaliumLogger.w("failed to migrate ${conversationId.toLogString()} to mixed: $it")
                Either.Right(Unit)
            }
    }

    private suspend fun finalise(conversationId: ConversationId): Either<CoreFailure, Unit> {
        kaliumLogger.i("finalising ${conversationId.toLogString()} to mls")
        return conversationRepository.updateProtocol(conversationId, Protocol.MLS)
            .fold({ failure ->
                kaliumLogger.w("failed to finalise ${conversationId.toLogString()} to mls: $failure")
                Either.Right(Unit)
            }, { updated ->
                if (updated) {
                    systemMessageInserter.insertProtocolChangedSystemMessage(
                        conversationId, selfUserId, Protocol.MLS
                    )
                }
                Either.Right(Unit)
            })
    }

    private suspend fun establishConversation(conversationId: ConversationId) =
        conversationRepository.getConversationProtocolInfo(conversationId)
            .flatMap { protocolInfo ->
                when (protocolInfo) {
                    is Conversation.ProtocolInfo.Mixed -> {
                        mlsConversationRepository.establishMLSGroup(protocolInfo.groupId, emptyList())
                            .flatMap {
                                conversationRepository.getConversationMembers(conversationId).flatMap { members ->
                                    mlsConversationRepository.addMemberToMLSGroup(protocolInfo.groupId, members)
                                }
                            }
                    }
                    else -> Either.Right(Unit)
                }
            }
}
