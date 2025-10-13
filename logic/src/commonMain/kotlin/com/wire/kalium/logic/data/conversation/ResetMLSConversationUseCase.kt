/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
package com.wire.kalium.logic.data.conversation

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.MLSFailure
import com.wire.kalium.common.error.wrapMLSRequest
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.flatMapLeft
import com.wire.kalium.common.functional.getOrFail
import com.wire.kalium.common.functional.left
import com.wire.kalium.common.functional.map
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.common.functional.right
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.cryptography.CryptoTransactionContext
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.client.CryptoTransactionProvider
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.toCrypto
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import io.mockative.Mockable

/**
 * Reset an MLS conversation which cannot be recovered by any other means.
 *
 * This use case will reset the MLS conversation by:
 *  - Calling /mls/reset-conversation API endpoint
 *  - Leaving the MLS group (wipeConversation)
 *  - Fetching the conversation to update group ID
 *  - Re-establishing the MLS group with the updated group ID and current members.
 */
@Mockable
interface ResetMLSConversationUseCase {
    // TODO(refactor): transactionProvider should be always required to avoid deadlocks.
    //                 Callers of this function should get one if needed.
    @Deprecated("Transaction provider should be provided")
    suspend operator fun invoke(
        conversationId: ConversationId
    ): Either<CoreFailure, Unit>

    suspend operator fun invoke(
        conversationId: ConversationId,
        transactionContext: CryptoTransactionContext
    ): Either<CoreFailure, Unit>
}

@Suppress("ReturnCount")
internal class ResetMLSConversationUseCaseImpl(
    private val userConfig: UserConfigRepository,
    private val transactionProvider: CryptoTransactionProvider,
    private val conversationRepository: ConversationRepository,
    private val mlsConversationRepository: MLSConversationRepository,
    private val fetchConversationUseCase: FetchConversationUseCase,
    private val kaliumConfigs: KaliumConfigs,
) : ResetMLSConversationUseCase {

    private val logger by lazy { kaliumLogger.withTextTag("ResetMLSConversationUseCase") }

    override suspend fun invoke(conversationId: ConversationId): Either<CoreFailure, Unit> {
        return transactionProvider.transaction("ResetMLSConversation") {
            invoke(conversationId, it)
        }
    }

    override suspend operator fun invoke(
        conversationId: ConversationId,
        transactionContext: CryptoTransactionContext
    ): Either<CoreFailure, Unit> {
        if (!kaliumConfigs.isMlsResetEnabled) {
            logger.i("MLS conversation reset feature is disabled via compile time flag.")
            return Unit.right()
        }

        if (!userConfig.isMlsConversationsResetEnabled()) {
            logger.i("MLS conversation reset feature is disabled.")
            return Unit.right()
        }

        return transactionContext.resetConversation(conversationId)
    }

    private suspend fun CryptoTransactionContext.resetConversation(
        conversationId: ConversationId
    ): Either<CoreFailure, Unit> {
        val mlsContext = mls ?: return errorNotMlsConversation()

        return getMlsProtocolInfo(conversationId)
            .flatMap { protocolInfo ->
                wrapMLSRequest {
                    mlsContext.conversationEpoch(protocolInfo.groupId.toCrypto()) to protocolInfo.groupId
                }.flatMapLeft {
                    logger.e("Failed to reset conversation: $it.")
                    if (it is MLSFailure.ConversationNotFound) {
                        (this.mls?.conversationEpoch(protocolInfo.groupId.toCrypto())!! to protocolInfo.groupId).right()
                    } else {
                        it.left()
                    }
                }
            }
            .flatMap { (epoch, groupId) ->
                conversationRepository.resetMlsConversation(groupId, epoch)
                    .onSuccess {
                        // the result of the leave can be ignored
                        mlsConversationRepository.leaveGroup(mlsContext, groupId)
                    }
            }
            .flatMap {
                fetchConversation(this, conversationId)
            }
            .flatMap { getMlsProtocolInfo(conversationId) }
            .map { updatedProtocolInfo ->
                val members = conversationRepository.getConversationMembers(conversationId).getOrFail {
                    logger.e("Failed to get members for conversation: $it")
                    return it.left()
                }
                mlsConversationRepository.establishMLSGroup(
                    mlsContext = mlsContext,
                    groupID = updatedProtocolInfo.groupId,
                    members = members,
                )
            }.map {}
    }

    private suspend fun fetchConversation(
        transaction: CryptoTransactionContext,
        conversationId: ConversationId
    ): Either<CoreFailure, Unit> = fetchConversationUseCase(transaction, conversationId, ConversationSyncReason.ConversationReset)

    private suspend fun getMlsProtocolInfo(conversationId: ConversationId): Either<CoreFailure, Conversation.ProtocolInfo.MLSCapable> {
        return conversationRepository.getConversationById(conversationId)
            .map {
                it.mlsProtocolInfo() ?: return errorNotMlsConversation()
            }
    }

    private fun errorNotMlsConversation() =
        CoreFailure.Unknown(IllegalStateException("Conversation is not an MLS conversation.")).left()
}

private fun Conversation.mlsProtocolInfo(): Conversation.ProtocolInfo.MLSCapable? {
    return when (this.protocol) {
        is Conversation.ProtocolInfo.MLSCapable -> this.protocol as Conversation.ProtocolInfo.MLSCapable
        else -> null
    }
}
