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
package com.wire.kalium.logic.feature.conversation.mls

import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.cryptography.CryptoTransactionContext
import com.wire.kalium.logic.data.client.CryptoTransactionProvider
import com.wire.kalium.logic.data.conversation.JoinExistingMLSConversationUseCase
import com.wire.kalium.logic.data.conversation.mls.PendingActionsRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.sync.SyncStateObserver
import io.mockative.Mockable

@Mockable
internal interface RecoverPendingMLSGroupJoinsUseCase {
    suspend operator fun invoke()
}

internal class RecoverPendingMLSGroupJoinsUseCaseImpl(
    private val pendingActionsRepository: PendingActionsRepository,
    private val syncStateObserver: SyncStateObserver,
    private val transactionProvider: CryptoTransactionProvider,
    private val joinExistingMLSConversation: JoinExistingMLSConversationUseCase,
) : RecoverPendingMLSGroupJoinsUseCase {

    override suspend fun invoke() {
        syncStateObserver.waitUntilLiveOrFailure().onFailure {
            return
        }

        val pendingConversationIds = pendingActionsRepository.getPendingMLSGroupJoins()
        if (pendingConversationIds.isEmpty()) return

        val successfulRecoveries = transactionProvider.transaction("recoverPendingMLSGroupJoins") { transactionContext ->
            Either.Right(recoverPendingConversations(transactionContext, pendingConversationIds))
        }

        when (successfulRecoveries) {
            is Either.Left -> Unit
            is Either.Right -> {
                if (successfulRecoveries.value.isNotEmpty()) {
                    pendingActionsRepository.acknowledgePendingMLSGroupJoins(successfulRecoveries.value)
                }
            }
        }
    }

    private suspend fun recoverPendingConversations(
        transactionContext: CryptoTransactionContext,
        pendingConversationIds: List<ConversationId>
    ): List<ConversationId> {
        val successfulConversationIds = mutableListOf<ConversationId>()
        pendingConversationIds.forEach { conversationId ->
            when (
                joinExistingMLSConversation(
                    transactionContext = transactionContext,
                    conversationId = conversationId,
                    allowJoinByExternalCommit = true
                ).onFailure {
                    kaliumLogger.w("Failed to recover pending MLS group join for ${conversationId.toLogString()}: $it")
                }
            ) {
                is Either.Left -> Unit
                is Either.Right -> successfulConversationIds.add(conversationId)
            }
        }
        return successfulConversationIds
    }
}
