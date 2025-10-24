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
import com.wire.kalium.cryptography.CryptoTransactionContext
import com.wire.kalium.logic.data.client.CryptoTransactionProvider
import com.wire.kalium.logic.data.conversation.UpdateConversationProtocolUseCase
import io.mockative.Mockable
import kotlinx.coroutines.flow.first

@Mockable
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
    private val callRepository: CallRepository,
    private val updateConversationProtocol: UpdateConversationProtocolUseCase,
    private val transactionProvider: CryptoTransactionProvider
) : MLSMigrator {

    override suspend fun migrateProteusConversations(): Either<CoreFailure, Unit> =
        selfTeamIdProvider().flatMap {
            it?.let { Either.Right(it) } ?: Either.Left(StorageFailure.DataNotFound)
        }.flatMap { teamId ->
            // TODO: Add support for channels here. Although... channels should always be MLS
            conversationRepository.getConversationIds(Conversation.Type.Group.Regular, Protocol.PROTEUS, teamId)
                .flatMap { conversations ->
                    transactionProvider.transaction("migrateProteusConversations") { transactionContext ->
                        conversations.foldToEitherWhileRight(Unit) { conversationId, _ ->
                            migrate(transactionContext, conversationId)
                        }
                    }
                }
        }

    override suspend fun finaliseAllProteusConversations(): Either<CoreFailure, Unit> =
        selfTeamIdProvider().flatMap {
            it?.let { Either.Right(it) } ?: Either.Left(StorageFailure.DataNotFound)
        }.flatMap { teamId ->
            // TODO: Add support for channels here. Although... channels should always be MLS
            transactionProvider.transaction("finaliseAllProteusConversations") { transactionContext ->
                conversationRepository.getConversationIds(Conversation.Type.Group.Regular, Protocol.MIXED, teamId)
                    .flatMap {
                        it.foldToEitherWhileRight(Unit) { conversationId, _ ->
                            finalise(transactionContext, conversationId)
                        }
                    }
            }
        }

    override suspend fun finaliseProteusConversations(): Either<CoreFailure, Unit> =
        selfTeamIdProvider().flatMap {
            it?.let { Either.Right(it) } ?: Either.Left(StorageFailure.DataNotFound)
        }.flatMap { teamId ->
            userRepository.fetchAllOtherUsers()
                .flatMap {
                    transactionProvider.transaction("finaliseProteusConversations") { transactionContext ->
                        conversationRepository.getTeamConversationIdsReadyToCompleteMigration(teamId)
                            .flatMap {
                                it.foldToEitherWhileRight(Unit) { conversationId, _ ->
                                    finalise(transactionContext, conversationId)
                                }
                            }
                    }
                }
        }

    private suspend fun migrate(transactionContext: CryptoTransactionContext, conversationId: ConversationId): Either<CoreFailure, Unit> {
        kaliumLogger.i("migrating ${conversationId.toLogString()} to mixed")
        return updateConversationProtocol(transactionContext, conversationId, Protocol.MIXED, localOnly = false)
            .flatMap { updated ->
                if (updated) {
                    systemMessageInserter.insertProtocolChangedSystemMessage(
                        conversationId,
                        selfUserId,
                        Protocol.MIXED
                    )
                    if (callRepository.establishedCallsFlow().first().isNotEmpty()) {
                        systemMessageInserter.insertProtocolChangedDuringACallSystemMessage(
                            conversationId,
                            selfUserId
                        )
                    }
                }
                kaliumLogger.i("migrating ${conversationId.toLogString()} to mls")
                establishConversation(transactionContext, conversationId)
            }.flatMapLeft {
                kaliumLogger.w("failed to migrate ${conversationId.toLogString()} to mixed: $it")
                Either.Right(Unit)
            }
    }

    private suspend fun finalise(transactionContext: CryptoTransactionContext, conversationId: ConversationId): Either<CoreFailure, Unit> {
        kaliumLogger.i("finalising ${conversationId.toLogString()} to mls")
        return updateConversationProtocol(transactionContext, conversationId, Protocol.MLS, localOnly = false)
            .fold({ failure ->
                kaliumLogger.w("failed to finalise ${conversationId.toLogString()} to mls: $failure")
                Either.Right(Unit)
            }, { updated ->
                if (updated) {
                    systemMessageInserter.insertProtocolChangedSystemMessage(
                        conversationId,
                        selfUserId,
                        Protocol.MLS
                    )
                }
                Either.Right(Unit)
            })
    }

    private suspend fun establishConversation(transactionContext: CryptoTransactionContext, conversationId: ConversationId) =
        conversationRepository.getConversationProtocolInfo(conversationId)
            .flatMap { protocolInfo ->
                when (protocolInfo) {
                    is Conversation.ProtocolInfo.Mixed -> {
                        val mlsContext = transactionContext.mls
                        if (mlsContext != null) {
                            conversationRepository.getConversationMembers(conversationId).flatMap { members ->
                                mlsConversationRepository.establishMLSGroup(
                                    mlsContext,
                                    protocolInfo.groupId,
                                    members,
                                    allowSkippingUsersWithoutKeyPackages = true
                                )
                            }
                        } else {
                            kaliumLogger.w("cannot migrate ${conversationId.toLogString()} to mixed, because mls is not supported")
                        }
                        Unit.right()
                    }

                    else -> Either.Right(Unit)
                }
            }
}
