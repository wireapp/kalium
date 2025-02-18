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
package com.wire.kalium.logic.feature.mlsmigration

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.Conversation.Protocol
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.SelfTeamIdProvider
import com.wire.kalium.logic.data.message.SystemMessageInserter
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.flatMapLeft
import com.wire.kalium.common.functional.fold
import com.wire.kalium.common.functional.foldToEitherWhileRight
import com.wire.kalium.common.functional.right
import com.wire.kalium.common.logger.kaliumLogger
import kotlinx.coroutines.flow.first

interface MLSMigrator {
    suspend fun migrateProteusConversations(): Either<CoreFailure, Unit>
    suspend fun finaliseProteusConversations(): Either<CoreFailure, Unit>
    suspend fun finaliseAllProteusConversations(): Either<CoreFailure, Unit>
}
@Suppress("LongParameterList")
internal class MLSMigratorImpl(
    private val selfUserId: UserId,
    private val selfTeamIdProvider: SelfTeamIdProvider,
    private val userRepository: UserRepository,
    private val conversationRepository: ConversationRepository,
    private val mlsConversationRepository: MLSConversationRepository,
    private val systemMessageInserter: SystemMessageInserter,
    private val callRepository: CallRepository
) : MLSMigrator {

    override suspend fun migrateProteusConversations(): Either<CoreFailure, Unit> =
        selfTeamIdProvider().flatMap {
            it?.let { Either.Right(it) } ?: Either.Left(StorageFailure.DataNotFound)
        }.flatMap { teamId ->
            conversationRepository.getConversationIds(Conversation.Type.GROUP, Protocol.PROTEUS, teamId)
                .flatMap {
                    it.foldToEitherWhileRight(Unit) { conversationId, _ ->
                        migrate(conversationId)
                    }
                }
        }

    override suspend fun finaliseAllProteusConversations(): Either<CoreFailure, Unit> =
        selfTeamIdProvider().flatMap {
            it?.let { Either.Right(it) } ?: Either.Left(StorageFailure.DataNotFound)
        }.flatMap { teamId ->
            conversationRepository.getConversationIds(Conversation.Type.GROUP, Protocol.MIXED, teamId)
                .flatMap {
                    it.foldToEitherWhileRight(Unit) { conversationId, _ ->
                        finalise(conversationId)
                    }
                }
        }

    override suspend fun finaliseProteusConversations(): Either<CoreFailure, Unit> =
        selfTeamIdProvider().flatMap {
            it?.let { Either.Right(it) } ?: Either.Left(StorageFailure.DataNotFound)
        }.flatMap { teamId ->
            userRepository.fetchAllOtherUsers()
                .flatMap {
                    conversationRepository.getTeamConversationIdsReadyToCompleteMigration(teamId)
                        .flatMap {
                            it.foldToEitherWhileRight(Unit) { conversationId, _ ->
                                finalise(conversationId)
                            }
                        }
                }
        }

    private suspend fun migrate(conversationId: ConversationId): Either<CoreFailure, Unit> {
        kaliumLogger.i("migrating ${conversationId.toLogString()} to mixed")
        return conversationRepository.updateProtocolRemotely(conversationId, Protocol.MIXED)
            .flatMap { updated ->
                if (updated) {
                    systemMessageInserter.insertProtocolChangedSystemMessage(
                        conversationId, selfUserId, Protocol.MIXED
                    )
                    if (callRepository.establishedCallsFlow().first().isNotEmpty()) {
                        systemMessageInserter.insertProtocolChangedDuringACallSystemMessage(
                            conversationId,
                            selfUserId
                        )
                    }
                }
                kaliumLogger.i("migrating ${conversationId.toLogString()} to mls")
                establishConversation(conversationId)
            }.flatMapLeft {
                kaliumLogger.w("failed to migrate ${conversationId.toLogString()} to mixed: $it")
                Either.Right(Unit)
            }
    }

    private suspend fun finalise(conversationId: ConversationId): Either<CoreFailure, Unit> {
        kaliumLogger.i("finalising ${conversationId.toLogString()} to mls")
        return conversationRepository.updateProtocolRemotely(conversationId, Protocol.MLS)
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
                        conversationRepository.getConversationMembers(conversationId).flatMap { members ->
                            mlsConversationRepository.establishMLSGroup(
                                protocolInfo.groupId,
                                members,
                                allowSkippingUsersWithoutKeyPackages = true
                            )
                        }
                        Unit.right()
                    }
                    else -> Either.Right(Unit)
                }
            }
}
