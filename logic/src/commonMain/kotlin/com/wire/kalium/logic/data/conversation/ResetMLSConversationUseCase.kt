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
import com.wire.kalium.common.error.wrapApiRequest
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.getOrFail
import com.wire.kalium.common.functional.left
import com.wire.kalium.common.functional.map
import com.wire.kalium.common.functional.right
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.cryptography.CryptoTransactionContext
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.client.CryptoTransactionProvider
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationApi

/**
 * Reset an MLS conversation which cannot be recovered by any other means.
 *
 * This use case will reset the MLS conversation by:
 *  - Calling /mls/reset-conversation API endpoint
 *  - Leaving the MLS group (wipeConversation)
 *  - Fetching the conversation to update group ID
 *  - Re-establishing the MLS group with the updated group ID and current members.
 */
interface ResetMLSConversationUseCase {
    suspend operator fun invoke(conversationId: ConversationId): Either<CoreFailure, Unit>
}

@Suppress("ReturnCount")
internal class ResetMLSConversationUseCaseImpl(
    private val userConfig: UserConfigRepository,
    private val transactionProvider: CryptoTransactionProvider,
    private val conversationRepository: ConversationRepository,
    private val mlsConversationRepository: MLSConversationRepository,
    private val fetchConversationUseCase: FetchConversationUseCase,
    private val conversationApi: ConversationApi,
) : ResetMLSConversationUseCase {

    override suspend operator fun invoke(conversationId: ConversationId): Either<CoreFailure, Unit> {

        if (!userConfig.isMlsConversationsResetEnabled()) {
            kaliumLogger.i("MLS conversation reset feature is disabled.")
            return Unit.right()
        }

        return transactionProvider.transaction("ResetMLSConversation") { transaction ->

            val mlsContext = transaction.mls ?: return@transaction errorNotMlsConversation()

            fetchConversation(transaction, conversationId)
                .flatMap { getMlsProtocolInfo(conversationId) }
                .flatMap { protocolInfo ->
                    conversationRepository.resetMlsConversation(protocolInfo.groupId, protocolInfo.epoch)
                        .map { protocolInfo.groupId }
                }
                .flatMap { groupId ->
                    mlsConversationRepository.leaveGroup(mlsContext, groupId)
                }
                .flatMap { fetchConversation(transaction, conversationId) }
                .flatMap { getMlsProtocolInfo(conversationId) }
                .map { updatedProtocolInfo ->

                    val members = conversationRepository.getConversationMembers(conversationId).getOrFail {
                        kaliumLogger.e("Failed to get members for conversation: $it")
                        return@transaction it.left()
                    }

                    mlsConversationRepository.establishMLSGroup(
                        mlsContext = mlsContext,
                        groupID = updatedProtocolInfo.groupId,
                        members = members,
                    )
                }.map {}
        }
    }

    private suspend fun fetchConversation(
        transaction: CryptoTransactionContext,
        conversationId: ConversationId
    ): Either<CoreFailure, Unit> = fetchConversationUseCase(transaction, conversationId)

    private suspend fun getMlsProtocolInfo(conversationId: ConversationId): Either<CoreFailure, Conversation.ProtocolInfo.MLS> {
        return conversationRepository.getConversationById(conversationId)
            .map {
                it.mlsProtocolInfo() ?: return errorNotMlsConversation()
            }
    }

    private fun errorNotMlsConversation() =
        CoreFailure.Unknown(IllegalStateException("Conversation is not an MLS conversation.")).left()
}

private fun Conversation.mlsProtocolInfo(): Conversation.ProtocolInfo.MLS? {
    return when (this.protocol) {
        is Conversation.ProtocolInfo.MLS -> this.protocol as? Conversation.ProtocolInfo.MLS
        else -> null
    }
}
