/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.fold
import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.cryptography.CryptoTransactionContext
import com.wire.kalium.logic.data.client.wrapInMLSContext
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.JoinExistingMLSConversationUseCase
import com.wire.kalium.logic.data.conversation.MLSConversationRepository

internal sealed interface AuditMLSConversationMembershipResult {
    data object Success : AuditMLSConversationMembershipResult
    data class Failure(val failure: CoreFailure) : AuditMLSConversationMembershipResult
}

/**
 * Audits all active MLS-capable conversations to verify that their groups exist in the local
 * CoreCrypto state and rejoins any missing groups.
 *
 * This is used after the backend key-package count reaches zero and packages have been refilled,
 * or after an existing Proteus client registers for MLS. Conversations created or migrated while
 * the client had no available key packages or MLS identity may not have included it. The caller
 * must wait until incremental sync is live and invoke this use case in a transaction separate from
 * key-package upload, so slow sync and pending Welcome events are processed first.
 *
 * The audit is idempotent: existing groups are skipped, all conversations are attempted even if
 * individual checks or rejoins fail, and [AuditMLSConversationMembershipResult.Failure] is returned
 * when any conversation failed so the durable audit marker can be retained for a later retry.
 */
internal interface AuditMLSConversationMembershipUseCase {
    suspend operator fun invoke(transactionContext: CryptoTransactionContext): AuditMLSConversationMembershipResult
}

internal class AuditMLSConversationMembershipUseCaseImpl(
    private val conversationRepository: ConversationRepository,
    private val mlsConversationRepository: MLSConversationRepository,
    private val joinExistingMLSConversationUseCase: JoinExistingMLSConversationUseCase,
) : AuditMLSConversationMembershipUseCase {

    private val logger = kaliumLogger.withTextTag("AuditMLSConversationMembershipUseCase")

    override suspend fun invoke(transactionContext: CryptoTransactionContext): AuditMLSConversationMembershipResult {
        logger.d("Starting MLS conversation membership audit")
        return conversationRepository.getActiveMLSConversationsForMembershipAudit().fold(
            { failure ->
                logger.d("MLS conversation membership audit failed to load conversations: $failure")
                AuditMLSConversationMembershipResult.Failure(failure)
            },
            { conversations ->
                logger.d("Auditing MLS membership for ${conversations.size} active conversation(s)")
                var firstFailure: CoreFailure? = null
                conversations.forEach { conversation ->
                    auditConversation(transactionContext, conversation).fold(
                        { failure -> if (firstFailure == null) firstFailure = failure },
                        {}
                    )
                }
                firstFailure?.let { failure ->
                    logger.d("MLS conversation membership audit completed with failures: $failure")
                    AuditMLSConversationMembershipResult.Failure(failure)
                } ?: run {
                    logger.d("MLS conversation membership audit completed successfully")
                    AuditMLSConversationMembershipResult.Success
                }
            }
        )
    }

    private suspend fun auditConversation(
        transactionContext: CryptoTransactionContext,
        conversation: Conversation
    ): Either<CoreFailure, Unit> {
        val protocol = conversation.protocol as? Conversation.ProtocolInfo.MLSCapable
            ?: return Either.Right(Unit).also {
                logger.d("Skipping non-MLS conversation ${conversation.id.toLogString()} during membership audit")
            }
        logger.d(
            "Checking MLS membership for conversation ${conversation.id.toLogString()}, " +
                "group ${protocol.groupId.toLogString()}"
        )
        return transactionContext.wrapInMLSContext { mlsContext ->
            mlsConversationRepository.hasEstablishedMLSGroup(mlsContext, protocol.groupId)
        }.onFailure { failure ->
            logger.d(
                "Failed to check MLS membership for conversation ${conversation.id.toLogString()}: $failure"
            )
        }.flatMap { exists ->
            if (exists) {
                logger.d("MLS group already exists for conversation ${conversation.id.toLogString()}")
                Either.Right(Unit)
            } else {
                logger.d("MLS group is missing for conversation ${conversation.id.toLogString()}, attempting to rejoin")
                joinExistingMLSConversationUseCase(
                    transactionContext = transactionContext,
                    conversationId = conversation.id,
                    allowJoinByExternalCommit = true
                ).onSuccess {
                    logger.d("Successfully rejoined MLS group for conversation ${conversation.id.toLogString()}")
                }.onFailure { failure ->
                    logger.d("Failed to rejoin MLS group for conversation ${conversation.id.toLogString()}: $failure")
                }
            }
        }
    }
}
